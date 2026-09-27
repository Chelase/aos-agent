package com.aos.agent.core.tools

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShellToolTest {

    private val captured = mutableListOf<List<String>>()

    private fun runner(output: CommandOutput = CommandOutput(0, "ok", "")): CommandRunner =
        CommandRunner { argv ->
            captured += argv
            output
        }

    private fun args(command: String): JsonObject = buildJsonObject { put("command", command) }

    private fun tool(
        auto: Set<String> = setOf("getprop"),
        ask: Set<String> = setOf("settings"),
        blocked: Set<String> = setOf("rm"),
        output: CommandOutput = CommandOutput(0, "ok", ""),
    ) = ShellTool(runner(output), auto, ask, blocked)

    @Test
    fun permissionFollowsTheCommandNotTheTool() {
        val shell = tool()

        assertEquals(ToolPermission.Auto, shell.permissionFor(args("getprop ro.build.version.release")))
        assertEquals(ToolPermission.Ask, shell.permissionFor(args("settings get global airplane_mode_on")))
        assertEquals(ToolPermission.Ask, shell.permissionFor(args("unknown_binary")))
        assertEquals(ToolPermission.Forbid, shell.permissionFor(args("rm -rf /data")))
        assertEquals(ToolPermission.Forbid, shell.permissionFor(args("")))
    }

    @Test
    fun commandIsTokenizedIntoArgvWithoutShell() = runTest {
        val shell = tool(output = CommandOutput(0, "  15  ", ""))

        val result = shell.execute(args("getprop   ro.build.version.release"))

        assertEquals(listOf("getprop", "ro.build.version.release"), captured.single())
        val fields = (result as ToolResult.Ok).fields
        assertEquals("15", (fields["stdout"] as JsonPrimitive).content)
        assertEquals(0, (fields["exit_code"] as JsonPrimitive).content.toInt())
    }

    /** 不经过 shell 已经挡掉了大部分注入，但含元字符的输入直接拒绝，避免模型误以为生效。 */
    @Test
    fun shellMetacharactersAreRejected() = runTest {
        val shell = tool()

        listOf("getprop x; rm -rf /", "getprop `id`", "getprop a|b", "echo x > /data/y").forEach { command ->
            val result = shell.execute(args(command))
            assertTrue("应拒绝：$command", result is ToolResult.Rejected)
        }
        assertTrue(captured.isEmpty())
    }

    @Test
    fun stderrOnlyAppearsWhenPresent() = runTest {
        val shell = tool(output = CommandOutput(1, "", "boom"))

        val fields = (shell.execute(args("getprop x")) as ToolResult.Ok).fields

        assertTrue(fields.containsKey("stderr"))
        assertEquals("boom", (fields["stderr"] as JsonPrimitive).content)
    }

    @Test
    fun missingCommandArgumentIsError() = runTest {
        assertTrue(tool().execute(buildJsonObject { }) is ToolResult.Error)
    }
}
