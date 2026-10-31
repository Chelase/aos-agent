package com.aos.agent.core.tools

import com.aos.agent.core.web.SearchProvider
import com.aos.agent.core.web.SearchResult
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * web_search 工具测试。
 *
 * 重点守住两件事：失败要如实报（不能返回空列表冒充"没搜到"），
 * 参数边界要夹住（limit 越界不能让模型拿到成千上万条）。
 */
class WebSearchToolTest {

    private class FakeProvider(
        private val outcome: SearchProvider.SearchOutcome,
    ) : SearchProvider {
        override val name: String = "fake"
        var lastQuery: String? = null
        var lastLimit: Int? = null

        override suspend fun search(query: String, limit: Int): SearchProvider.SearchOutcome {
            lastQuery = query
            lastLimit = limit
            return outcome
        }
    }

    private fun args(query: String, limit: Int? = null) = buildJsonObject {
        put("query", query)
        limit?.let { put("limit", it) }
    }

    @Test
    fun returnsStructuredResultsOnSuccess() = runTest {
        val provider = FakeProvider(
            SearchProvider.SearchOutcome.Ok(
                listOf(SearchResult("标题", "https://example.com", "摘要")),
                "fake",
            ),
        )
        val result = WebSearchTool(provider).execute(args("测试"))
        assertTrue(result is ToolResult.Ok)
        val fields = (result as ToolResult.Ok).fields
        assertEquals("ok", fields["status"]?.jsonPrimitive?.content)
        assertEquals("测试", fields["query"]?.jsonPrimitive?.content)
        assertEquals(1, fields["total"]?.jsonPrimitive?.content?.toInt())
        val first = fields["results"]!!.jsonArray[0].jsonObject
        assertEquals("标题", first["title"]?.jsonPrimitive?.content)
        assertEquals("https://example.com", first["url"]?.jsonPrimitive?.content)
    }

    @Test
    fun parseFailureIsReportedNotHidden() = runTest {
        val provider = FakeProvider(SearchProvider.SearchOutcome.ParseFailed("结构变了"))
        val result = WebSearchTool(provider).execute(args("测试"))
        assertTrue(result is ToolResult.Ok)
        val fields = (result as ToolResult.Ok).fields
        // 关键：状态是 parse_failed，不是"ok 但结果为空"
        assertEquals("parse_failed", fields["status"]?.jsonPrimitive?.content)
        assertTrue(fields["detail"]?.jsonPrimitive?.content?.contains("结构变了") == true)
        assertEquals(0, fields["results"]!!.jsonArray.size)
    }

    @Test
    fun networkErrorIsReported() = runTest {
        val provider = FakeProvider(SearchProvider.SearchOutcome.NetworkError("连不上"))
        val result = WebSearchTool(provider).execute(args("测试"))
        val fields = (result as ToolResult.Ok).fields
        assertEquals("network_error", fields["status"]?.jsonPrimitive?.content)
    }

    @Test
    fun emptyQueryIsRejectedWithoutCallingProvider() = runTest {
        val provider = FakeProvider(SearchProvider.SearchOutcome.Ok(emptyList(), "fake"))
        val result = WebSearchTool(provider).execute(args("   "))
        assertTrue(result is ToolResult.Error)
        assertNull(provider.lastQuery)
    }

    @Test
    fun missingQueryIsRejected() = runTest {
        val provider = FakeProvider(SearchProvider.SearchOutcome.Ok(emptyList(), "fake"))
        val result = WebSearchTool(provider).execute(buildJsonObject { })
        assertTrue(result is ToolResult.Error)
    }

    @Test
    fun limitIsClampedToBounds() = runTest {
        val provider = FakeProvider(SearchProvider.SearchOutcome.Ok(emptyList(), "fake"))
        val tool = WebSearchTool(provider)

        tool.execute(args("q", 999))
        assertEquals("超上限应夹到 20", 20, provider.lastLimit)

        tool.execute(args("q", 0))
        assertEquals("零应夹到 1", 1, provider.lastLimit)

        tool.execute(args("q", null))
        assertEquals("默认应为 8", 8, provider.lastLimit)
    }
}
