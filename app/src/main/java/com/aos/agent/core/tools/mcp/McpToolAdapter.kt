package com.aos.agent.core.tools.mcp

import com.aos.agent.core.tools.Tool
import com.aos.agent.core.tools.ToolPermission
import com.aos.agent.core.tools.ToolResult
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * 把一个远端 MCP 工具实现成本地 [Tool]。
 *
 * 引擎看到的仍然是 `Tool`：MCP 只存在于这一层，Skill 与 AOC 都不感知它。
 * [name] 是按 OpenAI function name 规则改写过的安全名（`mcp_<server>_<tool>`），
 * 真正调用时用 [remoteName]。
 */
class McpToolAdapter(
    private val client: McpClient,
    private val serverId: String,
    private val remoteName: String,
    override val name: String,
    private val remoteDescription: String,
    override val inputSchema: JsonObject,
    override val permission: ToolPermission,
) : Tool {
    override val version: Int = 1
    override val category: String = "mcp:$serverId"

    override val description: String = buildString {
        append("远端 MCP 工具（server=$serverId）。")
        if (remoteDescription.isNotBlank()) append(remoteDescription)
        append("结果可能是文本片段或结构化字段，缺失时如实说明。")
    }

    override suspend fun execute(args: JsonObject): ToolResult {
        val result = runCatching { client.callTool(remoteName, args) }
            .getOrElse { error -> return ToolResult.Error("mcp call ${remoteName} failed: ${error.javaClass.simpleName}") }

        val texts = result.content.mapNotNull { it.text }
        if (result.isError) {
            return ToolResult.Error(texts.joinToString(" ").ifBlank { "remote tool returned isError" })
        }

        val nonTextTypes = result.content.filter { it.type != "text" }.map { it.type }.distinct()
        return ToolResult.Ok(
            buildJsonObject {
                if (result.structuredContent != null) {
                    put("structured", result.structuredContent)
                }
                putJsonArray("content") { texts.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } }
                if (nonTextTypes.isNotEmpty()) {
                    putJsonArray("unsupported_content_types") { nonTextTypes.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } }
                }
            },
        )
    }
}
