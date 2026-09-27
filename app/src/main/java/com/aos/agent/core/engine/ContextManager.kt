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

/** 单轮对话的上下文装配：系统提示常驻，历史按预算从最旧开始丢。 */
class ContextManager(
    val systemPrompt: String,
    private val budget: TokenBudget = CharEstimateBudget(),
) {
    private val turns = mutableListOf<LlmMessage>()

    val turnCount: Int get() = turns.size

    fun buildRequest(userText: String, tools: List<ToolDefinition> = emptyList()): LlmRequest {
        val candidate = turns + LlmMessage(LlmRole.User, userText)
        return LlmRequest(
            messages = listOf(LlmMessage(LlmRole.System, systemPrompt)) + trim(candidate),
            toolDefinitions = tools,
        )
    }

    /** 只有正常完成的轮次才入历史，失败轮次不留半截内容。 */
    fun recordExchange(userText: String, assistantText: String) {
        turns += LlmMessage(LlmRole.User, userText)
        turns += LlmMessage(LlmRole.Assistant, assistantText)
    }

    fun reset() {
        turns.clear()
    }

    private fun trim(messages: List<LlmMessage>): List<LlmMessage> {
        var total = budget.estimate(systemPrompt) + messages.sumOf { budget.estimate(it.content) }
        val kept = messages.toMutableList()
        // kept.size > 1 保证当前这条用户提问永远留在窗口里。
        while (total > budget.maxTokens && kept.size > 1) {
            total -= budget.estimate(kept.removeAt(0).content)
        }
        return kept
    }
}
