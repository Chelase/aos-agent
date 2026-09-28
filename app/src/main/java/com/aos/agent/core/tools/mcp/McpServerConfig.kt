package com.aos.agent.core.tools.mcp

import com.aos.agent.core.tools.ToolPermission
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/**
 * 一个远端 MCP server 的配置。
 *
 * [token] 与 LLM apiKey 同级：只进应用私有 DataStore，日志与 `toString()` 一律掩码。
 * [permission] 决定该 server 的工具是否需要确认，默认 `Ask`（无确认器即拒）。
 */
data class McpServerConfig(
    val id: String,
    val url: String,
    val token: String? = null,
    val permission: ToolPermission = ToolPermission.Ask,
) {
    fun maskedToken(): String = if (token.isNullOrBlank()) "none" else "set(len=${token.length})"

    override fun toString(): String =
        "McpServerConfig(id=$id, url=$url, token=${maskedToken()}, permission=$permission)"
}

interface McpServerConfigSource {
    suspend fun servers(): List<McpServerConfig>
}

/** 配置列表在 DataStore 里的 JSON 形态，单独放一处便于测试。 */
object McpServerConfigCodec {
    fun encode(configs: List<McpServerConfig>): String = buildJsonArray {
        configs.forEach { config ->
            add(
                buildJsonObject {
                    put("id", config.id)
                    put("url", config.url)
                    config.token?.let { put("token", it) }
                    put("permission", config.permission.name.lowercase())
                },
            )
        }
    }.toString()

    fun decode(raw: String?): List<McpServerConfig> {
        if (raw.isNullOrBlank()) return emptyList()
        val array = runCatching { kotlinx.serialization.json.Json.parseToJsonElement(raw) }
            .getOrNull() as? JsonArray ?: return emptyList()
        return array.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val id = (obj["id"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
            val url = (obj["url"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
            McpServerConfig(
                id = id,
                url = url,
                token = (obj["token"] as? JsonPrimitive)?.contentOrNull,
                permission = when ((obj["permission"] as? JsonPrimitive)?.contentOrNull) {
                    "auto" -> ToolPermission.Auto
                    "forbid" -> ToolPermission.Forbid
                    else -> ToolPermission.Ask
                },
            )
        }
    }
}
