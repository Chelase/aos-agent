package com.aos.agent.ui.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 地址栏输入解析的纯逻辑测试。
 *
 * 判断"这是网址还是搜索词"是浏览器页最容易出错的地方：
 * 判错会让用户输入的搜索词被当成网址加载（或反过来），结果都是白屏。
 */
class BrowserInputTest {

    @Test
    fun explicitSchemesAreUsedAsIs() {
        assertEquals("https://example.com", BrowserInput.resolve("https://example.com"))
        assertEquals("http://example.com", BrowserInput.resolve("http://example.com"))
    }

    @Test
    fun hostnameLikeInputGetsHttpsPrefix() {
        assertEquals("https://example.com", BrowserInput.resolve("example.com"))
        assertEquals("https://www.example.com/path", BrowserInput.resolve("www.example.com/path"))
    }

    @Test
    fun naturalLanguageBecomesSearchQuery() {
        val resolved = BrowserInput.resolve("今天天气怎么样")!!
        assertTrue("应是搜索链接：$resolved", resolved.startsWith(BrowserInput.SEARCH_PREFIX))
        assertTrue("应编码查询词：$resolved", resolved.contains("%"))
    }

    @Test
    fun queryWithSpacesIsSearched() {
        val resolved = BrowserInput.resolve("how to cook rice")!!
        assertTrue(resolved.startsWith(BrowserInput.SEARCH_PREFIX))
    }

    @Test
    fun blankInputIsRejected() {
        assertEquals(null, BrowserInput.resolve(""))
        assertEquals(null, BrowserInput.resolve("   "))
    }

    @Test
    fun hostDetectionRejectsSentencesAndTrailingDots() {
        assertFalse(BrowserInput.looksLikeHost("今天天气怎么样"))
        assertFalse(BrowserInput.looksLikeHost("what is the weather today"))
        assertFalse(BrowserInput.looksLikeHost("example.com."))
        assertFalse(BrowserInput.looksLikeHost(".com"))
    }

    @Test
    fun hostDetectionAcceptsRealHosts() {
        assertTrue(BrowserInput.looksLikeHost("example.com"))
        assertTrue(BrowserInput.looksLikeHost("zh.wikipedia.org"))
        assertTrue(BrowserInput.looksLikeHost("developer.android.com"))
    }
}
