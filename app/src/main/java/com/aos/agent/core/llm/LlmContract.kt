package com.aos.agent.core.llm

import kotlinx.serialization.json.JsonObject

enum class LlmRole { System, User, Assistant, Tool }

/** 协议侧的角色字面量，与内部枚举分开，换协议不牵动调用方。 */
internal fun LlmRole.wire(): String = when (this) {
    LlmRole.System -> "system"
    LlmRole.User -> "user"
    LlmRole.Assistant -> "assistant"
    LlmRole.Tool -> "tool"
}

/** 模型发起的一次工具调用。[argumentsJson] 是模型原文，可能畸形，交给 ToolJsonRepair。 */
data class LlmToolCall(
    val id: String,
    val name: String,
    val argumentsJson: String,
)

/**
 * [toolCalls] 属于 assistant 轮，[toolCallId] 与 [toolName] 属于 tool 轮；
 * 其余场景留空，序列化时按 OpenAI 形态取舍。
 */
data class LlmMessage(
    val role: LlmRole,
    val content: String,
    val toolCalls: List<LlmToolCall> = emptyList(),
    val toolCallId: String? = null,
    val toolName: String? = null,
)

data class ToolDefinition(
    val name: String,
    val description: String,
    val parameters: JsonObject,
)

data class LlmRequest(
    val messages: List<LlmMessage>,
    val toolDefinitions: List<ToolDefinition> = emptyList(),
    val modelOverride: String? = null,
)

sealed interface LlmStreamChunk {
    data class Text(val delta: String) : LlmStreamChunk

    /** 流式 function calling 的增量片段，按 [index] 聚合成一次完整调用。 */
    data class ToolCallDelta(
        val index: Int,
        val id: String?,
        val name: String?,
        val argumentsDelta: String?,
    ) : LlmStreamChunk

    /** 流正常结束。 */
    data object Done : LlmStreamChunk

    /** 传输或协议层失败；已产出的 Text 不回滚。 */
    data class Failed(val cause: Throwable) : LlmStreamChunk
}
