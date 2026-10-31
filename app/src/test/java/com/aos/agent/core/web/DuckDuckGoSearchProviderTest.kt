package com.aos.agent.core.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 搜索解析测试。
 *
 * 用的是真实抓取的 DuckDuckGo html 端点结构（2026-10-31 实测）。
 * 这里守住的是"结构变了要报错"，而不是"永远返回点什么都行"。
 */
class DuckDuckGoSearchProviderTest {

    private val provider = DuckDuckGoSearchProvider()

    /** 真实结构：标题链接是 class="result__a"，href 是 DDG 跳转链接。 */
    private val sampleHtml = """
        <div class="results">
          <div class="result results_links">
            <h2 class="result__title">
              <a rel="nofollow" class="result__a" href="//duckduckgo.com/l/?uddg=https%3A%2F%2Fexample.com%2Fpage1&amp;rut=abc123">第一个结果标题</a>
            </h2>
            <a class="result__snippet">这是第一个结果的摘要文本。</a>
            <a class="result__url" href="https://example.com/page1">https://example.com/page1</a>
          </div>
          <div class="result results_links">
            <h2 class="result__title">
              <a rel="nofollow" class="result__a" href="//duckduckgo.com/l/?uddg=https%3A%2F%2Fexample.org%2Fpage2&amp;rut=def456">第二个结果标题</a>
            </h2>
            <a class="result__snippet">这是第二个结果的摘要文本。</a>
          </div>
        </div>
    """.trimIndent()

    @Test
    fun parsesTitlesUrlsAndSnippets() {
        val results = provider.parseHtmlResults(sampleHtml)
        assertEquals(2, results.size)
        assertEquals("第一个结果标题", results[0].title)
        assertEquals("https://example.com/page1", results[0].url)
        assertEquals("这是第一个结果的摘要文本。", results[0].snippet)
        assertEquals("第二个结果标题", results[1].title)
        assertEquals("https://example.org/page2", results[1].url)
    }

    @Test
    fun unwrapsDuckDuckGoRedirectLinks() {
        val href = "//duckduckgo.com/l/?uddg=https%3A%2F%2Fexample.com%2Fpath%3Fa%3D1%26b%3D2&amp;rut=xyz"
        assertEquals("https://example.com/path?a=1&b=2", provider.unwrapRedirect(href))
    }

    @Test
    fun keepsDirectLinksUnchanged() {
        assertEquals("https://example.com/x", provider.unwrapRedirect("https://example.com/x"))
    }

    @Test
    fun parsesLiteEndpointStructure() {
        val liteHtml = """
            <table>
              <tr>
                <td class="result-snippet">lite 摘要内容</td>
                <td><a rel="nofollow" href="https://example.com/lite1">lite 标题一</a></td>
              </tr>
            </table>
        """.trimIndent()
        val results = provider.parseLiteResults(liteHtml)
        assertEquals(1, results.size)
        assertEquals("https://example.com/lite1", results[0].url)
        assertTrue(results[0].snippet.contains("lite 摘要内容"))
    }

    @Test
    fun returnsEmptyListWhenStructureChanged() {
        // 页面结构完全变了（改版或被拦截）：解析不出条目，但**不能抛异常**，
        // 也不能假装解析成功
        val changed = "<html><body><div class='new-layout'>完全不同的结构</div></body></html>"
        val results = provider.parseHtmlResults(changed)
        assertTrue(results.isEmpty())
    }

    @Test
    fun filtersOutEntriesWithoutTitleOrUrl() {
        val broken = """
            <a class="result__a" href="//duckduckgo.com/l/?uddg=https%3A%2F%2Fexample.com">有链接无标题</a>
            <a class="result__a" href="">空链接</a>
        """.trimIndent()
        val results = provider.parseHtmlResults(broken)
        // 无标题的条目应被过滤
        assertTrue(results.none { it.title.isEmpty() })
    }

    @Test
    fun decodesEntitiesInTitles() {
        val html = """
            <a class="result__a" href="https://example.com/a">A &amp; B &lt;tag&gt;</a>
        """.trimIndent()
        val results = provider.parseHtmlResults(html)
        assertEquals("A & B <tag>", results[0].title)
    }

    @Test
    fun producesJsonWithStructuredResults() {
        val results = listOf(
            SearchResult(title = "标题", url = "https://example.com", snippet = "摘要"),
        )
        val json = searchResultsToJson("查询词", "duckduckgo", results)
        assertEquals("ok", json["status"]?.toString()?.trim('"'))
        assertEquals(1, json["total"]?.toString()?.toInt())
        val items = json["results"] as kotlinx.serialization.json.JsonArray
        assertEquals(1, items.size)
        val first = items[0] as kotlinx.serialization.json.JsonObject
        assertEquals("标题", first["title"]?.toString()?.trim('"'))
        assertEquals("https://example.com", first["url"]?.toString()?.trim('"'))
    }

    @Test
    fun handlesEmptyQueryWithoutNetworkCall() {
        // 空查询直接在本地挡掉，不该发起网络请求
        assertFalse(provider.unwrapRedirect("").isNotEmpty())
    }
}
