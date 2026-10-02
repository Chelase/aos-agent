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

/** 失败原因枚举，UI 侧映射到本地化文案。 */
enum class VoiceError { NO_SERVICE, PERMISSION, AUDIO_IN_USE, NO_MATCH, TIMEOUT, GENERIC }
