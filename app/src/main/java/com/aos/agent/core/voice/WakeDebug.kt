package com.aos.agent.core.voice

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** 一条唤醒观测：模型吐出来的原文 + 判定结果 + 相对开启时刻的毫秒。 */
data class WakeObservation(val text: String, val matched: Boolean, val sinceOpenMillis: Long)

/** 面板要渲染的全部状态，一份真相。 */
data class WakeDebugState(
    val enabled: Boolean = false,
    val observations: List<WakeObservation> = emptyList(),
    val hits: Int = 0,
    val misses: Int = 0,
    val openedAtMillis: Long? = null,
)

/**
 * 唤醒观测缓冲——真车调命中率用的眼睛。
 *
 * 只有开着面板时收集，离开即清空：常驻内存里堆识别文本没有意义。
 * 受限语法下文本取值只可能是"选定的某条说法"或 `[unk]`，日常对话内容不会流进来，
 * 所以显示原文是安全的；仍然只在内存、不落盘、不上传。
 */
object WakeDebug {

    /** 只留最近这么多条：够判断命中节奏，又不至于变成日志系统。 */
    const val MAX_ENTRIES = 20

    private val _state = MutableStateFlow(WakeDebugState())
    val state: StateFlow<WakeDebugState> = _state.asStateFlow()

    fun open() {
        _state.update {
            it.copy(enabled = true, observations = emptyList(), hits = 0, misses = 0, openedAtMillis = null)
        }
    }

    /** 关闭即清场：不把上一次的现场留在内存里。 */
    fun close() {
        _state.update { it.copy(enabled = false, observations = emptyList(), hits = 0, misses = 0) }
    }

    /**
     * 记一条。[nowMillis] 由调用方给（引擎用 `SystemClock.elapsedRealtime`），
     * 这样纯函数侧可以单测，不绑 Android 时钟。
     */
    fun record(text: String, matched: Boolean, nowMillis: Long) {
        _state.update { current ->
            if (!current.enabled) return@update current
            val origin = current.openedAtMillis ?: nowMillis
            val entry = WakeObservation(text, matched, (nowMillis - origin).coerceAtLeast(0L))
            current.copy(
                openedAtMillis = origin,
                observations = (current.observations + entry).takeLast(MAX_ENTRIES),
                hits = current.hits + if (matched) 1 else 0,
                misses = current.misses + if (matched) 0 else 1,
            )
        }
    }

    fun resetCounters() {
        _state.update { it.copy(observations = emptyList(), hits = 0, misses = 0) }
    }
}
