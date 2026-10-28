package com.aos.agent.core.engine

import com.aos.agent.core.llm.LlmMessage
import com.aos.agent.core.llm.LlmRequest
import com.aos.agent.core.llm.LlmRole
import com.aos.agent.core.llm.LlmToolCall
import com.aos.agent.core.llm.ToolDefinition
import com.aos.agent.core.session.SessionLog

interface TokenBudget {
    val maxTokens: Int
    fun estimate(text: String): Int
}

/**
 * 无依赖的粗略估算：中日韩字符按 1 token 计，其余按 4 字符 1 token 计。
 * 真 tokenizer 属后续优化，接口位已留；换实现不影响调用方。
 */
class CharEstimateBudget(override val maxTokens: Int = DEFAULT_MAX_TOKENS) : TokenBudget {
    override fun estimate(text: String): Int {
        var wide = 0
        var narrow = 0
        text.forEach { char ->
            if (WIDE_RANGES.any { char.code in it }) wide++ else narrow++
        }
        return wide + (narrow + 3) / 4
    }

    companion object {
        const val DEFAULT_MAX_TOKENS = 8_000

        /** 一个字符就基本等于一个 token 的区段：中日韩、假名、韩文、全角标点。 */
        private val WIDE_RANGES = listOf(
            0x1100..0x11FF,
            0x2E80..0x33FF,
            0x3400..0x4DBF,
            0x4E00..0x9FFF,
            0xAC00..0xD7AF,
            0xF900..0xFAFF,
            0xFE30..0xFE4F,
            0xFF00..0xFF60,
        )
    }
}

/** 上一轮工具调用的压缩轨迹，供下一轮系统提示引用。 */
data class ToolTrace(
    val name: String,
    val status: String,
    val summary: String,
)

/**
 * 单轮对话的上下文装配：系统提示每轮求值，历史按预算从最旧开始丢。
 * [systemPromptProvider] 用于 harness 分节等动态内容；固定字符串场景可直接传字面量。
 */
class ContextManager(
    private val systemPromptProvider: () -> String,
    private val budget: TokenBudget = CharEstimateBudget(),
) {
    constructor(
        systemPrompt: String,
        budget: TokenBudget = CharEstimateBudget(),
    ) : this({ systemPrompt }, budget)

    private val turns = mutableListOf<LlmMessage>()
    private var lastTraces: List<ToolTrace> = emptyList()

    val turnCount: Int get() = turns.size

    val systemPrompt: String get() = systemPromptProvider()

    /**
     * 开一轮：返回可追加的副本（系统提示 + 预算内历史 + 本次提问），不改动已存历史。
     * [extraInstruction] 是本轮命中的 skill 提示片段——只影响本轮，不写进长期历史。
     */
    fun beginTurn(userText: String, extraInstruction: String? = null): MutableList<LlmMessage> =
        buildRequest(userText, extraInstruction = extraInstruction).messages.toMutableList()

    fun buildRequest(
        userText: String,
        tools: List<ToolDefinition> = emptyList(),
        extraInstruction: String? = null,
    ): LlmRequest {
        val candidate = turns + LlmMessage(LlmRole.User, userText)
        val prompt = systemPromptWith(extraInstruction)
        return LlmRequest(
            messages = listOf(LlmMessage(LlmRole.System, prompt)) + trim(candidate, prompt),
            toolDefinitions = tools,
        )
    }

    private fun systemPromptWith(extraInstruction: String?): String {
        val base = buildString {
            append(systemPromptProvider().trim())
            val traceBlock = renderTraces(lastTraces)
            if (traceBlock.isNotEmpty()) {
                if (isNotEmpty()) append("\n\n")
                append(traceBlock)
            }
        }
        val instruction = extraInstruction?.trim()
        return if (instruction.isNullOrEmpty()) base else "$base\n\n$instruction"
    }

    /** 只有正常完成的轮次才入历史，失败轮次不留半截内容。 */
    fun recordExchange(userText: String, assistantText: String) {
        recordTurn(userText, assistantText, emptyList())
    }

    fun recordTurn(userText: String, assistantText: String, traces: List<ToolTrace> = emptyList()) {
        turns += LlmMessage(LlmRole.User, userText)
        turns += LlmMessage(LlmRole.Assistant, assistantText)
        lastTraces = compressTraces(traces)
    }

    /**
     * 记录一个含工具调用的回合。
     *
     * [toolExchange] 是引擎产生的中间消息序列：一条 assistant(tool_calls) 后面跟若干 tool 消息。
     * 必须原样保留配对关系——OpenAI 兼容协议要求 tool 消息前有对应的 assistant(tool_calls)，
     * 且最终答案要单独作为最后一条 assistant 消息，合并会被服务端拒。
     */
    fun recordToolTurn(
        userText: String,
        toolExchange: List<LlmMessage>,
        finalText: String,
        traces: List<ToolTrace> = emptyList(),
    ) {
        turns += LlmMessage(LlmRole.User, userText)
        turns += toolExchange
        turns += LlmMessage(LlmRole.Assistant, finalText)
        lastTraces = compressTraces(traces)
    }

    fun reset() {
        turns.clear()
        lastTraces = emptyList()
    }

    /**
     * 从持久化恢复：只取最近一轮（含工具交换序列），避免冷启动塞爆上下文。
     * 恢复的是"上一轮发生过什么"，不是"把所有历史都搬回来"。
     */
    fun restore(records: List<SessionLog.TurnRecord>) {
        records.forEach { record ->
            turns += LlmMessage(LlmRole.User, record.user)
            turns += record.toolExchange
            turns += LlmMessage(LlmRole.Assistant, record.assistant)
        }
        lastTraces = emptyList()
    }

    /**
     * 两级降级：先压旧工具结果（保留问答脉络），仍超限再按回合组丢最旧历史。
     * 丢整轮是最后手段——一次丢光最伤，车载场景的上下文本来就更小。
     */
    private fun trim(messages: List<LlmMessage>, prompt: String): List<LlmMessage> {
        val compacted = ContextDegradation.compactToolResults(messages)
        val promptTokens = budget.estimate(prompt)
        val target = (budget.maxTokens - promptTokens).coerceAtLeast(0)
        return ContextDegradation.dropOldestTurns(compacted, target, budget)
    }

    companion object {
        private const val TRACE_BUDGET_CHARS = 400
        private const val SUMMARY_CHARS = 120

        fun compressTraces(traces: List<ToolTrace>): List<ToolTrace> {
            if (traces.isEmpty()) return emptyList()
            val clipped = traces.map {
                it.copy(summary = it.summary.replace('\n', ' ').take(SUMMARY_CHARS))
            }
            var used = 0
            val kept = mutableListOf<ToolTrace>()
            for (trace in clipped) {
                val piece = renderOne(trace)
                if (kept.isNotEmpty() && used + 1 + piece.length > TRACE_BUDGET_CHARS) break
                kept += trace
                used += piece.length + if (kept.size == 1) 0 else 1
            }
            return kept
        }

        fun renderTraces(traces: List<ToolTrace>): String {
            if (traces.isEmpty()) return ""
            val body = traces.joinToString("；") { renderOne(it) }
            return "上一轮工具轨迹：$body"
        }

        private fun renderOne(trace: ToolTrace): String {
            val summary = trace.summary.trim()
            return if (summary.isEmpty()) {
                "${trace.name} → ${trace.status}"
            } else {
                "${trace.name} → ${trace.status}，${summary}"
            }
        }
    }
}
