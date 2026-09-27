package com.aos.agent.core.engine

import com.aos.agent.core.llm.LlmMessage
import com.aos.agent.core.llm.LlmProvider
import com.aos.agent.core.llm.LlmRequest
import com.aos.agent.core.llm.LlmRole
import com.aos.agent.core.llm.LlmStreamChunk
import com.aos.agent.core.llm.LlmToolCall
import com.aos.agent.core.tools.ToolJsonRepair
import com.aos.agent.core.tools.ToolLoopGuard
import com.aos.agent.core.tools.ToolResult
import com.aos.agent.core.tools.ToolSystem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * 无头 Agent 主循环。
 *
 * 不引用任何 `android.*`：能被 JVM 测试直接驱动，这正是"没有界面也能干活"的证据。
 * [tools] 为空时退化为纯对话循环（Step 2 行为不变）。
 */
class AgentEngine(
    private val provider: LlmProvider,
    private val context: ContextManager,
    private val tools: ToolSystem? = null,
    private val guard: ToolLoopGuard = ToolLoopGuard(),
) {
    fun run(query: String): Flow<AgentEvent> = flow {
        emit(AgentEvent.Started)
        val messages = context.beginTurn(query)
        guard.reset()
        var step = 0

        while (true) {
            step++
            if (guard.stepsExhausted(step)) {
                emit(AgentEvent.Failed("超过最大推理步数 ${guard.maxSteps}，工具循环疑似打转"))
                return@flow
            }

            val text = StringBuilder()
            val calls = linkedMapOf<Int, ToolCallAccumulator>()
            var streamFailure: Throwable? = null

            provider.stream(
                LlmRequest(
                    messages = messages.toList(),
                    toolDefinitions = tools?.definitions().orEmpty(),
                ),
            ).collect { chunk ->
                when (chunk) {
                    is LlmStreamChunk.Text -> {
                        text.append(chunk.delta)
                        emit(AgentEvent.Token(chunk.delta))
                    }

                    is LlmStreamChunk.ToolCallDelta ->
                        calls.getOrPut(chunk.index) { ToolCallAccumulator() }.merge(chunk)

                    LlmStreamChunk.Done -> Unit

                    is LlmStreamChunk.Failed -> streamFailure = chunk.cause
                }
            }

            streamFailure?.let { error ->
                emit(AgentEvent.Failed(error.message ?: error.javaClass.simpleName, error))
                return@flow
            }

            val toolCalls = calls.values.mapNotNull { it.toCall() }
            if (toolCalls.isEmpty()) {
                val answer = text.toString()
                context.recordExchange(query, answer)
                emit(AgentEvent.Completed(answer))
                return@flow
            }

            if (tools == null) {
                emit(AgentEvent.Failed("模型请求调用工具，但当前没有接入 ToolSystem"))
                return@flow
            }

            messages += LlmMessage(LlmRole.Assistant, text.toString(), toolCalls)
            for (call in toolCalls) {
                if (guard.isRepeat(call)) {
                    emit(AgentEvent.Failed("检测到重复调用：${call.name} 的参数与上一步完全相同"))
                    return@flow
                }
                emit(AgentEvent.ToolCalled(call.id, call.name, call.argumentsJson))
                val outcome = executeTool(tools, call)
                emit(AgentEvent.ToolFinished(call.id, call.name, outcome.kind, outcome.payload))
                messages += LlmMessage(
                    role = LlmRole.Tool,
                    content = outcome.payload,
                    toolCallId = call.id,
                    toolName = call.name,
                )
            }
        }
    }

    private class ToolOutcome(val kind: String, val payload: String)

    private suspend fun executeTool(tools: ToolSystem, call: LlmToolCall): ToolOutcome {
        if (tools.find(call.name) == null) {
            return ToolOutcome("error", errorPayload("unknown tool: ${call.name}"))
        }
        val args = ToolJsonRepair.parseArguments(call.argumentsJson)
            ?: return ToolOutcome("error", errorPayload("工具参数不是合法 JSON，且保守修复后仍解析失败"))
        return when (val result = tools.execute(call.name, args)) {
            is ToolResult.Ok -> ToolOutcome("ok", result.fields.toString())
            is ToolResult.Rejected -> ToolOutcome(
                "rejected",
                buildJsonObject {
                    put("status", "rejected")
                    put("reason", result.reason)
                }.toString(),
            )

            is ToolResult.TimedOut -> ToolOutcome(
                "timeout",
                buildJsonObject {
                    put("status", "timeout")
                    put("timeout_ms", result.timeoutMillis)
                }.toString(),
            )

            is ToolResult.Error -> ToolOutcome("error", errorPayload(result.cause))
        }
    }

    private fun errorPayload(cause: String): String = buildJsonObject {
        put("status", "error")
        put("cause", cause)
    }.toString()

    /** 流式 function calling 的片段聚合：id/name 只在首块出现，arguments 分片到达。 */
    private class ToolCallAccumulator {
        private var id: String? = null
        private var name: String = ""
        private val arguments = StringBuilder()

        fun merge(delta: LlmStreamChunk.ToolCallDelta) {
            delta.id?.let { candidate -> if (id.isNullOrEmpty()) id = candidate }
            delta.name?.let { candidate -> if (name.isEmpty()) name = candidate }
            delta.argumentsDelta?.let { arguments.append(it) }
        }

        fun toCall(): LlmToolCall? {
            val callId = id ?: return null
            if (name.isEmpty() && arguments.isEmpty()) return null
            return LlmToolCall(callId, name, arguments.toString())
        }
    }
}
