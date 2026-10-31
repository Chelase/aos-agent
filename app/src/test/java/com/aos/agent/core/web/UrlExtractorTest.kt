package com.aos.agent.core.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 聊天里的链接抽取。
 *
 * 判错的后果：漏抽则用户点不开；误抽则把车机引向内网或明文页面。
 */
class UrlExtractorTest {

    @Test
    fun extractsPlainHttpsLink() {
        val links = UrlExtractor.extract("详见 https://example.com 这个页面")
        assertEquals(1, links.size)
        assertEquals("https://example.com", links[0].url)
    }

    @Test
    fun extractsMultipleLinks() {
        val links = UrlExtractor.extract("甲 https://a.com 与 乙 https://b.org/x")
        assertEquals(2, links.size)
        assertEquals("https://a.com", links[0].url)
        assertEquals("https://b.org/x", links[1].url)
    }

    @Test
    fun excludesChinesePunctuationFromUrl() {
        val links = UrlExtractor.extract("参考 https://zh.wikipedia.org/wiki/车机。")
        assertEquals(1, links.size)
        // 末尾的中文句号不属于 URL
        assertTrue(links[0].url.endsWith("/车机"))
        assertTrue(!links[0].url.contains("。"))
    }

    @Test
    fun ignoresPlainHttpLinks() {
        // 明文 http 不作为可点入口——用户一点就离开了安全边界
        assertTrue(UrlExtractor.extract("http://example.com").isEmpty())
    }

    @Test
    fun rejectsInternalAddresses() {
        assertTrue(UrlExtractor.extract("https://127.0.0.1/x").isEmpty())
        assertTrue(UrlExtractor.extract("https://192.168.1.1/").isEmpty())
        assertTrue(UrlExtractor.extract("https://169.254.169.254/").isEmpty())
    }

    @Test
    fun rejectsLocalhostAndInternalDomains() {
        assertTrue(UrlExtractor.extract("https://localhost/x").isEmpty())
        assertTrue(UrlExtractor.extract("https://printer.local/x").isEmpty())
    }

    @Test
    fun extractsLinkWithPathAndQuery() {
        val links = UrlExtractor.extract("看 https://example.com/a/b?q=1&r=2 吧")
        assertEquals(1, links.size)
        assertTrue(links[0].url.startsWith("https://example.com/a/b"))
    }

    @Test
    fun reportsPositionsSoCallerCanSplitText() {
        val text = "看 https://example.com 结束"
        val link = UrlExtractor.extract(text).single()
        assertEquals("看 ", text.substring(0, link.start))
        assertEquals(" 结束", text.substring(link.end))
    }

    @Test
    fun handlesNoLinks() {
        assertTrue(UrlExtractor.extract("没有链接的普通回答").isEmpty())
        assertTrue(UrlExtractor.extract("").isEmpty())
    }
}
