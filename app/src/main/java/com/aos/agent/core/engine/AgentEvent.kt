package com.aos.agent.core.engine

/**
 * 引擎对外只暴露结构化事件，不暴露回调或界面。
 * AOC 的 `skill_request`（二期）与单元测试消费的是同一条流。
 *
 * 契约固定：`Started → (Token | ToolCalled | ToolFinished)* → Completed | Failed`。
 * 后续只能在流上**加**事件，不能改既有语义（见 mechanisms/agent-capabilities.md 关键约束 6）。
 */
sealed interface AgentEvent {
    data object Started : AgentEvent

    /** 模型增量文本。 */
    data class Token(val text: String) : AgentEvent

    data class ToolCalled(val callId: String, val toolName: String, val arguments: String) : AgentEvent

    /** [outcome] 取值：ok / rejected / timeout / error / unavailable。 */
    data class ToolFinished(
        val callId: String,
        val toolName: String,
        val outcome: String,
        val payload: String,
    ) : AgentEvent

    /** 本轮正常结束，[text] 为完整回复。 */
    data class Completed(val text: String) : AgentEvent

    /** 本轮失败。失败也走事件流，不向调用方抛异常，避免半截流卡死宿主。 */
    data class Failed(val message: String, val cause: Throwable? = null) : AgentEvent
}
