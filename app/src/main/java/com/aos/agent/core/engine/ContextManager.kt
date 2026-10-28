package com.aos.agent.core.engine

import com.aos.agent.core.llm.LlmMessage
import com.aos.agent.core.llm.LlmRequest
import com.aos.agent.core.llm.LlmRole
import com.aos.agent.core.llm.ToolDefinition

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

    fun reset() {
        turns.clear()
        lastTraces = emptyList()
    }

    private fun trim(messages: List<LlmMessage>, prompt: String): List<LlmMessage> {
        var total = budget.estimate(prompt) + messages.sumOf { budget.estimate(it.content) }
        val kept = messages.toMutableList()
        // kept.size > 1 保证当前这条用户提问永远留在窗口里。
        while (total > budget.maxTokens && kept.size > 1) {
            total -= budget.estimate(kept.removeAt(0).content)
        }
        return kept
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
