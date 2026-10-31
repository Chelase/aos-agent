package com.aos.agent.core.tools

import com.aos.agent.core.web.DuckDuckGoSearchProvider
import com.aos.agent.core.web.SearchProvider
import com.aos.agent.core.web.searchResultsToJson
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * 联网搜索工具：关键词 → 结果列表。
 *
 * 只读公开信息，不登录、不写回，所以 `permission = Auto`。
 *
 * 结果里只有标题/摘要/链接，**没有正文**——正文要另一个工具按 URL 取。
 * 搜索源用的是给浏览器用的 html 端点（不是官方 API），结构会变；
 * 解析失败时如实报"结构变化"，不能返回空列表冒充"没搜到"。
 */
class WebSearchTool(
    private val provider: SearchProvider = DuckDuckGoSearchProvider(),
) : Tool {
    override val name: String = "web_search"
    override val version: Int = 1
    override val category: String = "web"
    override val permission: ToolPermission = ToolPermission.Auto

    override val description: String =
        "联网搜索公开信息：按关键词返回若干条结果，每条含标题、链接、摘要。" +
            "查询时效性信息（最新版本、今天新闻、当前价格等）时先用它，不要凭记忆回答。" +
            "返回的是摘要不是正文；要读具体页面正文需用 web_fetch。"

    override val inputSchema: JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            put("query", buildJsonObject {
                put("type", "string")
                put("description", "搜索关键词，用自然语言或关键词组合")
            })
            put("limit", buildJsonObject {
                put("type", "integer")
                put("description", "返回条数，默认 8，上限 20")
            })
        })
        putJsonArray("required") { add("query") }
    }

    override suspend fun execute(args: JsonObject): ToolResult {
        val query = args.string("query")?.trim().orEmpty()
        if (query.isEmpty()) return ToolResult.Error("缺少 query 参数")
        val limit = (args.int("limit") ?: DEFAULT_LIMIT).coerceIn(1, MAX_LIMIT)

        return when (val outcome = provider.search(query, limit)) {
            is SearchProvider.SearchOutcome.Ok -> ToolResult.Ok(
                searchResultsToJson(query, outcome.provider, outcome.results),
            )

            is SearchProvider.SearchOutcome.ParseFailed -> ToolResult.Ok(
                buildJsonObject {
                    put("status", "parse_failed")
                    put("query", query)
                    put("detail", outcome.reason)
                    put("results", kotlinx.serialization.json.JsonArray(emptyList()))
                    put("total", 0)
                },
            )

            is SearchProvider.SearchOutcome.NetworkError -> ToolResult.Ok(
                buildJsonObject {
                    put("status", "network_error")
                    put("query", query)
                    put("detail", outcome.reason)
                    put("results", kotlinx.serialization.json.JsonArray(emptyList()))
                    put("total", 0)
                },
            )
        }
    }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it !is kotlinx.serialization.json.JsonNull }?.contentOrNull

    private fun JsonObject.int(key: String): Int? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.toIntOrNull()

    private companion object {
        const val DEFAULT_LIMIT = 8
        const val MAX_LIMIT = 20
    }
}
