package com.aos.agent.core.tools.mcp

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

data class McpToolSpec(
    val name: String,
    val description: String,
    val inputSchema: JsonObject,
)

data class McpContent(
    val type: String,
    val text: String?,
)

data class McpCallResult(
    val content: List<McpContent>,
    val isError: Boolean,
    val structuredContent: JsonObject?,
)

data class McpServerInfo(
    val name: String,
    val version: String,
    val protocolVersion: String,
)

/**
 * MCP 的 client 侧，只实现本期需要的三个方法。
 *
 * 不引官方 JVM SDK：它要带进一整套传输栈，与"轻量 + 任意车机"冲突，
 * 而我们只用 `initialize` / `tools/list` / `tools/call`。
 */
class McpClient(
    private val transport: McpTransport,
    private val clientName: String,
    private val clientVersion: String,
) {
    suspend fun initialize(): McpServerInfo {
        val result = transport.request(
            "initialize",
            buildJsonObject {
                put("protocolVersion", MCP_PROTOCOL_VERSION)
                put("capabilities", buildJsonObject { })
                put(
                    "clientInfo",
                    buildJsonObject {
                        put("name", clientName)
                        put("version", clientVersion)
                    },
                )
            },
        ).asObjectOrThrow()

        transport.notify("notifications/initialized", buildJsonObject { })

        return McpServerInfo(
            name = result.objectOf("serverInfo")?.string("name").orEmpty(),
            version = result.objectOf("serverInfo")?.string("version").orEmpty(),
            protocolVersion = result.string("protocolVersion").orEmpty(),
        )
    }

    suspend fun listTools(): List<McpToolSpec> {
        val result = transport.request("tools/list", buildJsonObject { }).asObjectOrThrow()
        val tools = result["tools"] as? JsonArray ?: return emptyList()
        return tools.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val name = obj.string("name") ?: return@mapNotNull null
            McpToolSpec(
                name = name,
                description = obj.string("description").orEmpty(),
                inputSchema = obj.objectOf("inputSchema") ?: buildJsonObject { put("type", "object") },
            )
        }
    }

    suspend fun callTool(name: String, arguments: JsonObject): McpCallResult {
        val result = transport.request(
            "tools/call",
            buildJsonObject {
                put("name", name)
                put("arguments", arguments)
            },
        ).asObjectOrThrow()

        val content = (result["content"] as? JsonArray)?.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            McpContent(type = obj.string("type") ?: "unknown", text = obj.string("text"))
        }.orEmpty()

        return McpCallResult(
            content = content,
            isError = (result["isError"] as? JsonPrimitive)?.booleanOrNull == true,
            structuredContent = result.objectOf("structuredContent"),
        )
    }

    private fun JsonElement.asObjectOrThrow(): JsonObject =
        this as? JsonObject ?: throw McpException("result is not a JSON object")

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull

    private fun JsonObject.objectOf(key: String): JsonObject? = this[key] as? JsonObject
}
