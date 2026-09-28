package com.aos.agent.core.tools.mcp

import com.aos.agent.core.tools.ToolSystem
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class McpTransportFrameTest {

    @Test
    fun picksTheFirstJsonFrameOutOfAnSseBody() {
        val body = """
            event: message
            data: {"jsonrpc":"2.0","id":1,"result":{"tools":[]}}

            data: {"jsonrpc":"2.0","id":2,"result":{"later":true}}
        """.trimIndent()

        val frame = extractJsonRpcFrame(body)

        assertTrue(frame!!.contains("\"tools\""))
    }

    @Test
    fun skipsHeartbeatsAndNonJsonFrames() {
        val body = ": keep-alive\n\ndata: \ndata: not-json\ndata: [DONE]\ndata: {\"ok\":1}"

        assertEquals("""{"ok":1}""", extractJsonRpcFrame(body))
    }

    /** 本函数只管 SSE 分支；application/json 响应走整体解析，不经过这里。 */
    @Test
    fun bareJsonWithoutDataPrefixIsNotTreatedAsAFrame() {
        assertEquals(null, extractJsonRpcFrame("""{"a":1}"""))
    }

    @Test
    fun noFrameAtAllReturnsNull() {
        assertEquals(null, extractJsonRpcFrame(": ping\n\ndata: garbage"))
    }
}

/** 远端工具与本地权限闸的接线：MCP 不该绕过 fail-closed。 */
class McpToolSystemIntegrationTest {

    /** 只回一条固定 tools/call 结果的假传输。 */
    private class CallTransport(private val result: String) : McpTransport {
        override suspend fun request(method: String, params: kotlinx.serialization.json.JsonObject) =
            Json.parseToJsonElement("""{"result":$result}""").let { (it as JsonObject)["result"]!! }

        override suspend fun notify(method: String, params: kotlinx.serialization.json.JsonObject) = Unit
    }

    private fun remoteTool(permission: com.aos.agent.core.tools.ToolPermission) = McpToolAdapter(
        client = McpClient(
            CallTransport("""{"content":[{"type":"text","text":"hello"}],"isError":false}"""),
            clientName = "aos-agent",
            clientVersion = "0.1.0",
        ),
        serverId = "demo",
        remoteName = "say",
        name = "mcp_demo_say",
        remoteDescription = "说一句",
        inputSchema = buildJsonObject { },
        permission = permission,
    )

    @Test
    fun remoteAskToolIsRejectedWithoutConfirmer() = runTest {
        val system = ToolSystem()
        system.register(remoteTool(com.aos.agent.core.tools.ToolPermission.Ask))

        val result = system.execute("mcp_demo_say", buildJsonObject { })

        assertTrue("远端工具必须走同一个 fail-closed 闸：$result", result is com.aos.agent.core.tools.ToolResult.Rejected)
    }

    @Test
    fun remoteAutoToolRunsThroughTheSameTimeoutAndMapping() = runTest {
        val system = ToolSystem()
        system.register(remoteTool(com.aos.agent.core.tools.ToolPermission.Auto))

        val result = system.execute("mcp_demo_say", buildJsonObject { }) as com.aos.agent.core.tools.ToolResult.Ok

        assertEquals("hello", ((result.fields["content"] as kotlinx.serialization.json.JsonArray)[0]).toString().trim('"'))
    }
}
