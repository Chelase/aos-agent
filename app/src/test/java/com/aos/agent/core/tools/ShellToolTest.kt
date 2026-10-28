package com.aos.agent.core.tools

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShellToolTest {

    private val captured = mutableListOf<List<String>>()

    private fun runner(output: CommandOutput = CommandOutput(0, "ok", "")): CommandRunner =
        CommandRunner { argv ->
            captured += argv
            output
        }

    private fun args(
        command: String,
        grep: String? = null,
        head: Int? = null,
    ): JsonObject = buildJsonObject {
        put("command", command)
        grep?.let { put("grep", it) }
        head?.let { put("head", it) }
    }

    private fun tool(
        auto: Set<String> = setOf("getprop"),
        ask: Set<String> = setOf("settings"),
        blocked: Set<String> = setOf("rm"),
        output: CommandOutput = CommandOutput(0, "ok", ""),
        outputDir: File? = null,
    ) = ShellTool(runner(output), auto, ask, blocked, outputDir)

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

    @Test
    fun grepKeepsOnlyMatchingLines() = runTest {
        val stdout = "alpha\nSupported Properties: foo\nbeta\nSupported Properties: bar\n"
        val fields = (tool(output = CommandOutput(0, stdout, ""))
            .execute(args("dumpsys car_service", grep = "Supported Properties")) as ToolResult.Ok).fields

        assertEquals(
            "Supported Properties: foo\nSupported Properties: bar",
            fields["stdout"]!!.jsonPrimitive.content,
        )
        assertEquals(2, fields["total_lines"]!!.jsonPrimitive.int)
        assertFalse(fields["truncated"]!!.jsonPrimitive.boolean)
    }

    @Test
    fun headLimitsFilteredLines() = runTest {
        val stdout = (1..10).joinToString("\n") { "line-$it" }
        val fields = (tool(output = CommandOutput(0, stdout, ""))
            .execute(args("getprop x", head = 3)) as ToolResult.Ok).fields

        assertEquals("line-1\nline-2\nline-3", fields["stdout"]!!.jsonPrimitive.content)
        assertEquals(3, fields["total_lines"]!!.jsonPrimitive.int)
    }

    @Test
    fun longOutputIsHeadTailTruncatedAndPersisted() = runTest {
        val dir = createTempDirectory("shell-tool-out").toFile()
        val body = "H".repeat(5_000) + "MID" + "T".repeat(5_000)
        val fields = (tool(output = CommandOutput(0, body, ""), outputDir = dir)
            .execute(args("dumpsys car_service")) as ToolResult.Ok).fields

        assertTrue(fields["truncated"]!!.jsonPrimitive.boolean)
        assertTrue(fields["stdout"]!!.jsonPrimitive.content.contains("省略"))
        val path = checkNotNull(fields["full_output"]!!.jsonPrimitive.contentOrNull)
        assertTrue(File(path).isFile)
        assertEquals(body, File(path).readText())
    }

    /** 截断时模型看不到正文，必须告诉它去哪读完整输出、还有多少字节。 */
    @Test
    fun truncatedOutputTellsTheModelWhereToReadTheRest() = runTest {
        val dir = createTempDirectory("shell-tool-out").toFile()
        val body = "H".repeat(5_000) + "MID" + "T".repeat(5_000)
        val fields = (tool(output = CommandOutput(0, body, ""), outputDir = dir)
            .execute(args("dumpsys car_service")) as ToolResult.Ok).fields

        val notice = checkNotNull(fields["truncation_notice"]!!.jsonPrimitive.contentOrNull)
        val path = checkNotNull(fields["full_output"]!!.jsonPrimitive.contentOrNull)
        assertTrue("应带完整输出路径：$notice", notice.contains(path))
        assertTrue("应提示可二次读取：$notice", notice.contains("再次读取"))
        assertTrue("应给出原始字符数：$notice", notice.contains(body.length.toString()))
    }

    @Test
    fun untruncatedOutputHasNoTruncationNotice() = runTest {
        val fields = (tool(output = CommandOutput(0, "short", ""), outputDir = createTempDirectory("shell-tool-out").toFile())
            .execute(args("getprop x")) as ToolResult.Ok).fields

        assertFalse(fields.containsKey("truncation_notice"))
    }

    @Test
    fun truncateHeadTailKeepsEnds() {
        val text = "A".repeat(100) + "MIDDLE" + "Z".repeat(100)
        val (clipped, truncated) = ProcessCommandRunner.truncateHeadTail(text, keepEachSide = 10)

        assertTrue(truncated)
        assertTrue(clipped.startsWith("AAAAAAAAAA"))
        assertTrue(clipped.endsWith("ZZZZZZZZZZ"))
        assertTrue(clipped.contains("省略"))
    }
}
