package com.aos.agent.core.engine

import com.aos.agent.core.llm.LlmRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextManagerTest {

    /** 每 10 个字符算 1 个 token，便于精确构造超预算场景。 */
    private class TenCharsPerToken(override val maxTokens: Int) : TokenBudget {
        override fun estimate(text: String): Int = text.length / 10
    }

    @Test
    fun systemPromptSurvivesEvenTheTightestBudget() {
        val context = ContextManager(systemPrompt = "s".repeat(100), budget = TenCharsPerToken(maxTokens = 1))

        context.recordExchange("u".repeat(100), "a".repeat(100))
        val messages = context.buildRequest("q".repeat(100)).messages

        assertEquals(LlmRole.System, messages.first().role)
        assertEquals(LlmRole.User, messages.last().role)
        assertEquals("当前提问必须留在窗口内", 1, messages.count { it.role == LlmRole.User && it.content.startsWith("q") })
    }

    @Test
    fun oldestTurnsAreDroppedFirst() {
        val context = ContextManager(systemPrompt = "sys", budget = TenCharsPerToken(maxTokens = 4))
        repeat(5) { i -> context.recordExchange("old-question-$i", "old-answer-$i") }

        val contents = context.buildRequest("fresh").messages.map { it.content }

        assertEquals("sys", contents.first())
        assertEquals("fresh", contents.last())
        assertTrue("最新一轮历史应保留", contents.contains("old-answer-4"))
        assertTrue("最旧一轮应被丢弃", !contents.contains("old-question-0"))
    }

    @Test
    fun withinBudgetHistoryIsKeptIntact() {
        val context = ContextManager(systemPrompt = "sys")
        context.recordExchange("一", "二")

        val messages = context.buildRequest("三").messages

        assertEquals(
            listOf(LlmRole.System, LlmRole.User, LlmRole.Assistant, LlmRole.User),
            messages.map { it.role },
        )
    }

    /** 中文一个字算一个 token，不能让 chars/4 那种估法把窗口撑爆。 */
    @Test
    fun cjkCountsHeavierThanLatin() {
        val budget = CharEstimateBudget()

        val cjk = budget.estimate("车辆状态查询")
        val latin = budget.estimate("vehicle status query here")

        assertEquals(6, cjk)
        assertEquals(7, latin)
    }

    @Test
    fun resetClearsHistoryOnly() {
        val context = ContextManager(systemPrompt = "sys")
        context.recordExchange("q", "a")
        context.reset()

        assertEquals(0, context.turnCount)
        assertEquals(2, context.buildRequest("q2").messages.size)
    }
}
