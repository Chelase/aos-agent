package com.aos.agent.core.engine

import com.aos.agent.core.llm.LlmProvider
import com.aos.agent.core.llm.LlmStreamChunk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * 无头 Agent 主循环。
 *
 * 不引用任何 `android.*`：能被 JVM 测试直接驱动，这正是"没有界面也能干活"的证据，
 * 也是 Step 5 里 AOC `skill_request` 的入口。
 *
 * Step 3 会在 [run] 的流上插入 `ToolCall` / `ToolResult` 事件并接 function calling 循环，
 * 现有事件语义不变。
 */
class AgentEngine(
    private val provider: LlmProvider,
    private val context: ContextManager,
) {
    fun run(query: String): Flow<AgentEvent> = flow {
        emit(AgentEvent.Started)
        val answer = StringBuilder()
        var failure: Throwable? = null

        provider.stream(context.buildRequest(query)).collect { chunk ->
            when (chunk) {
                is LlmStreamChunk.Text -> {
                    answer.append(chunk.delta)
                    emit(AgentEvent.Token(chunk.delta))
                }

                LlmStreamChunk.Done -> Unit

                is LlmStreamChunk.Failed -> failure = chunk.cause
            }
        }

        val error = failure
        if (error != null) {
            emit(AgentEvent.Failed(message = error.message ?: error.javaClass.simpleName, cause = error))
        } else {
            val fullText = answer.toString()
            context.recordExchange(query, fullText)
            emit(AgentEvent.Completed(fullText))
        }
    }
}
