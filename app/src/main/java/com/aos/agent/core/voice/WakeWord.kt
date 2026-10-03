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
 * 唤醒判定与说法生成的纯函数。
 *
 * 受限语法下 Vosk 只会给出语法内的词条或 `[unk]`；判定不收正则、不切句，只去空白与标点
 * 后看是否包含某个说法，这样中英混说与识别器多给的空格都不会把"叫了我"判成没叫。
 */
object WakeWordMatcher {

    private const val UNKNOWN_TOKEN = "[unk]"

    fun isWake(text: String, phrases: List<String>): Boolean {
        val heard = normalize(text)
        return phrases.any { phrase ->
            val needle = normalize(phrase)
            needle.isNotEmpty() && heard.isNotEmpty() && heard.contains(needle)
        }
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

    /** 空白与标点不参与比对：识别器给"你好 Chelsea"还是"你好，chelsea"都该算命中。 */
    private fun normalize(text: String): String = buildString {
        text.forEach { ch ->
            if (!ch.isWhitespace() && ch !in PUNCTUATION_MARKS) append(ch.lowercaseChar())
        }
    }

    private val PUNCTUATION_MARKS = charArrayOf(
        ',', '.', '!', '?', ';', ':', '\'', '"', '、', '，', '。', '！', '？', '；', '：',
    )
}

/** 唤醒说法的三种模板。名字由用户给，模板固定可选，多个可同时开。 */
enum class WakeVariant { HELLO, HI, BARE }

/**
 * 由 Agent 名字生成唤醒说法与 Vosk 受限语法。
 *
 * 前缀"你好"/"Hi" 是**要念出口的词**，不随界面语言翻译（英文界面上用户照样喊"你好X"），
 * 所以这里用常量而不是 string 资源；界面显示时原样回显这些字符串。
 */
object WakeWordPhrases {

    private const val HELLO_PREFIX = "你好"
    private const val HI_PREFIX = "Hi"
    private const val UNKNOWN_TOKEN = "[unk]"

    /** 按 HELLO → HI → BARE 固定顺序生成，去空去重；名字为空则没有任何说法。 */
    fun build(name: String, variants: Set<WakeVariant>): List<String> {
        val who = name.trim()
        if (who.isEmpty()) return emptyList()
        val ordered = buildList {
            if (WakeVariant.HELLO in variants) add("$HELLO_PREFIX$who")
            if (WakeVariant.HI in variants) add("$HI_PREFIX $who")
            if (WakeVariant.BARE in variants) add(who)
        }
        return ordered.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    }

    /**
     * 受限语法 JSON。
     *
     * 名字是用户输入的，可能带引号或反斜杠，不转义就会把整条语法打烂（ recognizer 构造失败）；
     * 这里剥掉这两个字符而不是做 JSON 转义——唤醒词里出现引号本身没有意义。
     */
    fun grammarOf(phrases: List<String>): String {
        val items = phrases.map { "\"" + it.replace("\\", "").replace("\"", "") + "\"" } + "\"$UNKNOWN_TOKEN\""
        return "[" + items.joinToString(", ") + "]"
    }

    /** 界面上把说法串成一串展示（通知文案、开关标签共用）。 */
    fun displayOf(phrases: List<String>): String = phrases.joinToString(" / ")
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
