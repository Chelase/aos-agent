package com.aos.agent.core.tools

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.test.runTest
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

class FileReadToolTest {

    private fun tool(dir: File) = FileReadTool(listOf(dir))

    private fun args(path: String, offset: Int? = null, limit: Int? = null, grep: String? = null) =
        buildJsonObject {
            put("path", path)
            offset?.let { put("offset", it) }
            limit?.let { put("limit", it) }
            grep?.let { put("grep", it) }
        }

    @Test
    fun pagesThroughLines() = runTest {
        val dir = createTempDirectory("file-read").toFile()
        val file = File(dir, "out.txt").apply {
            writeText((1..10).joinToString("\n") { "line-$it" })
        }

        val first = (tool(dir).execute(args(file.absolutePath, limit = 4)) as ToolResult.Ok).fields
        assertEquals(10, first["total_lines"]!!.jsonPrimitive.int)
        assertEquals(4, first["returned_lines"]!!.jsonPrimitive.int)
        assertTrue(first["has_more"]!!.jsonPrimitive.boolean)
        assertTrue(first["stdout"]!!.jsonPrimitive.content.startsWith("line-1"))

        val second = (tool(dir).execute(args(file.absolutePath, offset = 4, limit = 4)) as ToolResult.Ok).fields
        assertEquals("line-5\nline-6\nline-7\nline-8", second["stdout"]!!.jsonPrimitive.content)
    }

    @Test
    fun grepFiltersBeforePaging() = runTest {
        val dir = createTempDirectory("file-read").toFile()
        val file = File(dir, "out.txt").apply {
            writeText("alpha\nSupported: foo\nbeta\nSupported: bar\n")
        }

        val fields = (tool(dir).execute(
            args(file.absolutePath, grep = "Supported"),
        ) as ToolResult.Ok).fields

        assertEquals(2, fields["total_lines"]!!.jsonPrimitive.int)
        assertFalse(fields["has_more"]!!.jsonPrimitive.boolean)
    }

    @Test
    fun outsideAllowedDirIsRejectedNotRead() = runTest {
        val dir = createTempDirectory("file-read-allowed").toFile()
        val outside = createTempDirectory("file-read-outside").toFile()
        val secret = File(outside, "secret.txt").apply { writeText("nope") }

        val result = tool(dir).execute(args(secret.absolutePath))

        assertTrue(result is ToolResult.Rejected)
        assertEquals(RejectionReason.Forbidden, (result as ToolResult.Rejected).reason)
    }

    @Test
    fun missingFileIsError() = runTest {
        val dir = createTempDirectory("file-read").toFile()

        assertTrue(tool(dir).execute(args(File(dir, "nope.txt").absolutePath)) is ToolResult.Error)
    }

    @Test
    fun badPagingArgsAreError() = runTest {
        val dir = createTempDirectory("file-read").toFile()
        val file = File(dir, "out.txt").apply { writeText("x") }

        assertTrue(tool(dir).execute(args(file.absolutePath, offset = -1)) is ToolResult.Error)
        assertTrue(tool(dir).execute(args(file.absolutePath, limit = 0)) is ToolResult.Error)
    }

    @Test
    fun hasMoreHintTellsModelHowToContinue() = runTest {
        val dir = createTempDirectory("file-read").toFile()
        val file = File(dir, "out.txt").apply {
            writeText((1..10).joinToString("\n") { "line-$it" })
        }

        val fields = (tool(dir).execute(args(file.absolutePath, limit = 3)) as ToolResult.Ok).fields
        val notice = fields["notice"]!!.jsonPrimitive.contentOrNull.orEmpty()

        assertTrue("应告诉模型继续读：$notice", notice.contains("offset=3"))
    }
}
