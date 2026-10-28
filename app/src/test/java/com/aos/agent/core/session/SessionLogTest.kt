package com.aos.agent.core.session

import com.aos.agent.core.llm.LlmMessage
import com.aos.agent.core.llm.LlmRole
import com.aos.agent.core.llm.LlmToolCall
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionLogTest {

    private fun tempDir(): File = Files.createTempDirectory("session-log-test").toFile()

    private fun exchange() = listOf(
        LlmMessage(LlmRole.Assistant, "", listOf(LlmToolCall("c1", "shell_exec", "{}"))),
        LlmMessage(LlmRole.Tool, """{"stdout":"hi"}""", toolCallId = "c1", toolName = "shell_exec"),
    )

    @Test
    fun appendThenReadRoundTripsOneTurn() {
        val dir = tempDir()
        SessionLog.append(dir, SessionLog.TurnRecord(123L, "查电量", exchange(), "目前 80%"))

        val records = SessionLog.readRecent(dir)

        assertEquals(1, records.size)
        assertEquals(123L, records.single().timestamp)
        assertEquals("查电量", records.single().user)
        assertEquals("目前 80%", records.single().assistant)
        assertEquals(2, records.single().toolExchange.size)
        assertEquals(LlmRole.Assistant, records.single().toolExchange[0].role)
        assertEquals(listOf("c1"), records.single().toolExchange[0].toolCalls.map { it.id })
        assertEquals(LlmRole.Tool, records.single().toolExchange[1].role)
        assertEquals("c1", records.single().toolExchange[1].toolCallId)
    }

    @Test
    fun readRecentReturnsOnlyTheLastTurn() {
        val dir = tempDir()
        SessionLog.append(dir, SessionLog.TurnRecord(1L, "第一问", emptyList(), "第一答"))
        SessionLog.append(dir, SessionLog.TurnRecord(2L, "第二问", emptyList(), "第二答"))

        val records = SessionLog.readRecent(dir, lastTurns = 1)

        assertEquals(listOf("第二问"), records.map { it.user })
    }

    @Test
    fun corruptedLineIsSkippedNotFatal() {
        val dir = tempDir()
        SessionLog.append(dir, SessionLog.TurnRecord(1L, "好问题", emptyList(), "好答案"))
        File(dir, SessionLog.LOG_NAME).appendText("这不是 json\n")

        val records = SessionLog.readRecent(dir, lastTurns = 5)

        assertEquals(listOf("好问题"), records.map { it.user })
    }

    @Test
    fun rotateKeepsOldFileAsEvidence() {
        val dir = tempDir()
        SessionLog.append(dir, SessionLog.TurnRecord(1L, "q", emptyList(), "a"))

        SessionLog.rotate(dir, timestamp = 999L)

        assertTrue("旧文件应保留作排查证据", File(dir, "session-999.jsonl").exists())
        assertTrue("新会话从空文件开始", SessionLog.readRecent(dir).isEmpty())
    }

    @Test
    fun emptyDirReadsEmpty() {
        assertTrue(SessionLog.readRecent(tempDir()).isEmpty())
    }
}
