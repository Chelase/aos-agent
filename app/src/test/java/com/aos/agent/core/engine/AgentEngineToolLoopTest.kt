package com.aos.agent.core.engine

import com.aos.agent.core.llm.LlmProvider
import com.aos.agent.core.llm.LlmRequest
import com.aos.agent.core.llm.LlmRole
import com.aos.agent.core.llm.LlmStreamChunk
import com.aos.agent.core.tools.Tool
import com.aos.agent.core.tools.ToolLoopGuard
import com.aos.agent.core.tools.ToolPermission
import com.aos.agent.core.tools.ToolResult
import com.aos.agent.core.tools.ToolSystem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentEngineToolLoopTest {

    private class ScriptedProvider(private val rounds: List<List<LlmStreamChunk>>) : LlmProvider {
        val requests = mutableListOf<LlmRequest>()
        private var index = 0

        override val id = "scripted"

        override fun stream(request: LlmRequest): Flow<LlmStreamChunk> {
            requests += request
            val chunks = rounds[minOf(index, rounds.lastIndex)]
            index++
            return flow { chunks.forEach { emit(it) } }
        }
    }

    private class FakeTool(
        override val name: String,
        private val reply: JsonObject = buildJsonObject { put("value", 1.0) },
    ) : Tool {
        val received = mutableListOf<JsonObject>()

        override val version = 1
        override val category = "test"
        override val description = "假工具 $name"
        override val permission = ToolPermission.Auto
        override val inputSchema = buildJsonObject { put("type", "object") }

        override suspend fun execute(args: JsonObject): ToolResult {
            received += args
            return ToolResult.Ok(reply)
        }
    }

    private fun toolCall(id: String, name: String, arguments: String) = listOf(
        LlmStreamChunk.ToolCallDelta(0, id, name, arguments),
        LlmStreamChunk.Done,
    )

    private fun answer(vararg pieces: String) = pieces.map { LlmStreamChunk.Text(it) } + LlmStreamChunk.Done

    private fun engine(provider: LlmProvider, tools: ToolSystem?) =
        AgentEngine(provider, ContextManager(systemPrompt = "sys"), tools)

    @Test
    fun toolCallThenFinalAnswerEmitsBothEventKinds() = runTest {
        val tool = FakeTool("vehicle_basic")
        val system = ToolSystem().apply { register(tool) }
        val provider = ScriptedProvider(
            listOf(
                toolCall("call_1", "vehicle_basic", """{"fields":["SPEED"]}"""),
                answer("车速 12 m/s"),
            ),
        )

        val events = engine(provider, system).run("现在车速多少").toList()

        assertEquals(AgentEvent.Started, events.first())
        assertEquals("call_1", (events.single { it is AgentEvent.ToolCalled } as AgentEvent.ToolCalled).callId)
        val finished = events.single { it is AgentEvent.ToolFinished } as AgentEvent.ToolFinished
        assertEquals("ok", finished.outcome)
        assertEquals("车速 12 m/s", (events.last() as AgentEvent.Completed).text)
        assertEquals("""{"fields":["SPEED"]}""", tool.received.single().toString())
    }

    @Test
    fun toolResultGoesBackAsToolRoleMessage() = runTest {
        val system = ToolSystem().apply { register(FakeTool("vehicle_basic")) }
        val provider = ScriptedProvider(
            listOf(
                toolCall("call_9", "vehicle_basic", "{}"),
                answer("好了"),
            ),
        )

        engine(provider, system).run("查一下").toList()

        val second = provider.requests[1].messages
        val toolMessage = second.single { it.role == LlmRole.Tool }
        assertEquals("call_9", toolMessage.toolCallId)
        assertEquals("vehicle_basic", toolMessage.toolName)
        val assistant = second.single { it.role == LlmRole.Assistant }
        assertEquals(listOf("call_9"), assistant.toolCalls.map { it.id })
    }

    @Test
    fun malformedArgumentsAreRepairedBeforeExecution() = runTest {
        val tool = FakeTool("shell_exec")
        val system = ToolSystem().apply { register(tool) }
        val provider = ScriptedProvider(
            listOf(
                toolCall("c1", "shell_exec", """{"command":"getprop x",}"""),
                answer("完成"),
            ),
        )

        engine(provider, system).run("跑一下").toList()

        assertEquals("getprop x", (tool.received.single()["command"] as JsonPrimitive).content)
    }

    @Test
    fun repeatedIdenticalCallIsAborted() = runTest {
        val system = ToolSystem().apply { register(FakeTool("vehicle_basic")) }
        val provider = ScriptedProvider(
            listOf(
                toolCall("a", "vehicle_basic", """{"fields":["SPEED"]}"""),
                toolCall("b", "vehicle_basic", """{"fields":["SPEED"]}"""),
                toolCall("c", "vehicle_basic", """{"fields":["SPEED"]}"""),
            ),
        )

        val events = engine(provider, system).run("再来").toList()

        val failure = events.last() as AgentEvent.Failed
        assertTrue("应报重复调用：${failure.message}", failure.message.contains("重复调用"))
    }

    @Test
    fun stepLimitStopsTheLoop() = runTest {
        val system = ToolSystem().apply { register(FakeTool("vehicle_basic")) }
        val provider = ScriptedProvider(
            (1..12).map { toolCall("call_$it", "vehicle_basic", """{"n":$it}""") },
        )

        val events = AgentEngine(provider, ContextManager("sys"), system, ToolLoopGuard(maxSteps = 3))
            .run("转起来").toList()

        val failure = events.last() as AgentEvent.Failed
        assertTrue(failure.message.contains("最大推理步数"))
        assertEquals(3, provider.requests.size)
    }

    @Test
    fun toolCallWithoutToolSystemFailsLoudly() = runTest {
        val provider = ScriptedProvider(listOf(toolCall("x", "vehicle_basic", "{}")))

        val events = engine(provider, null).run("查").toList()

        assertTrue((events.last() as AgentEvent.Failed).message.contains("没有接入 ToolSystem"))
    }

    @Test
    fun rejectedToolIsReportedNotThrown() = runTest {
        val system = ToolSystem().apply {
            register(
                object : Tool {
                    override val name = "risky"
                    override val version = 1
                    override val category = "test"
                    override val description = "需要确认"
                    override val permission = ToolPermission.Ask
                    override val inputSchema = buildJsonObject { put("type", "object") }
                    override suspend fun execute(args: JsonObject) = ToolResult.Ok(buildJsonObject { })
                },
            )
        }
        val provider = ScriptedProvider(
            listOf(toolCall("r1", "risky", "{}"), answer("算了")),
        )

        val events = engine(provider, system).run("试试").toList()

        val finished = events.single { it is AgentEvent.ToolFinished } as AgentEvent.ToolFinished
        assertEquals("rejected", finished.outcome)
        assertEquals("算了", (events.last() as AgentEvent.Completed).text)
    }
}
