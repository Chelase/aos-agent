package com.aos.agent.core.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 浏览器脚本构造与结果解析。
 *
 * 这层的错误有两个方向，都很糟：
 * - **转义漏了** → 模型给的 selector 能跳出字符串字面量，在页面里执行任意 JS；
 * - **解析错了** → 把"没找到元素"报成"操作成功"，Agent 会以为点过了。
 */
class BrowserScriptsTest {

    // ------------------------------------------------------------- 字符串转义

    @Test
    fun escapesQuotesSoSelectorCannotEscapeTheStringLiteral() {
        // 期望字面量： "  \"  ; alert(1); //  "
        val escaped = BrowserScripts.jsString("\"; alert(1); //")
        assertEquals("\"\\\"; alert(1); //\"", escaped)
    }

    @Test
    fun escapesBackslashSoItCannotEscapeTheClosingQuote() {
        val escaped = BrowserScripts.jsString("\\")
        assertEquals("\"\\\\\"", escaped)
    }

    @Test
    fun escapesNewlinesAndTabs() {
        assertEquals("\"a\\nb\"", BrowserScripts.jsString("a\nb"))
        assertEquals("\"a\\tb\"", BrowserScripts.jsString("a\tb"))
    }

    @Test
    fun escapesLineAndParagraphSeparators() {
        // U+2028/U+2029 在 JS 字符串字面量里会截断字符串
        assertEquals("\"a\\u2028b\"", BrowserScripts.jsString("a\u2028b"))
        assertEquals("\"a\\u2029b\"", BrowserScripts.jsString("a\u2029b"))
    }

    @Test
    fun escapesControlCharacters() {
        assertEquals("\"a\\u0000b\"", BrowserScripts.jsString("a\u0000b"))
    }

    @Test
    fun keepsOrdinaryTextIntact() {
        assertEquals("\"button.submit\"", BrowserScripts.jsString("button.submit"))
        assertEquals("\"登录\"", BrowserScripts.jsString("登录"))
    }

    // ------------------------------------------------------------- 脚本构造

    @Test
    fun clickScriptEmbedsSelectorAsStringLiteral() {
        val script = BrowserScripts.click("button.submit", null)
        assertTrue(script.contains("\"button.submit\""))
    }

    @Test
    fun clickScriptEmbedsTextAsStringLiteral() {
        val script = BrowserScripts.click(null, "登录")
        assertTrue(script.contains("\"登录\""))
    }

    @Test
    fun clickScriptDoesNotEmbedRawSelectorWhenAbsent() {
        val script = BrowserScripts.click(null, "登录")
        // 缺 selector 时必须是 null，不能是空串——空选择器会让 querySelector 抛错
        assertTrue(script.contains("var sel = null"))
    }

    @Test
    fun fillScriptUsesNativeSetterSoFrameworksSeeTheChange() {
        val script = BrowserScripts.fill("#q", "hello", null)
        // 直接设 value 不会触发 React/Vue 的受控更新，必须走原型上的原生 setter
        assertTrue(script.contains("getOwnPropertyDescriptor"))
        assertTrue(script.contains("dispatchEvent"))
        assertTrue(script.contains("'input'"))
        assertTrue(script.contains("'change'"))
    }

    @Test
    fun fillScriptEmbedsValueSafely() {
        val script = BrowserScripts.fill("#q", "\"x\"", null)
        assertTrue(script.contains("\\\"x\\\""))
    }

    @Test
    fun scrollScriptSupportsBothSelectorAndPosition() {
        assertTrue(BrowserScripts.scroll("#footer", null).contains("scrollIntoView"))
        assertTrue(BrowserScripts.scroll(null, 500).contains("scrollTo"))
    }

    @Test
    fun waitScriptOnlyProbesWithoutBlocking() {
        val script = BrowserScripts.waitFor("#result")
        assertTrue(script.contains("querySelector"))
        // 不能有 await：注入脚本不能长时间阻塞 WebView 事件循环
        assertFalse(script.contains("await"))
    }

    @Test
    fun readScriptPrefersArticleThenMainThenBody() {
        val script = BrowserScripts.readPage()
        assertTrue(script.contains("querySelector('article')"))
        assertTrue(script.contains("querySelector('main')"))
        assertTrue(script.contains("document.body"))
    }

    @Test
    fun readScriptStripsScriptAndStyleNodes() {
        val script = BrowserScripts.readPage()
        // script 的 innerText 是执行残留，混进正文就是噪声
        assertTrue(script.contains("script,style"))
    }

    @Test
    fun readScriptRejectsWebViewInternalErrorPage() {
        // WebView 加载失败时显示的是内部错误页，它的正文是"网页无法打开"，
        // 当成正常正文返回会让 Agent 以为读到了页面
        val script = BrowserScripts.readPage()
        assertTrue(script.contains("chrome-error://"))
    }

    // ------------------------------------------------------------- 结果解析

    @Test
    fun parsesReadResult() {
        val raw = """{"status":"ok","url":"https://a.com/x","title":"标题","content":"正文内容"}"""
        val result = BrowserScripts.parseRead(raw)
        assertTrue(result is BrowserScripts.ReadResult.Ok)
        result as BrowserScripts.ReadResult.Ok
        assertEquals("https://a.com/x", result.url)
        assertEquals("标题", result.title)
        assertEquals("正文内容", result.content)
        assertFalse(result.truncated)
    }

    @Test
    fun truncatesOversizedContent() {
        val big = "x".repeat(BrowserScripts.MAX_CONTENT_CHARS + 500)
        val raw = """{"status":"ok","url":"https://a.com","title":"t","content":"$big"}"""
        val result = BrowserScripts.parseRead(raw) as BrowserScripts.ReadResult.Ok
        assertTrue(result.truncated)
        assertEquals(BrowserScripts.MAX_CONTENT_CHARS, result.content.length)
    }

    @Test
    fun reportsReadFailureWithDetail() {
        val raw = """{"status":"error","detail":"页面异常"}"""
        val result = BrowserScripts.parseRead(raw)
        assertTrue(result is BrowserScripts.ReadResult.Failed)
        assertEquals("页面异常", (result as BrowserScripts.ReadResult.Failed).reason)
    }

    @Test
    fun treatsMissingContentAsFailureNotEmptySuccess() {
        // 没有 content 字段不能当成"页面是空的"——那是不同的结论
        val result = BrowserScripts.parseRead("""{"status":"ok","url":"https://a.com"}""")
        assertTrue(result is BrowserScripts.ReadResult.Ok)
        assertEquals("", (result as BrowserScripts.ReadResult.Ok).content)
    }

    @Test
    fun rejectsUnparsableReadResult() {
        val result = BrowserScripts.parseRead("not json at all")
        assertTrue(result is BrowserScripts.ReadResult.Failed)
    }

    @Test
    fun parsesActOk() {
        val raw = """{"status":"ok","tag":"BUTTON","label":"登录"}"""
        val result = BrowserScripts.parseAct(raw)
        assertTrue(result is BrowserScripts.ActResult.Ok)
        result as BrowserScripts.ActResult.Ok
        assertEquals("BUTTON", result.tag)
        assertEquals("登录", result.label)
    }

    @Test
    fun parsesActNotFoundDistinctlyFromError() {
        // 找不到元素与执行出错是不同的结论，模型的下一步动作也不同
        val notFound = BrowserScripts.parseAct("""{"status":"not_found"}""")
        assertTrue(notFound is BrowserScripts.ActResult.NotFound)
        val failed = BrowserScripts.parseAct("""{"status":"error","detail":"boom"}""")
        assertTrue(failed is BrowserScripts.ActResult.Failed)
        assertEquals("boom", (failed as BrowserScripts.ActResult.Failed).reason)
    }

    @Test
    fun parseWaitReportsHitAndMiss() {
        assertTrue(BrowserScripts.parseWait("""{"status":"ok","found":true}"""))
        assertFalse(BrowserScripts.parseWait("""{"status":"not_found","found":false}"""))
    }

    @Test
    fun parseWaitOnGarbageIsFalse() {
        assertFalse(BrowserScripts.parseWait("garbage"))
    }
}
