package com.aos.agent.core.engine

import com.aos.agent.core.llm.LlmProvider
import com.aos.agent.core.llm.LlmRequest
import com.aos.agent.core.llm.LlmRole
import com.aos.agent.core.llm.LlmStreamChunk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class AgentEngineTest {

    /** 记录请求，便于断言多轮上下文是否真的带上了上一轮。 */
    private class FakeProvider(
        private val chunks: List<LlmStreamChunk>,
        private val recorder: (LlmRequest) -> Unit = {},
    ) : LlmProvider {
        val requests = mutableListOf<LlmRequest>()

        override val id = "fake"

        override fun stream(request: LlmRequest): Flow<LlmStreamChunk> {
            recorder(request)
            requests += request
            return flow { chunks.forEach { emit(it) } }
        }
    }

    private fun text(vararg pieces: String) = pieces.map { LlmStreamChunk.Text(it) }

    @Test
    fun run_emitsStartTokensThenCompleted() = runTest {
        val engine = AgentEngine(
            provider = FakeProvider(text("你", "好", "呀")),
            context = ContextManager(systemPrompt = "你是车机助手"),
        )

        val events = engine.run("在吗").toList()

        assertEquals(AgentEvent.Started, events.first())
        assertEquals(
            listOf("你", "好", "呀"),
            events.filterIsInstance<AgentEvent.Token>().map { it.text },
        )
        val last = events.last()
        assertTrue("期望 Completed，实际 $last", last is AgentEvent.Completed)
        assertEquals("你好呀", (last as AgentEvent.Completed).text)
    }

    @Test
    fun run_reportsFailureAsEventAndKeepsStreamClosed() = runTest {
        val boom = IOException("connection reset")
        val engine = AgentEngine(
            provider = FakeProvider(text("部分") + LlmStreamChunk.Failed(boom)),
            context = ContextManager(systemPrompt = "sys"),
        )

        val events = engine.run("走起").toList()

        assertEquals(1, events.filterIsInstance<AgentEvent.Failed>().size)
        val failure = events.filterIsInstance<AgentEvent.Failed>().single()
        assertEquals("connection reset", failure.message)
        assertEquals(boom, failure.cause)
        assertTrue("失败轮次不应产出 Completed", events.none { it is AgentEvent.Completed })
    }

    @Test
    fun run_carriesPreviousTurnIntoNextRequest() = runTest {
        val provider = FakeProvider(text("第一轮答"))
        val context = ContextManager(systemPrompt = "sys")
        val engine = AgentEngine(provider = provider, context = context)

        engine.run("第一问").toList()
        engine.run("第二问").toList()

        val second = provider.requests.last().messages
        assertEquals(LlmRole.System, second.first().role)
        assertEquals(
            listOf(LlmRole.User, LlmRole.Assistant, LlmRole.User),
            second.drop(1).map { it.role },
        )
        assertEquals("第一轮答", second[2].content)
        assertEquals("第二问", second[3].content)
    }

    @Test
    fun failedTurn_doesNotPolluteHistory() = runTest {
        val context = ContextManager(systemPrompt = "sys")
        val engine = AgentEngine(
            provider = FakeProvider(listOf(LlmStreamChunk.Failed(IOException("boom")))),
            context = context,
        )

        engine.run("问").toList()

        assertEquals(0, context.turnCount)
    }
}
