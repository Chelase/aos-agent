package com.aos.agent.core.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 用**真实抓取的** DuckDuckGo html 片段做回归。
 *
 * fixture 是 2026-10-31 真实抓取的页面片段（`app/src/test/resources/ddg_result_sample.html`），
 * 刻意保留原始的 class 名与 DDG 跳转链接格式——这样端点改版时，
 * 这个测试会先失败，而不是等到线上搜不出东西才发现。
 */
class DuckDuckGoRealFixtureTest {

    private val provider = DuckDuckGoSearchProvider()

    private fun fixture(): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream("ddg_result_sample.html")) {
            "缺少 fixture：ddg_result_sample.html"
        }.bufferedReader().readText()

    @Test
    fun parsesRealPageFragment() {
        val html = fixture()
        val results = provider.parseHtmlResults(html)
        assertTrue("真实片段应能解析出结果", results.isNotEmpty())
    }

    @Test
    fun realFragmentYieldsDirectTargetUrls() {
        val results = provider.parseHtmlResults(fixture())
        results.forEach { result ->
            assertTrue(
                "解析出的 URL 应是目标站直链而非 DDG 跳转页：${result.url}",
                result.url.startsWith("http"),
            )
            assertTrue(
                "不应残留 duckduckgo 跳转：${result.url}",
                !result.url.contains("duckduckgo.com/l/"),
            )
        }
    }

    @Test
    fun realFragmentTitlesAreNonEmptyAndDecoded() {
        val results = provider.parseHtmlResults(fixture())
        results.forEach { result ->
            assertTrue("标题不应为空", result.title.isNotBlank())
            assertTrue("标题不应残留实体：${result.title}", !result.title.contains("&amp;"))
        }
    }
}
