package com.aos.agent.ui.voice

import androidx.annotation.StringRes
import com.aos.agent.R
import com.aos.agent.core.voice.SpeechSynthesizer
import com.aos.agent.core.voice.SpeechTranscriber
import com.aos.agent.core.voice.VoiceCommand
import com.aos.agent.core.voice.VoiceCommandParser
import com.aos.agent.core.voice.VoiceCommandVocabulary
import com.aos.agent.core.voice.VoiceError
import com.aos.agent.core.voice.VoiceFocusHandle
import com.aos.agent.data.store.VoiceSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 语音对话状态机：
 * `IDLE → LISTENING → (指令就地执行 | THINKING) → SPEAKING → WAITING → LISTENING …`，
 * 任一环节回到 IDLE 时交还音频焦点。
 *
 * 只依赖识别/播报/焦点三个接口，不碰 Android 类，因此单元测试可以灌假通道把整条回路
 * （含静默间隙、无语音超时、人声打断、焦点挂起恢复）跑完；延迟走注入的 [scope]，
 * 测试用虚拟时间推进，不靠 sleep。所有方法都必须在主线程调用（识别器与 TTS 的线程要求）。
 */
class VoiceController(
    private val transcriber: SpeechTranscriber,
    private val synthesizer: SpeechSynthesizer,
    private val focus: VoiceFocusHandle,
    private val vocabulary: VoiceCommandVocabulary,
    private val settings: StateFlow<VoiceSettings>,
    private val scope: CoroutineScope,
    private val hasPermission: () -> Boolean,
) {

    private val _state = MutableStateFlow(VoiceUiState())
    val state: StateFlow<VoiceUiState> = _state.asStateFlow()

    /** 识别为普通提问时回调（宿主发给引擎）。 */
    var onQuery: (String) -> Unit = {}

    /** 识别为本地指令时回调（宿主就地执行并给回执）。 */
    var onCommand: (VoiceCommand) -> Unit = {}

    private var silenceJob: Job? = null
    private var timeoutJob: Job? = null
    private var holdsFocus = false
    private var awaitingFocus = false

    /** 页面 onResume、权限结果回来后调用：能力与权限都可能刚发生变化。 */
    fun refreshAvailability() {
        val serviceMissing = !transcriber.available
        val permissionMissing = !hasPermission()
        _state.value = _state.value.copy(
            usable = !serviceMissing && !permissionMissing,
            needsPermission = !serviceMissing && permissionMissing,
            blockedReasonRes = when {
                serviceMissing -> R.string.voice_err_no_service
                permissionMissing -> R.string.voice_err_permission
                else -> null
            },
            continuous = settings.value.continuous,
        )
    }

    /** 麦克风按钮：按当前阶段决定是开始聆听、结束聆听还是打断播报后聆听。 */
    fun toggle() {
        refreshAvailability()
        if (!state.value.usable) {
            _state.value = _state.value.copy(noticeRes = state.value.blockedReasonRes)
            return
        }
        when (state.value.phase) {
            VoicePhase.IDLE -> startSession()

            VoicePhase.WAITING -> goIdle(null)

            // 让识别器结算已听到的内容，onFinal 会接手
            VoicePhase.LISTENING -> transcriber.stop()

            VoicePhase.THINKING -> _state.value =
                _state.value.copy(noticeRes = R.string.voice_notice_thinking)

            VoicePhase.SPEAKING -> interruptPlayback()
        }
    }

    /** 引擎开始产出一轮回答。 */
    fun onTurnStarted() {
        _state.value = _state.value.copy(phase = VoicePhase.THINKING, partial = "")
    }

    /** 一轮回答完成：按开关播报，播完按连续模式走静默间隙回聆听，否则收工。 */
    fun onAnswer(text: String) {
        if (awaitingFocus) {
            // 焦点还在别人手里：不抢回来播，文字回答本来就在对话页上；等 GAIN 再继续回路
            suspendLoop(R.string.voice_notice_focus_suspended)
            return
        }
        if (!settings.value.ttsEnabled || !synthesizer.available) {
            finishTurn()
            return
        }
        // 键盘提问也要播出去：播报同样需要焦点压低媒体，不能只在语音会话里才申请
        ensureFocus()
        _state.value = _state.value.copy(phase = VoicePhase.SPEAKING, partial = "", noticeArg = null)
        synthesizer.speak(text) { afterSpeaking() }
        probeForBargeIn()
    }

    /** 引擎失败：不卡在 THINKING，灯回 IDLE 并提示。 */
    fun onTurnFailed() {
        goIdle(R.string.voice_notice_failed)
    }

    fun release() {
        silenceJob?.cancel()
        timeoutJob?.cancel()
        releaseFocus()
        transcriber.release()
        synthesizer.release()
    }

    // ---- 会话与焦点 ----

    private fun startSession() {
        awaitingFocus = false
        startListening()
    }

    /** 一次会话只申请一份焦点：重复申请会让系统把我们自己前一条请求踢成 LOSS。 */
    private fun ensureFocus() {
        if (holdsFocus) return
        holdsFocus = focus.claim(onLost = ::onAudioFocusLost, onGained = ::onAudioFocusGained)
    }

    private fun onAudioFocusLost() {
        if (_state.value.phase == VoicePhase.IDLE) return
        suspendLoop(R.string.voice_notice_focus_suspended)
    }

    /**
     * 按住回路但不结束它：焦点请求仍挂在名下，系统给回 GAIN 时自动续上。
     * 因此这里不能走 [goIdle]——那会交还焦点，也就再也等不到恢复回调。
     */
    private fun suspendLoop(@StringRes noticeRes: Int) {
        transcriber.cancel()
        synthesizer.stop()
        silenceJob?.cancel()
        timeoutJob?.cancel()
        awaitingFocus = true
        _state.value = _state.value.copy(
            phase = VoicePhase.IDLE,
            partial = "",
            noticeRes = noticeRes,
            noticeArg = null,
            suspendedByFocus = true,
        )
    }

    private fun onAudioFocusGained() {
        if (!awaitingFocus) return
        awaitingFocus = false
        refreshAvailability()
        if (!state.value.usable) {
            goIdle(state.value.blockedReasonRes)
            return
        }
        startListening()
    }

    private fun releaseFocus() {
        if (!holdsFocus) return
        holdsFocus = false
        awaitingFocus = false
        focus.release()
    }

    // ---- 回路各环节 ----

    private fun startListening() {
        // 开麦前必须持有会话焦点：回路也可能从键盘提问走进来（播报关闭时）
        ensureFocus()
        _state.value = _state.value.copy(
            phase = VoicePhase.LISTENING,
            partial = "",
            noticeRes = null,
            continuous = settings.value.continuous,
            suspendedByFocus = false,
        )
        timeoutJob?.cancel()
        timeoutJob = scope.launch {
            delay(NO_SPEECH_TIMEOUT_MS)
            if (_state.value.phase == VoicePhase.LISTENING && _state.value.partial.isBlank()) {
                transcriber.cancel()
                goIdle(R.string.voice_notice_timeout)
            }
        }
        transcriber.start(
            onPartial = { text ->
                if (text.isNotBlank()) timeoutJob?.cancel()
                if (_state.value.phase == VoicePhase.LISTENING) {
                    _state.value = _state.value.copy(partial = text)
                }
            },
            onFinal = { text -> onHeard(text) },
            onError = { error -> onVoiceError(error) },
        )
    }

    /** 播报结束后的防自听间隙：喇叭余音和房间回声都落在这 1 秒里。 */
    private fun startSilenceGap() {
        _state.value = _state.value.copy(phase = VoicePhase.WAITING, partial = "", noticeRes = null)
        silenceJob?.cancel()
        silenceJob = scope.launch {
            delay(SILENCE_AFTER_SPEECH_MS)
            if (_state.value.phase == VoicePhase.WAITING) startListening()
        }
    }

    private fun afterSpeaking() {
        // 只认播报回调：被打断时阶段已被带走，谁带走谁负责下一步，避免两路都起聆听
        if (_state.value.phase != VoicePhase.SPEAKING) return
        finishTurn()
    }

    private fun finishTurn() {
        if (settings.value.continuous) startSilenceGap() else goIdle(null)
    }

    /** 人声打断探针：播报期间另起一路只听部分结果的识别，默认关（见设置页说明）。 */
    private fun probeForBargeIn() {
        if (!settings.value.continuous || !settings.value.bargeInEnabled) return
        transcriber.start(
            onPartial = { text ->
                if (text.isNotBlank() && _state.value.phase == VoicePhase.SPEAKING) interruptPlayback()
            },
            onFinal = { /* 探针不提交结果，打断后另起一轮干净聆听 */ },
            onError = { /* 探针起不来不影响播报 */ },
        )
    }

    private fun interruptPlayback() {
        transcriber.cancel()
        synthesizer.stop()
        _state.value = _state.value.copy(noticeRes = R.string.voice_notice_interrupted)
        startListening()
    }

    private fun goIdle(@StringRes noticeRes: Int?) {
        silenceJob?.cancel()
        timeoutJob?.cancel()
        releaseFocus()
        _state.value = _state.value.copy(
            phase = VoicePhase.IDLE,
            partial = "",
            noticeRes = noticeRes,
            noticeArg = null,
            suspendedByFocus = false,
        )
    }

    private fun onHeard(text: String) {
        if (_state.value.phase != VoicePhase.LISTENING) return
        val heard = text.trim()
        if (heard.isEmpty()) {
            goIdle(R.string.voice_notice_no_match)
            return
        }
        val command = VoiceCommandParser.parse(heard, vocabulary)
        if (command != null) {
            goIdle(noticeFor(command))
            _state.value = _state.value.copy(
                lastHeard = heard,
                noticeArg = (command as? VoiceCommand.UseSkill)?.skill,
            )
            onCommand(command)
            return
        }
        timeoutJob?.cancel()
        silenceJob?.cancel()
        _state.value = _state.value.copy(
            phase = VoicePhase.THINKING,
            partial = "",
            lastHeard = heard,
            noticeRes = null,
            noticeArg = null,
        )
        onQuery(heard)
    }

    private fun onVoiceError(error: VoiceError) {
        goIdle(
            when (error) {
                VoiceError.NO_SERVICE -> R.string.voice_err_no_service
                VoiceError.PERMISSION -> R.string.voice_err_permission
                VoiceError.AUDIO_IN_USE -> R.string.voice_err_audio_busy
                VoiceError.NO_MATCH -> R.string.voice_notice_no_match
                VoiceError.TIMEOUT -> R.string.voice_notice_timeout
                VoiceError.GENERIC -> R.string.voice_notice_failed
            },
        )
    }

    @StringRes
    private fun noticeFor(command: VoiceCommand): Int = when (command) {
        VoiceCommand.NewSession -> R.string.voice_ack_new_session
        VoiceCommand.OpenSettings -> R.string.voice_ack_open_settings
        VoiceCommand.CloseSettings -> R.string.voice_ack_close_settings
        VoiceCommand.GoHome -> R.string.voice_ack_go_home
        VoiceCommand.UseMcp -> R.string.voice_ack_use_mcp
        is VoiceCommand.UseSkill -> R.string.voice_ack_use_skill
    }

    private companion object {
        /** 播报完到重新开麦之间的静默，防自听。 */
        const val SILENCE_AFTER_SPEECH_MS = 1_000L

        /** 连续对话里开口前的等待上限，到点收工交还焦点。 */
        const val NO_SPEECH_TIMEOUT_MS = 8_000L
    }
}
