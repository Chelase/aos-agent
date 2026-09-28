package com.aos.agent.ui.chat

import com.aos.agent.core.engine.AgentEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatTranscriptTest {

    @Test
    fun tokensMergeIntoOneAssistantBubble() {
        val entries = mutableListOf<ChatEntry>()

        ChatTranscript.apply(entries, AgentEvent.Started)
        ChatTranscript.apply(entries, AgentEvent.Token("车速 "))
        ChatTranscript.apply(entries, AgentEvent.Token("0 m/s"))
        ChatTranscript.apply(entries, AgentEvent.Completed("车速 0 m/s"))

        assertEquals(1, entries.count { it is ChatEntry.Assistant })
        assertEquals("车速 0 m/s", (entries.last { it is ChatEntry.Assistant } as ChatEntry.Assistant).text)
    }

    @Test
    fun toolTraceKeepsOrderAndOutcome() {
        val entries = mutableListOf<ChatEntry>()

        ChatTranscript.startTurn(entries, "车速多少", "车辆状态查询")
        ChatTranscript.apply(entries, AgentEvent.ToolCalled("c1", "vehicle_basic", "{}"))
        ChatTranscript.apply(entries, AgentEvent.ToolFinished("c1", "vehicle_basic", "ok", """{"v":0}"""))
        ChatTranscript.apply(entries, AgentEvent.Token("停了"))

        assertEquals(
            listOf("User", "Note", "ToolCalled", "ToolFinished", "Assistant"),
            entries.map { it.javaClass.simpleName },
        )
        assertEquals("命中 skill：车辆状态查询", (entries[1] as ChatEntry.Note).text)
    }

    @Test
    fun failureBecomesVisibleNote() {
        val entries = mutableListOf<ChatEntry>()

        ChatTranscript.apply(entries, AgentEvent.Failed("未配置模型服务"))

        assertTrue((entries.single() as ChatEntry.Note).text.contains("未配置模型服务"))
    }
}
