package com.aos.agent.core.tools

import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolSystemTest {

    private val emptyArgs: JsonObject = buildJsonObject { }

    private class RecordingTool(
        override val name: String,
        override val permission: ToolPermission,
        private val delayMillis: Long = 0,
        private val boom: Boolean = false,
    ) : Tool {
        var calls = 0

        override val version = 1
        override val category = "test"
        override val description = "测试工具 $name"
        override val inputSchema = buildJsonObject { put("type", "object") }

        override suspend fun execute(args: JsonObject): ToolResult {
            calls++
            if (boom) throw IllegalStateException("内部细节不该外泄")
            if (delayMillis > 0) delay(delayMillis)
            return ToolResult.Ok(buildJsonObject { put("ping", name) })
        }
    }

    @Test
    fun unknownToolBecomesStructuredError() = runTest {
        val result = ToolSystem().execute("nope", emptyArgs)

        assertTrue(result is ToolResult.Error)
        assertTrue((result as ToolResult.Error).cause.contains("unknown tool"))
    }

    @Test
    fun autoToolRunsImmediately() = runTest {
        val tool = RecordingTool("auto_one", ToolPermission.Auto)
        val system = ToolSystem()
        system.register(tool)

        assertTrue(system.execute("auto_one", emptyArgs) is ToolResult.Ok)
        assertEquals(1, tool.calls)
    }

    /** 没有确认器时 Ask 必须拒绝——fail-closed，不能退化成自动放行。 */
    @Test
    fun askWithoutConfirmerIsRejectedAndNeverExecutes() = runTest {
        val tool = RecordingTool("ask_one", ToolPermission.Ask)
        val system = ToolSystem()
        system.register(tool)

        val result = system.execute("ask_one", emptyArgs)

        assertTrue(result is ToolResult.Rejected)
        assertEquals(0, tool.calls)
    }

    @Test
    fun askWithConfirmer_canAllowOrDeny() = runTest {
        val allowed = RecordingTool("ask_two", ToolPermission.Ask)
        val denied = RecordingTool("ask_three", ToolPermission.Ask)
        val allowSystem = ToolSystem(confirmer = ToolConfirmer { _, _ -> true })
        val denySystem = ToolSystem(confirmer = ToolConfirmer { _, _ -> false })
        allowSystem.register(allowed)
        denySystem.register(denied)

        assertTrue(allowSystem.execute("ask_two", emptyArgs) is ToolResult.Ok)
        assertTrue(denySystem.execute("ask_three", emptyArgs) is ToolResult.Rejected)
        assertEquals(0, denied.calls)
    }

    @Test
    fun forbiddenToolNeverRuns() = runTest {
        val tool = RecordingTool("blocked", ToolPermission.Forbid)
        val system = ToolSystem(confirmer = ToolConfirmer { _, _ -> true })
        system.register(tool)

        assertTrue(system.execute("blocked", emptyArgs) is ToolResult.Rejected)
        assertEquals(0, tool.calls)
    }

    @Test
    fun slowToolHitsTimeout() = runTest {
        val system = ToolSystem(timeoutMillis = 50)
        system.register(RecordingTool("slow", ToolPermission.Auto, delayMillis = 1_000))

        val result = system.execute("slow", emptyArgs)

        assertTrue(result is ToolResult.TimedOut)
        assertEquals(50L, (result as ToolResult.TimedOut).timeoutMillis)
    }

    /** 工具内部异常只能带类型出去，消息可能含用户数据。 */
    @Test
    fun toolExceptionIsWrappedNotThrown() = runTest {
        val system = ToolSystem()
        system.register(RecordingTool("boom", ToolPermission.Auto, boom = true))

        val result = system.execute("boom", emptyArgs)

        assertTrue(result is ToolResult.Error)
        val cause = (result as ToolResult.Error).cause
        assertTrue("不该泄漏异常消息：$cause", cause.contains("IllegalStateException") && !cause.contains("内部细节"))
    }

    @Test
    fun exposedForFiltersSubsetAndIgnoresUnknownNames() {
        val system = ToolSystem()
        system.register(RecordingTool("a", ToolPermission.Auto))
        system.register(RecordingTool("b", ToolPermission.Auto))

        assertEquals(listOf("a"), system.exposedFor(setOf("a", "ghost")).map { it.name })
        assertEquals(2, system.exposedFor(null).size)
        assertNull(system.find("ghost"))
    }

    @Test
    fun definitionsCarrySchemaTriple() {
        val system = ToolSystem()
        system.register(RecordingTool("a", ToolPermission.Auto))

        val definition = system.definitions().single()

        assertEquals("a", definition.name)
        assertEquals("测试工具 a", definition.description)
        assertEquals("object", (definition.parameters["type"] as? kotlinx.serialization.json.JsonPrimitive)?.content)
    }
}
