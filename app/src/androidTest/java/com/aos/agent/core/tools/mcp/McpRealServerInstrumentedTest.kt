package com.aos.agent.core.tools.mcp

import com.aos.agent.core.tools.ToolPermission
import com.aos.agent.core.tools.ToolResult
import android.util.Log
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume.assumeTrue

/**
 * 与**真实 MCP server** 的互通验证（官方 TypeScript SDK 起的 Streamable HTTP server）。
 *
 * 只有传了 `-e mcpUrl <url>` 才跑；不传就跳过，避免把外部依赖焊进常规测试。
 * 补测方式见 step6 子计划 §5：本机起 fixture server，模拟器经 10.0.2.2 访问宿主机。
 */
@RunWith(AndroidJUnit4::class)
class McpRealServerInstrumentedTest {

    private companion object {
        const val TAG = "McpInterop"
    }

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    @Test
    fun loadsListsAndCallsToolsOnARealServer() {
        val urlArg = InstrumentationRegistry.getArguments().getString("mcpUrl")
        assumeTrue("未提供 -e mcpUrl，跳过真实 server 互通", !urlArg.isNullOrEmpty())
        val url = urlArg!!

        val source = McpToolSource("aos-agent", "0.1.0") { config ->
            StreamableHttpTransport(client, config.url, config.token)
        }

        // 先单独探一次握手，把真实原因打出来：McpSourceStatus 只带异常类型名，
        // 联调时看不到是 400 还是连不上。
        val probe = runCatching {
            runBlocking { McpClient(StreamableHttpTransport(client, url, null), "aos-agent", "0.1.0").initialize() }
        }
        probe.exceptionOrNull()?.let { Log.i(TAG, "initialize failed: ${it.message}") }
        assertTrue("握手失败：${probe.exceptionOrNull()?.message}", probe.isSuccess)

        val loaded = runBlocking {
            source.load(McpServerConfig(id = "fixture", url = url, permission = ToolPermission.Auto))
        }

        assertTrue("握手或 tools/list 失败：${loaded.status}", loaded.status.ok)
        assertEquals(
            setOf("mcp_fixture_get_time", "mcp_fixture_echo", "mcp_fixture_boom"),
            loaded.tools.map { it.name }.toSet(),
        )

        val echo = loaded.tools.first { it.name == "mcp_fixture_echo" }
        val echoResult = runBlocking { echo.execute(buildJsonObject { put("message", "车机在线") }) }
        assertTrue("echo 应成功：$echoResult", echoResult is ToolResult.Ok)
        val fields = (echoResult as ToolResult.Ok).fields
        assertEquals(
            "echo:车机在线",
            ((fields["content"] as kotlinx.serialization.json.JsonArray)[0] as JsonPrimitive).content,
        )
        assertNotNull("structuredContent 要摊平进来", fields["structured"])

        val boom = loaded.tools.first { it.name == "mcp_fixture_boom" }
        val boomResult = runBlocking { boom.execute(buildJsonObject { }) }
        assertTrue("isError 必须映射成 Error：$boomResult", boomResult is ToolResult.Error)
    }
}
