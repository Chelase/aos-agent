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

data class LlmMessage(val role: LlmRole, val content: String)

/**
 * 工具定义。Step 2 只占位不解析，Step 3 的 ToolSystem 负责产出。
 */
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

    /** 流正常结束。 */
    data object Done : LlmStreamChunk

    /** 传输或协议层失败；已产出的 Text 不回滚。 */
    data class Failed(val cause: Throwable) : LlmStreamChunk
}
