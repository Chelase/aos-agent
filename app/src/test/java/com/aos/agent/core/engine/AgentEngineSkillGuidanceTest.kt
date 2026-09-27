package com.aos.agent.core.engine

import com.aos.agent.core.llm.LlmProvider
import com.aos.agent.core.llm.LlmRequest
import com.aos.agent.core.llm.LlmStreamChunk
import com.aos.agent.core.tools.Tool
import com.aos.agent.core.tools.ToolPermission
import com.aos.agent.core.tools.ToolResult
import com.aos.agent.core.tools.ToolSystem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentEngineSkillGuidanceTest {

    private class RecordingProvider : LlmProvider {
        val requests = mutableListOf<LlmRequest>()

        override val id = "recording"

        override fun stream(request: LlmRequest): Flow<LlmStreamChunk> {
            requests += request
            return flow {
                emit(LlmStreamChunk.Text("好"))
                emit(LlmStreamChunk.Done)
            }
        }
    }

    private class StubTool(override val name: String) : Tool {
        override val version = 1
        override val category = "test"
        override val description = "桩工具 $name"
        override val permission = ToolPermission.Auto
        override val inputSchema = buildJsonObject { put("type", "object") }
        override suspend fun execute(args: JsonObject): ToolResult = ToolResult.Ok(buildJsonObject { })
    }

    private fun systemPromptOf(request: LlmRequest): String = request.messages.first().content

    @Test
    fun skillInstructionIsAppendedToSystemPromptForThatTurnOnly() = runTest {
        val provider = RecordingProvider()
        val engine = AgentEngine(provider, ContextManager(systemPrompt = "你是车机助手"))

        engine.run("电量多少", instruction = "回答给出数值与单位").toList()
        engine.run("然后呢").toList()

        assertTrue(
            "本轮应带上 skill 片段",
            systemPromptOf(provider.requests[0]).contains("回答给出数值与单位"),
        )
        assertEquals("下一轮不该残留 skill 片段", "你是车机助手", systemPromptOf(provider.requests[1]))
    }

    @Test
    fun toolSubsetIsNarrowedToWhatTheSkillDeclares() = runTest {
        val system = ToolSystem()
        system.register(StubTool("vehicle_basic"))
        system.register(StubTool("system_info"))
        system.register(StubTool("shell_exec"))
        val provider = RecordingProvider()

        AgentEngine(provider, ContextManager("sys"), system)
            .run("看下系统", toolNames = setOf("system_info"))
            .toList()

        assertEquals(listOf("system_info"), provider.requests.single().toolDefinitions.map { it.name })
    }

    /** skill 写了引擎里没有的名字（例如控制类工具）：过滤后不会出现在工具定义里。 */
    @Test
    fun unknownToolNamesDeclaredBySkillNeverReachTheEngine() = runTest {
        val system = ToolSystem()
        system.register(StubTool("vehicle_basic"))
        val provider = RecordingProvider()

        AgentEngine(provider, ContextManager("sys"), system)
            .run("开窗", toolNames = setOf("vehicle_basic", "control_window"))
            .toList()

        assertEquals(listOf("vehicle_basic"), provider.requests.single().toolDefinitions.map { it.name })
    }

    @Test
    fun nullToolNamesExposesEverythingRegistered() = runTest {
        val system = ToolSystem()
        system.register(StubTool("a"))
        system.register(StubTool("b"))
        val provider = RecordingProvider()

        AgentEngine(provider, ContextManager("sys"), system).run("随便").toList()

        assertEquals(listOf("a", "b"), provider.requests.single().toolDefinitions.map { it.name })
    }
}
