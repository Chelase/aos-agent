package com.aos.agent.ui.voice

import androidx.annotation.StringRes
import com.aos.agent.R
import com.aos.agent.core.voice.SpeechSynthesizer
import com.aos.agent.core.voice.SpeechTranscriber
import com.aos.agent.core.voice.VoiceCommand
import com.aos.agent.core.voice.VoiceCommandParser
import com.aos.agent.core.voice.VoiceCommandVocabulary
import com.aos.agent.core.voice.VoiceError
import com.aos.agent.data.store.VoiceSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 语音对话状态机：
 * `IDLE → LISTENING → (指令就地执行 | THINKING) → SPEAKING → 连续模式回 LISTENING / 否则 IDLE`。
 *
 * 只依赖 [SpeechTranscriber]/[SpeechSynthesizer] 两个接口，不碰 Android 类，
 * 因此单元测试可以灌假通道把整条回路（含打断、连续对话、误触发）跑完。
 * 所有方法都必须在主线程调用（识别器与 TTS 的线程要求）。
 */
class VoiceController(
    private val transcriber: SpeechTranscriber,
    private val synthesizer: SpeechSynthesizer,
    private val vocabulary: VoiceCommandVocabulary,
    private val settings: StateFlow<VoiceSettings>,
    private val hasPermission: () -> Boolean,
) {

    private val _state = MutableStateFlow(VoiceUiState())
    val state: StateFlow<VoiceUiState> = _state.asStateFlow()

    /** 识别为普通提问时回调（宿主发给引擎）。 */
    var onQuery: (String) -> Unit = {}

    /** 识别为本地指令时回调（宿主就地执行并给回执）。 */
    var onCommand: (VoiceCommand) -> Unit = {}

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
            VoicePhase.IDLE -> startListening()

            // 让识别器结算已听到的内容，onFinal 会接手
            VoicePhase.LISTENING -> transcriber.stop()

            VoicePhase.THINKING -> _state.value =
                _state.value.copy(noticeRes = R.string.voice_notice_thinking)

            // barge-in：打断播报，立刻回到聆听
            VoicePhase.SPEAKING -> {
                synthesizer.stop()
                _state.value = _state.value.copy(noticeRes = R.string.voice_notice_interrupted)
                startListening()
            }
        }
    }

    /** 引擎开始产出一轮回答。 */
    fun onTurnStarted() {
        _state.value = _state.value.copy(phase = VoicePhase.THINKING, partial = "")
    }

    /** 一轮回答完成：按开关播报，播完按连续模式决定回聆听还是收工。 */
    fun onAnswer(text: String) {
        val ttsOn = settings.value.ttsEnabled && synthesizer.available
        if (!ttsOn) {
            afterSpeaking()
            return
        }
        _state.value = _state.value.copy(phase = VoicePhase.SPEAKING, partial = "")
        synthesizer.speak(text) { afterSpeaking() }
    }

    /** 引擎失败：不卡在 THINKING，灯回 IDLE 并提示。 */
    fun onTurnFailed() {
        _state.value = _state.value.copy(
            phase = VoicePhase.IDLE,
            noticeRes = R.string.voice_notice_failed,
        )
    }

    fun release() {
        transcriber.release()
        synthesizer.release()
    }

    private fun afterSpeaking() {
        if (settings.value.continuous) startListening() else {
            _state.value = _state.value.copy(phase = VoicePhase.IDLE)
        }
    }

    private fun startListening() {
        _state.value = _state.value.copy(
            phase = VoicePhase.LISTENING,
            partial = "",
            noticeRes = null,
            continuous = settings.value.continuous,
        )
        transcriber.start(
            onPartial = { text -> _state.value = _state.value.copy(partial = text) },
            onFinal = { text -> onHeard(text) },
            onError = { error -> onVoiceError(error) },
        )
    }

    private fun onHeard(text: String) {
        val heard = text.trim()
        if (heard.isEmpty()) {
            _state.value = _state.value.copy(
                phase = VoicePhase.IDLE,
                partial = "",
                noticeRes = R.string.voice_notice_no_match,
            )
            return
        }
        val command = VoiceCommandParser.parse(heard, vocabulary)
        if (command != null) {
            _state.value = _state.value.copy(
                phase = VoicePhase.IDLE,
                partial = "",
                lastHeard = heard,
                noticeRes = noticeFor(command),
                noticeArg = (command as? VoiceCommand.UseSkill)?.skill,
            )
            onCommand(command)
            return
        }
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
        _state.value = _state.value.copy(
            phase = VoicePhase.IDLE,
            partial = "",
            noticeRes = when (error) {
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
}
