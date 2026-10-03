package com.aos.agent.core.voice

/**
 * 语音通道接口：识别（ASR）与播报（TTS）。
 *
 * 状态机只认这两个接口，Android 实现见 `system/voice/`；单元测试灌假实现。
 * 约定：所有回调都在主线程触发，实现方自己保证不重复回调。
 */
interface SpeechTranscriber {

    /** 设备是否具备可用的识别服务；false 时界面必须渲染禁用态并说明原因，不得伪造。 */
    val available: Boolean

    fun start(onPartial: (String) -> Unit, onFinal: (String) -> Unit, onError: (VoiceError) -> Unit)

    /** 结束本次聆听：让识别器把已听到的内容作为最终结果交回。 */
    fun stop()

    fun cancel()

    fun release()
}

interface SpeechSynthesizer {
    val available: Boolean

    /** 播报 [text]，播完（或被停止）后回调 [onDone]。同一时刻只播一路。 */
    fun speak(text: String, onDone: () -> Unit)

    fun stop()

    fun release()
}

/**
 * 一次语音会话的音频焦点。焦点由会话持有而不是由播报持有：
 * 同一 App 内两个焦点请求会互相踢掉对方（播报时把聆听挤成 LOSS），
 * 而且只有**自己名下**的请求被抢走时才收得到回调——挂起/恢复必须有这一路常驻请求才谈得上。
 *
 * 播报侧因此不再申请焦点（媒体压低由本会话的 MAY_DUCK 覆盖）。
 */
interface VoiceFocusHandle {

    /** 申请会话焦点，[onLost]/[onGained] 由系统在同一路焦点变化时回调。返回是否真的拿到。 */
    fun claim(onLost: () -> Unit, onGained: () -> Unit): Boolean

    fun release()
}

/** 失败原因枚举，UI 侧映射到本地化文案。 */
enum class VoiceError { NO_SERVICE, PERMISSION, AUDIO_IN_USE, NO_MATCH, TIMEOUT, GENERIC }
