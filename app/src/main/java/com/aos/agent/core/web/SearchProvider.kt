package com.aos.agent.core.web

import java.net.URLDecoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 搜索引擎适配：关键词 → 结果列表。
 *
 * 当前实现 DuckDuckGo 的 html 端点。**该端点是给浏览器用的，不是官方 API**，
 * 结构随时可能变——所以解析失败必须报"结构变化"，不能返回空列表冒充"没找到"。
 */
interface SearchProvider {
    val name: String

    suspend fun search(query: String, limit: Int): SearchOutcome

    sealed interface SearchOutcome {
        data class Ok(val results: List<SearchResult>, val provider: String) : SearchOutcome

        /** 端点通了但解析不出条目：多半是页面结构变了。 */
        data class ParseFailed(val reason: String) : SearchOutcome

        /** 网络层失败。 */
        data class NetworkError(val reason: String) : SearchOutcome
    }
}

data class SearchResult(
    val title: String,
    val url: String,
    val snippet: String,
)

/**
 * DuckDuckGo 搜索。
 *
 * 两个端点：主用 `html.duckduckgo.com/html/`，条目不够时降级 `lite.duckduckgo.com/lite/`。
 * 两个端点都要求带桌面 UA，否则会被当成异常流量。
 */
class DuckDuckGoSearchProvider(
    private val client: OkHttpClient = OkHttpClient(),
) : SearchProvider {

    override val name: String = "duckduckgo"

    override suspend fun search(query: String, limit: Int): SearchProvider.SearchOutcome {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return SearchProvider.SearchOutcome.NetworkError("搜索词为空")

        when (val html = fetch(HTML_ENDPOINT, trimmed)) {
            is FetchResult.Failure -> {
                // 主端点就失败了：先试 lite，都不行才算网络错误
                val lite = fetch(LITE_ENDPOINT, trimmed)
                if (lite is FetchResult.Success) {
                    val liteResults = parseLiteResults(lite.body)
                    if (liteResults.isNotEmpty()) {
                        return SearchProvider.SearchOutcome.Ok(liteResults.take(limit), "$name-lite")
                    }
                }
                val reason = if (lite is FetchResult.Failure) {
                    "主端点 ${html.reason}；lite 端点 ${lite.reason}"
                } else {
                    "主端点 ${html.reason}"
                }
                return SearchProvider.SearchOutcome.NetworkError(reason)
            }

            is FetchResult.Success -> {
                val results = parseHtmlResults(html.body)
                if (results.isNotEmpty()) {
                    return SearchProvider.SearchOutcome.Ok(results.take(limit), name)
                }

                // 主端点通了但没解析出条目：可能结构变了，也可能是被限流。降级 lite 再试一次，
                // 两次都空才报解析失败——避免把"拦截"误报成"结构变了"。
                val lite = fetch(LITE_ENDPOINT, trimmed)
                if (lite is FetchResult.Success) {
                    val liteResults = parseLiteResults(lite.body)
                    if (liteResults.isNotEmpty()) {
                        return SearchProvider.SearchOutcome.Ok(liteResults.take(limit), "$name-lite")
                    }
                }
                return SearchProvider.SearchOutcome.ParseFailed(
                    "DuckDuckGo 返回的页面结构已变化，无法解析搜索结果（可能是被限流或改版）",
                )
            }
        }
    }

    private fun fetch(endpoint: String, query: String): FetchResult {
        val request = Request.Builder()
            .url("$endpoint?q=${java.net.URLEncoder.encode(query, "UTF-8")}")
            .header("User-Agent", DESKTOP_UA)
            .header("Accept", "text/html,application/xhtml+xml")
            .build()
        return runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@use FetchResult.Failure("HTTP ${response.code}")
                }
                val body = response.body?.string()
                if (body.isNullOrEmpty()) {
                    FetchResult.Failure("响应体为空")
                } else {
                    FetchResult.Success(body)
                }
            }
        }.getOrElse { error ->
            // 保留异常类型与原因，排查时能看出是超时、DNS 还是连接被拒
            FetchResult.Failure("${error.javaClass.simpleName}: ${error.message}")
        }
    }

    private sealed interface FetchResult {
        data class Success(val body: String) : FetchResult
        data class Failure(val reason: String) : FetchResult
    }

    /** 主端点解析：`result__a` 是标题链接，`result__snippet` 是摘要。 */
    internal fun parseHtmlResults(html: String): List<SearchResult> {
        val anchorPattern = Regex(
            "<a[^>]*class=\"result__a\"[^>]*href=\"([^\"]+)\"[^>]*>(.*?)</a>",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        )
        val snippetPattern = Regex(
            "<a[^>]*class=\"result__snippet\"[^>]*>(.*?)</a>",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        )
        val snippets = snippetPattern.findAll(html).map { match ->
            decodeEntities(stripTags(match.groupValues[1])).trim()
        }.toList()

        return anchorPattern.findAll(html).mapIndexed { index, match ->
            SearchResult(
                title = decodeEntities(stripTags(match.groupValues[2])).trim(),
                url = unwrapRedirect(match.groupValues[1]),
                snippet = snippets.getOrNull(index).orEmpty(),
            )
        }.filter { it.url.isNotEmpty() && it.title.isNotEmpty() }.toList()
    }

    /** lite 端点解析：结构与主端点不同，链接直接在 `<a>` 上。 */
    internal fun parseLiteResults(html: String): List<SearchResult> {
        val linkPattern = Regex(
            "<a[^>]*rel=\"nofollow\"[^>]*href=\"([^\"]+)\"[^>]*>(.*?)</a>",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        )
        val snippetPattern = Regex(
            "<td[^>]*class=\"result-snippet\"[^>]*>(.*?)</td>",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        )
        val snippets = snippetPattern.findAll(html).map { match ->
            decodeEntities(stripTags(match.groupValues[1])).trim()
        }.toList()

        return linkPattern.findAll(html).mapIndexed { index, match ->
            val href = decodeEntities(match.groupValues[1]).trim()
            SearchResult(
                title = decodeEntities(stripTags(match.groupValues[2])).trim(),
                url = unwrapRedirect(href),
                snippet = snippets.getOrNull(index).orEmpty(),
            )
        }.filter { it.url.startsWith("http") && it.title.isNotEmpty() }.toList()
    }

    /**
     * 解开 DDG 的跳转链接：`//duckduckgo.com/l/?uddg=<urlencoded>&rut=...`
     * 直接用会拿到跳转页而不是目标站。
     */
    internal fun unwrapRedirect(href: String): String {
        val raw = decodeEntities(href.trim())
        if (raw.startsWith("http") && !raw.contains("duckduckgo.com/l/")) return raw
        val marker = "uddg="
        val index = raw.indexOf(marker)
        if (index < 0) return if (raw.startsWith("http")) raw else ""
        val tail = raw.substring(index + marker.length)
        val end = tail.indexOf('&')
        val encoded = if (end >= 0) tail.substring(0, end) else tail
        return runCatching { URLDecoder.decode(encoded, "UTF-8") }.getOrDefault("")
    }

    private fun stripTags(html: String): String = html.replace(Regex("<[^>]*>"), "")

    private fun decodeEntities(text: String): String = text
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#x27;", "'")
        .replace("&#39;", "'")
        .replace("&nbsp;", " ")

    private companion object {
        const val HTML_ENDPOINT = "https://html.duckduckgo.com/html/"
        const val LITE_ENDPOINT = "https://lite.duckduckgo.com/lite/"
        const val DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    }
}

/** 把结果转成工具输出结构。 */
internal fun searchResultsToJson(
    query: String,
    provider: String,
    results: List<SearchResult>,
): JsonObject = buildJsonObject {
    put("status", "ok")
    put("query", query)
    put("provider", provider)
    put("total", results.size)
    putJsonArray("results") {
        results.forEach { result ->
            add(
                buildJsonObject {
                    put("title", result.title)
                    put("url", result.url)
                    put("snippet", result.snippet)
                },
            )
        }
    }
}
