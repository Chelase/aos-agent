package com.aos.agent.core.voice

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 离线唤醒词（KWS）通道。
 *
 * 与 [SpeechTranscriber] 分开：唤醒是常驻听、判定"是不是叫我"，识别是按需听、要整句文本。
 * 两者共用麦克风会互相抢，所以唤醒只产布尔，不产文本，音频也不出本地。
 */
interface WakeWordEngine {

    /** 模型与原生库都就绪才 true；false 时界面必须给出原因，不得留个按了没反应的开关。 */
    val available: Boolean

    fun start(onWake: () -> Unit, onError: (WakeError) -> Unit)

    fun stop()

    fun release()
}

enum class WakeError { MODEL_MISSING, NO_MIC_PERMISSION, AUDIO_IN_USE, NATIVE_UNAVAILABLE, GENERIC }

/**
 * 唤醒判定纯函数。
 *
 * 受限语法下 Vosk 只会给唤醒词或 `[unk]`；判定不收正则、不切句，只去空白后看是否包含唤醒词，
 * 这样中英混说与识别器多给的空格都不会把"叫了我"判成没叫。
 */
object WakeWordMatcher {

    private const val UNKNOWN_TOKEN = "[unk]"

    fun isWake(text: String, keyword: String): Boolean {
        val needle = normalize(keyword)
        return needle.isNotEmpty() && normalize(text).contains(needle)
    }

    /** `[unk]` 与空串都算没听到；带前后噪声的整句按包含判定。 */
    fun isUnrecognized(text: String): Boolean = normalize(text).isEmpty() ||
        normalize(text) == normalize(UNKNOWN_TOKEN)

    /**
     * 取 Vosk 结果 JSON 的 `text` 字段。
     *
     * 解析失败一律按"没听到"返回空串而不是抛出：常驻循环里抛异常等于把唤醒整个停掉，
     * 而一帧坏 JSON 不代表后面都坏。
     */
    fun textOf(resultJson: String): String = runCatching {
        (Json.parseToJsonElement(resultJson) as? JsonObject)
            ?.get("text")?.jsonPrimitive?.content.orEmpty()
    }.getOrDefault("")

    private fun normalize(text: String): String =
        text.filterNot { it.isWhitespace() }.lowercase()
}

/**
 * 进程内唤醒闸门与唤醒事件。
 *
 * 同一个进程里的两路 `AudioRecord` 会抢同一个麦克风，所以界面正在跑语音会话时常驻 KWS 要让路；
 * 命中事件走 [wakes]（界面活着就直接进聆听），Activity 不在栈上的情况由服务补一次 `startActivity`。
 */
object WakeWordGate {
    val paused = MutableStateFlow(false)

    private val _wakes = MutableSharedFlow<Unit>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** 唤醒事件流。无重放：晚到的收集者不该被一次历史唤醒再拉起来。 */
    val wakes = _wakes.asSharedFlow()

    fun emitWake() {
        _wakes.tryEmit(Unit)
    }
}
