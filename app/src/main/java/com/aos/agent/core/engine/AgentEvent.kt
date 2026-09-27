package com.aos.agent.core.engine

/**
 * 引擎对外只暴露结构化事件，不暴露回调或界面。
 * AOC 的 `skill_request`（Step 5）与单元测试（Step 2）消费的是同一条流。
 */
sealed interface AgentEvent {
    data object Started : AgentEvent

    /** 模型增量文本。 */
    data class Token(val text: String) : AgentEvent

    /** 本轮正常结束，[text] 为完整回复。 */
    data class Completed(val text: String) : AgentEvent

    /** 本轮失败。失败也走事件流，不向调用方抛异常，避免半截流卡死宿主。 */
    data class Failed(val message: String, val cause: Throwable? = null) : AgentEvent
}
