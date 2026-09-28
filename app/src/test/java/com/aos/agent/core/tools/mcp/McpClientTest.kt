package com.aos.agent.core.tools.mcp

import com.aos.agent.core.tools.ToolPermission
import com.aos.agent.core.tools.ToolResult
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/** 假传输：按方法名返回官方形态的报文，同时记录发出去过什么。 */
private class FakeTransport(
    private val responses: MutableMap<String, JsonElement> = mutableMapOf(),
    private val failure: String? = null,
) : McpTransport {
    val requests = mutableListOf<Pair<String, JsonObject>>()
    val notifications = mutableListOf<String>()

    override suspend fun request(method: String, params: JsonObject): JsonElement {
        requests += method to params
        failure?.let { throw IOException(it) }
        return responses[method] ?: throw IOException("no fixture for $method")
    }

    override suspend fun notify(method: String, params: JsonObject) {
        notifications += method
    }
}

private val JSON = Json { ignoreUnknownKeys = true }

private fun fixture(raw: String): JsonElement = JSON.parseToJsonElement(raw)

private val initializeResult = """
{"protocolVersion":"2025-06-18","capabilities":{"tools":{"listChanged":true}},
 "serverInfo":{"name":"demo-server","version":"1.2.3"}}
"""

private val toolsListResult = """
{"tools":[
  {"name":"get.time","description":"当前时间","inputSchema":{"type":"object","properties":{}}},
  {"name":"weather_now","description":"天气","inputSchema":{"type":"object"}},
  {"name":"get_time","inputSchema":{"type":"object"}}
]}
"""

class McpClientTest {

    @Test
    fun initializeSendsProtocolHandshakeThenInitializedNotification() = runTest {
        val transport = FakeTransport(
            mutableMapOf("initialize" to fixture(initializeResult), "tools/list" to fixture(toolsListResult)),
        )

        val info = McpClient(transport, "aos-agent", "0.1.0").initialize()

        val params = transport.requests.single { it.first == "initialize" }.second
        assertEquals("2025-06-18", (params["protocolVersion"] as JsonPrimitive).content)
        assertEquals("aos-agent", ((params["clientInfo"] as JsonObject)["name"] as JsonPrimitive).content)
        assertNotNull(params["capabilities"])
        assertEquals(listOf("notifications/initialized"), transport.notifications)
        assertEquals("demo-server", info.name)
        assertEquals("1.2.3", info.version)
    }

    @Test
    fun listToolsKeepsOptionalFieldsAbsent() = runTest {
        val transport = FakeTransport(mutableMapOf("tools/list" to fixture(toolsListResult)))

        val tools = McpClient(transport, "aos-agent", "0.1.0").listTools()

        assertEquals(3, tools.size)
        assertEquals("get.time", tools[0].name)
        assertEquals("当前时间", tools[0].description)
        assertEquals("", tools[2].description)
        assertEquals("object", (tools[2].inputSchema["type"] as JsonPrimitive).content)
    }

    @Test
    fun callToolMapsContentAndErrorFlag() = runTest {
        val transport = FakeTransport(
            mutableMapOf(
                "tools/call" to fixture(
                    """{"content":[{"type":"text","text":"12:30"},{"type":"image","data":"..."}],
                        "structuredContent":{"hour":12},"isError":false}""",
                ),
            ),
        )

        val result = McpClient(transport, "a", "b").callTool("get.time", buildJsonObject { })

        assertEquals(listOf("text", "image"), result.content.map { it.type })
        assertEquals("12:30", result.content.first().text)
        assertEquals(12, ((result.structuredContent?.get("hour")) as JsonPrimitive).content.toInt())
        assertFalse(result.isError)
        val params = transport.requests.single().second
        assertEquals("get.time", (params["name"] as JsonPrimitive).content)
    }
}

class McpToolSourceTest {

    private fun source(failure: String? = null) = McpToolSource(
        clientName = "aos-agent",
        clientVersion = "0.1.0",
        transportFor = {
            FakeTransport(
                responses = mutableMapOf(
                    "initialize" to fixture(initializeResult),
                    "tools/list" to fixture(toolsListResult),
                    "tools/call" to fixture("""{"content":[{"type":"text","text":"ok"}],"isError":false}"""),
                ),
                failure = failure,
            )
        },
    )

    private val config = McpServerConfig(id = "demo", url = "http://10.0.2.2:9000/mcp")

    @Test
    fun remoteToolsBecomeLocallyNamedAdapters() = runTest {
        val loaded = source().load(config)

        assertTrue(loaded.status.ok)
        assertEquals(3, loaded.status.toolCount)
        assertEquals(
            listOf("mcp_demo_get_time", "mcp_demo_weather_now", "mcp_demo_get_time_2"),
            loaded.tools.map { it.name },
        )
        assertTrue("远端名不能合法化后就被丢掉", loaded.tools.all { it.category == "mcp:demo" })
    }

    /** 名字改写只为满足 function name 规则，调用必须打回原始远端名。 */
    @Test
    fun callUsesTheOriginalRemoteName() = runTest {
        val transport = FakeTransport(
            mutableMapOf(
                "tools/call" to fixture("""{"content":[{"type":"text","text":"12:30"}],"isError":false}"""),
            ),
        )
        val adapter = McpToolAdapter(
            client = McpClient(transport, "a", "b"),
            serverId = "demo",
            remoteName = "get.time",
            name = "mcp_demo_get_time",
            remoteDescription = "当前时间",
            inputSchema = buildJsonObject { put("type", "object") },
            permission = ToolPermission.Ask,
        )

        val result = adapter.execute(buildJsonObject { })

        assertTrue(result is ToolResult.Ok)
        assertEquals("get.time", (transport.requests.single().second["name"] as JsonPrimitive).content)
        assertEquals(
            "12:30",
            ((result as ToolResult.Ok).fields["content"]!!.let { (it as kotlinx.serialization.json.JsonArray)[0] } as JsonPrimitive).content,
        )
    }

    @Test
    fun brokenServerOnlyKillsItsOwnSource() = runTest {
        val loaded = source(failure = "connection refused").load(config)

        assertFalse(loaded.status.ok)
        assertEquals(0, loaded.status.toolCount)
        assertTrue("状态要能看出原因类型", loaded.status.detail?.contains("IOException") == true)
        assertTrue(loaded.tools.isEmpty())
    }

    @Test
    fun remoteErrorFlagBecomesToolError() = runTest {
        val transport = FakeTransport(
            mutableMapOf("tools/call" to fixture("""{"content":[{"type":"text","text":"boom"}],"isError":true}""")),
        )
        val adapter = McpToolAdapter(
            McpClient(transport, "a", "b"), "demo", "x", "mcp_demo_x", "", buildJsonObject { }, ToolPermission.Auto,
        )

        val result = adapter.execute(buildJsonObject { })

        assertTrue(result is ToolResult.Error)
        assertTrue((result as ToolResult.Error).cause.contains("boom"))
    }

    @Test
    fun qualifiedNameIsSanitizedAndBounded() {
        val long = "a".repeat(200)

        val name = McpToolSource.qualifiedName("my server", long)

        assertTrue(name.length <= 64)
        assertTrue(name.matches(Regex("^[A-Za-z0-9_-]+$")))
        assertTrue(name.startsWith("mcp_my_server_"))
    }
}

class McpServerConfigCodecTest {

    @Test
    fun roundTripsAndDefaultsToAsk() {
        val configs = listOf(
            McpServerConfig("demo", "http://10.0.2.2:9000/mcp", token = "tok-123456"),
            McpServerConfig("local", "http://localhost:8080", permission = ToolPermission.Auto),
        )

        val decoded = McpServerConfigCodec.decode(McpServerConfigCodec.encode(configs))

        assertEquals(configs, decoded)
    }

    @Test
    fun garbageAndNullDecodeToEmpty() {
        assertTrue(McpServerConfigCodec.decode(null).isEmpty())
        assertTrue(McpServerConfigCodec.decode("not json").isEmpty())
        assertTrue(McpServerConfigCodec.decode("""[{"url":"http://x"}]""").isEmpty())
    }

    /** 配置对象的字符串形态不能带出 token，否则日志会泄。 */
    @Test
    fun toStringMasksTheToken() {
        val config = McpServerConfig("demo", "http://x", token = "super-secret-token")

        val described = config.toString()

        assertFalse(described.contains("super-secret-token"))
        assertTrue(described.contains("set(len=18)"))
    }
}
