package com.aos.agent.core.engine

import com.aos.agent.core.llm.LlmMessage
import com.aos.agent.core.llm.LlmRole
import com.aos.agent.core.llm.LlmToolCall
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextDegradationTest {

    private fun toolMessage(id: String, content: String) = LlmMessage(
        role = LlmRole.Tool,
        content = content,
        toolCallId = id,
        toolName = "shell_exec",
    )

    @Test
    fun longJsonFieldIsCompactedButStructureSurvives() {
        val content = """{"stdout":"${"x".repeat(500)}","full_output":"/data/out.txt","truncated":true}"""

        val compacted = ContextDegradation.compactToolResults(
            listOf(toolMessage("c1", content)),
            keepRecent = 0,
        )

        val body = compacted.single().content
        assertTrue("应标记已压缩：$body", body.contains("\"compacted\":true"))
        assertTrue("路径字段要保留：$body", body.contains("/data/out.txt"))
        assertTrue("布尔字段要保留：$body", body.contains("\"truncated\":true"))
        assertTrue("长字段应变短：${body.length}", body.length < content.length)
    }

    @Test
    fun recentToolResultsAreLeftIntact() {
        val old = toolMessage("old", """{"stdout":"${"x".repeat(300)}"}""")
        val recent = toolMessage("recent", """{"stdout":"${"y".repeat(300)}"}""")

        val compacted = ContextDegradation.compactToolResults(
            listOf(old, recent),
            keepRecent = 1,
        )

        assertTrue("旧工具结果应被压", compacted[0].content.contains("compacted"))
        assertEquals("最近一条应保持原样", recent.content, compacted[1].content)
    }

    @Test
    fun keepRecentZeroCompactsEveryToolResult() {
        val compacted = ContextDegradation.compactToolResults(
            listOf(toolMessage("c1", """{"stdout":"${"x".repeat(300)}"}""")),
            keepRecent = 0,
        )

        assertTrue(compacted.single().content.contains("compacted"))
    }

    @Test
    fun nonJsonContentIsTruncatedWithLengthHint() {
        val compacted = ContextDegradation.compactToolResults(
            listOf(toolMessage("c1", "z".repeat(400))),
            keepRecent = 0,
        )

        assertTrue(compacted.single().content.contains("已压缩"))
        assertTrue(compacted.single().content.contains("400"))
    }

    /** tool 消息前必须有对应的 assistant(tool_calls)，降级不能拆散配对。 */
    @Test
    fun compactionKeepsToolCallPairing() {
        val assistant = LlmMessage(
            role = LlmRole.Assistant,
            content = "",
            toolCalls = listOf(LlmToolCall("c1", "shell_exec", "{}")),
        )
        val messages = listOf(
            assistant,
            toolMessage("c1", """{"stdout":"${"x".repeat(300)}"}"""),
        )

        val compacted = ContextDegradation.compactToolResults(messages, keepRecent = 0)

        assertEquals(2, compacted.size)
        assertEquals(LlmRole.Assistant, compacted[0].role)
        assertEquals(LlmRole.Tool, compacted[1].role)
        assertEquals("c1", compacted[1].toolCallId)
        assertEquals(listOf("c1"), compacted[0].toolCalls.map { it.id })
    }

    @Test
    fun messagesWithoutToolResultsAreUnchanged() {
        val messages = listOf(
            LlmMessage(LlmRole.User, "问"),
            LlmMessage(LlmRole.Assistant, "答"),
        )

        assertEquals(messages, ContextDegradation.compactToolResults(messages))
    }

    /**
     * C4 的核心：超预算时先压工具输出，而不是直接丢整轮——
     * 问答脉络（用户问了什么、助手答了什么）必须留着。
     */
    @Test
    fun toolResultsAreCompactedBeforeOldTurnsAreDropped() {
        val context = ContextManager(systemPrompt = "sys", budget = TenPerToken(maxTokens = 200))
        val toolExchange = mutableListOf<LlmMessage>()
        val calls = (1..3).map { LlmToolCall("c$it", "shell_exec", "{}") }
        toolExchange += LlmMessage(LlmRole.Assistant, "", calls)
        (1..3).forEach { i -> toolExchange += toolMessage("c$i", """{"stdout":"${"x".repeat(600)}"}""") }
        context.recordToolTurn(
            userText = "q".repeat(60),
            toolExchange = toolExchange,
            finalText = "a".repeat(60),
        )

        val messages = context.buildRequest("fresh").messages
        val tools = messages.filter { it.role == LlmRole.Tool }

        assertEquals("三条工具结果都应保留", 3, tools.size)
        assertTrue("最旧的工具结果应被压缩", tools.first().content.contains("compacted"))
        assertTrue("最近的工具结果保持原样", tools.drop(1).none { it.content.contains("compacted") })
        assertTrue("问答脉络应保留", messages.any { it.content == "q".repeat(60) })
    }

    /**
     * 二级降级按回合组丢：tool 消息不能和它的 assistant(tool_calls) 分家，
     * 否则 OpenAI 兼容服务端会拒整个请求。
     */
    @Test
    fun droppingOldTurnsKeepsToolCallPairing() {
        val assistant = LlmMessage(
            role = LlmRole.Assistant,
            content = "",
            toolCalls = listOf(LlmToolCall("c1", "shell_exec", "{}")),
        )
        val messages = listOf(
            LlmMessage(LlmRole.User, "旧提问"),
            assistant,
            toolMessage("c1", """{"stdout":"x"}"""),
            LlmMessage(LlmRole.User, "当前提问"),
        )

        val dropped = ContextDegradation.dropOldestTurns(
            messages,
            targetTokens = 0,
            budget = TenPerToken(maxTokens = 100),
        )

        assertTrue("旧回合应被整组丢弃", dropped.none { it.content == "旧提问" })
        assertTrue("当前提问必须留下", dropped.any { it.content == "当前提问" })
        // 剩下若还有 tool 消息，它的 assistant 必须在且必须紧邻其前
        val toolIndex = dropped.indexOfFirst { it.role == LlmRole.Tool }
        if (toolIndex >= 0) {
            val assistantIndex = toolIndex - 1
            assertTrue("tool 前必须有 assistant(tool_calls)",
                dropped[assistantIndex].role == LlmRole.Assistant &&
                    dropped[assistantIndex].toolCalls.isNotEmpty())
            assertEquals(dropped[assistantIndex].toolCalls.first().id, dropped[toolIndex].toolCallId)
        }
    }

    @Test
    fun droppingTurnsStopsBeforeRemovingTheCurrentQuestion() {
        val messages = listOf(
            LlmMessage(LlmRole.User, "旧提问"),
            LlmMessage(LlmRole.Assistant, "旧回答"),
            LlmMessage(LlmRole.User, "当前提问"),
        )

        val dropped = ContextDegradation.dropOldestTurns(
            messages,
            targetTokens = 0,
            budget = TenPerToken(maxTokens = 100),
        )

        assertTrue("当前提问必须留下", dropped.any { it.content == "当前提问" })
    }

    private class TenPerToken(override val maxTokens: Int) : TokenBudget {
        override fun estimate(text: String): Int = text.length / 10
    }

    @Test
    fun compactedHistoryStillCarriesRecentToolResultVerbatim() {
        val context = ContextManager(systemPrompt = "sys")
        context.recordToolTurn(
            userText = "q",
            toolExchange = listOf(
                LlmMessage(LlmRole.Assistant, "", listOf(LlmToolCall("c1", "shell_exec", "{}"))),
                toolMessage("c1", """{"stdout":"${"x".repeat(300)}"}"""),
            ),
            finalText = "a",
        )

        val tool = context.buildRequest("next").messages.single { it.role == LlmRole.Tool }

        assertFalse("最近的工具结果不该被压", tool.content.contains("compacted"))
    }

    /**
     * 协议三段分离：user → assistant(tool_calls) → tool → assistant(最终答案)。
     * 最终答案与 tool_calls 合并成一条 assistant 会被服务端拒。
     */
    @Test
    fun toolTurnKeepsProtocolSegmentOrder() {
        val context = ContextManager(systemPrompt = "sys")
        context.recordToolTurn(
            userText = "q",
            toolExchange = listOf(
                LlmMessage(LlmRole.Assistant, "", listOf(LlmToolCall("c1", "shell_exec", "{}"))),
                toolMessage("c1", """{"stdout":"x"}"""),
            ),
            finalText = "最终答案",
        )

        val roles = context.beginTurn("next").map { it.role }

        assertEquals(
            listOf(
                LlmRole.System,
                LlmRole.User,
                LlmRole.Assistant,
                LlmRole.Tool,
                LlmRole.Assistant,
                LlmRole.User,
            ),
            roles,
        )
    }
}
