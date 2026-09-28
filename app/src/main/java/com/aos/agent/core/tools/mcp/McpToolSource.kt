package com.aos.agent.core.tools.mcp

import com.aos.agent.core.tools.Tool
import kotlinx.coroutines.CancellationException

/** 一个来源的加载结果，供日志与后续可观测读取——失败不静默消失。 */
data class McpSourceStatus(
    val serverId: String,
    val ok: Boolean,
    val toolCount: Int,
    val detail: String? = null,
)

/**
 * 把一个 MCP server 变成一批可注册的 [Tool]。
 *
 * 核心要求来自父计划 Step 6：**坏 server 只能让它自己消失**——连接失败、握手不兼容、
 * `tools/list` 报错，都只让这一路贡献 0 个工具并留下状态，本地工具与引擎循环照常。
 */
class McpToolSource(
    private val clientName: String,
    private val clientVersion: String,
    private val transportFor: (McpServerConfig) -> McpTransport,
) {
    data class Loaded(val status: McpSourceStatus, val tools: List<Tool>)

    suspend fun load(config: McpServerConfig): Loaded = try {
        val client = McpClient(transportFor(config), clientName, clientVersion)
        client.initialize()
        val specs = client.listTools()
        val used = mutableSetOf<String>()
        Loaded(
            status = McpSourceStatus(config.id, ok = true, toolCount = specs.size),
            tools = specs.map { spec ->
                McpToolAdapter(
                    client = client,
                    serverId = config.id,
                    remoteName = spec.name,
                    name = uniqueName(config.id, spec.name, used),
                    remoteDescription = spec.description,
                    inputSchema = spec.inputSchema,
                    permission = config.permission,
                )
            },
        )
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Throwable) {
        Loaded(
            McpSourceStatus(config.id, ok = false, toolCount = 0, detail = error.javaClass.simpleName),
            emptyList(),
        )
    }

    companion object {
        /** OpenAI function name 只接受 `[A-Za-z0-9_-]`，远端名字要先改写并限长。 */
        const val MAX_NAME_LENGTH = 64

        fun qualifiedName(serverId: String, remoteName: String): String =
            "mcp_${sanitize(serverId)}_${sanitize(remoteName)}"
                .take(MAX_NAME_LENGTH)
                .trimEnd('_', '-')

        private fun uniqueName(serverId: String, remoteName: String, used: MutableSet<String>): String {
            val base = qualifiedName(serverId, remoteName)
            var candidate = base
            var suffix = 2
            while (!used.add(candidate)) {
                candidate = "${base}_${suffix++}".take(MAX_NAME_LENGTH)
            }
            return candidate
        }

        private fun sanitize(value: String): String =
            value.map { if (it.isLetterOrDigit() || it == '_' || it == '-') it else '_' }.joinToString("")
    }
}
