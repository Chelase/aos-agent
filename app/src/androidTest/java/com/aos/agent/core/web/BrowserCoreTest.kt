package com.aos.agent.core.web

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

/**
 * 浏览器核心层的纯逻辑测试。
 *
 * 跑在设备上（需要 Context 探测 provider），但只测不需要真实网络的纯函数。
 */
@RunWith(AndroidJUnit4::class)
class BrowserCoreTest {

    @Test
    fun decodesJsonStringResult() {
        // evaluateJavascript 返回 JSON 字面量：字符串带引号
        assertEquals("hello", BrowserController.decodeJsResult("\"hello\""))
        assertEquals("", BrowserController.decodeJsResult("null"))
        assertEquals("", BrowserController.decodeJsResult(null))
    }

    @Test
    fun decodesEscapedCharacters() {
        assertEquals("line1\nline2", BrowserController.decodeJsResult("\"line1\\nline2\""))
        assertEquals("a\tb", BrowserController.decodeJsResult("\"a\\tb\""))
        assertEquals("say \"hi\"", BrowserController.decodeJsResult("\"say \\\"hi\\\"\""))
    }

    @Test
    fun keepsPlainObjectResultsIntact() {
        // 对象/数组不是字符串，原样返回
        assertEquals("{\"a\":1}", BrowserController.decodeJsResult("{\"a\":1}"))
        assertEquals("[1,2]", BrowserController.decodeJsResult("[1,2]"))
    }

    @Test
    fun describesWebViewErrorsInReadableChinese() {
        val text = BrowserController.describeError(-2, "net::ERR_NAME_NOT_RESOLVED")
        assertTrue("应含可读原因：$text", text.contains("域名解析失败"))
        assertTrue("应保留原始描述：$text", text.contains("ERR_NAME_NOT_RESOLVED"))
    }

    @Test
    fun describesUnknownErrorWithCode() {
        val text = BrowserController.describeError(-999, "")
        assertTrue("未知错误应给出错误码：$text", text.contains("-999"))
    }

    @Test
    fun urlGuardStillRejectsInternalAddresses() {
        assertFalse(UrlGuard.check("https://127.0.0.1/").allowed)
        assertFalse(UrlGuard.check("https://169.254.169.254/").allowed)
        assertFalse(UrlGuard.check("https://192.168.0.1/").allowed)
        assertFalse(UrlGuard.check("http://example.com").allowed)
        assertTrue(UrlGuard.check("https://example.com").allowed)
    }

    @Test
    fun probesWebViewProviderOnRealDevice() {
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val result = WebCapability.probe(context)
        // 设备上要么可用、要么给出明确原因——不允许"不知道"
        when (val availability = result.availability) {
            is WebCapability.Availability.Available ->
                assertTrue("可用时应交回实例", result.webView != null)
            is WebCapability.Availability.Unavailable ->
                assertTrue("降级原因不应为空", availability.reason.isNotBlank())
        }
        // 探测出的实例要释放，不能泄漏 native 内存
        result.webView?.destroy()
    }

    @Test
    fun controllerReportsNotAttachedBeforeWebViewExists() {
        val controller = BrowserController()
        assertFalse(controller.isAttached)
        assertEquals(null, controller.currentUrl())
    }
}
