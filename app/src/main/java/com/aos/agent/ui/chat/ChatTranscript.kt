package com.aos.agent.ui.chat

import com.aos.agent.core.engine.AgentEvent

/** 对话流里的一条内容。工具轨迹单独成行，是这一页存在的理由。 */
sealed interface ChatEntry {
    data class User(val text: String) : ChatEntry
    data class ToolCalled(val tool: String, val args: String) : ChatEntry
    data class ToolFinished(val tool: String, val outcome: String, val payload: String) : ChatEntry
    data class Assistant(val text: String) : ChatEntry
    data class Note(val text: String) : ChatEntry
}

/**
 * 把引擎事件流折叠成对话条目。纯函数，便于单测：
 * 界面只负责渲染，不解释协议。
 */
object ChatTranscript {

    fun startTurn(entries: MutableList<ChatEntry>, query: String, matchedSkill: String?): List<ChatEntry> {
        entries += ChatEntry.User(query)
        matchedSkill?.let { entries += ChatEntry.Note("命中 skill：$it") }
        return entries.toList()
    }

    fun apply(entries: MutableList<ChatEntry>, event: AgentEvent) {
        when (event) {
            AgentEvent.Started -> Unit

            is AgentEvent.Token -> {
                val last = entries.lastOrNull()
                if (last is ChatEntry.Assistant) {
                    entries[entries.lastIndex] = ChatEntry.Assistant(last.text + event.text)
                } else {
                    entries += ChatEntry.Assistant(event.text)
                }
            }

            is AgentEvent.ToolCalled -> entries += ChatEntry.ToolCalled(event.toolName, event.arguments)
            is AgentEvent.ToolFinished -> entries += ChatEntry.ToolFinished(event.toolName, event.outcome, event.payload)
            is AgentEvent.Completed -> if (entries.lastOrNull() !is ChatEntry.Assistant) {
                entries += ChatEntry.Assistant(event.text)
            }

            is AgentEvent.Failed -> entries += ChatEntry.Note("失败：${event.message}")
        }
    }
}
