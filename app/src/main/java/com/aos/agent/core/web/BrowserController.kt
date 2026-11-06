package com.aos.agent.core.web

import android.webkit.WebView
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 浏览器会话控制器：WebView 的持有者 + JS 注入封装。
 *
 * 设计要点（都是踩坑得来的）：
 *
 * 1. **主线程**：WebView 的所有方法必须在创建它的线程调用，否则抛异常。
 * 2. **显式释放**：WebView 内存主要在 native 侧，不受 `maxHeap` 限制，
 *    不 `destroy()` 会持续涨并触发 LMK。离开页面必须 `destroy()` + 清引用。
 * 3. **注入串行化**：多个 JS 注入并发会互相穿插，页面上下文被搞乱，用 Mutex 串起来。
 * 4. **不用 addJavascriptInterface**：它把宿主对象直接暴露给任意页面。
 *    页面要回调宿主只能用 `addWebMessageListener` + origin 校验。
 *
 * **不设 WebViewClient**：页面状态回调（加载中/失败/标题）由 UI 层的 ViewModel 挂，
 * 否则两处都会设 client，互相覆盖——后设的那个赢，另一边的回调全丢。
 */
class BrowserController : BrowserSession {

    private var webView: WebView? = null
    private val injectLock = Mutex()

    /** 页面是否正在加载。Agent 读页时据此判断"还没加载完"。 */
    @Volatile
    private var loading: Boolean = false

    /**
     * 当前页 URL 与标题的缓存。
     *
     * **不能直接读 `webView.url`**：那是 WebView 方法，非主线程调用会抛
     * "A WebView method was called on thread ..."。Agent 工具在 runtime
     * 的协程里读 URL，不一定在主线程。
     *
     * 也不用 `runBlocking + withContext(Main)` 绕——从主线程调用会挂死
     * （主线程等自己）。所以由主线程的 WebViewClient 回调把值同步过来。
     */
    @Volatile
    private var pageUrl: String? = null

    @Volatile
    private var pageTitle: String? = null

    fun attach(webView: WebView) {
        this.webView = webView
    }

    /** 由 UI 层的 WebViewClient 回调同步加载态。 */
    fun setLoading(value: Boolean) {
        loading = value
    }

    /** 由 UI 层的 WebViewClient 回调同步当前页信息。必须从主线程调。 */
    fun setPageInfo(url: String?, title: String?) {
        if (url != null) pageUrl = url
        if (title != null) pageTitle = title
    }

    override fun currentUrl(): String? = pageUrl

    override fun currentTitle(): String? = pageTitle

    /** 是否已绑定 WebView。Agent 工具用它判断"浏览器页开没开"。 */
    override val isAttached: Boolean get() = webView != null

    override val isLoading: Boolean get() = loading

    /**
     * 执行 JS 并取回结果。
     *
     * 失败返回结构化原因，不抛异常——调用方（Agent 工具）需要把原因如实转述给模型。
     *
     * **WebView 调用必须切到主线程**：Agent 工具跑在 runtime 的协程里，
     * 那个线程不是 WebView 的创建线程，直接调会抛
     * "A WebView method was called on thread ..."。所以这里把 WebView 调用
     * 派发到主线程，等待仍在调用方线程——不在主线程上阻塞。
     */
    override suspend fun evaluate(script: String, timeoutMillis: Long): BrowserSession.JsOutcome {
        val target = webView ?: return BrowserSession.JsOutcome.Failed("浏览器页未打开")
        return injectLock.withLock {
            try {
                val deferred = CompletableDeferred<String?>()
                withContext(Dispatchers.Main) {
                    target.evaluateJavascript(script) { raw -> deferred.complete(raw) }
                }
                val raw = withTimeoutOrNull(timeoutMillis) { deferred.await() }
                    ?: return@withLock BrowserSession.JsOutcome.Failed("脚本执行超时（${timeoutMillis}ms）")
                BrowserSession.JsOutcome.Ok(decodeJsResult(raw))
            } catch (error: Throwable) {
                BrowserSession.JsOutcome.Failed("${error.javaClass.simpleName}: ${error.message}")
            }
        }
    }

    /** 释放 WebView。必须显式调用——native 内存不受 maxHeap 限制。 */
    fun release() {
        val target = webView
        webView = null
        loading = false
        target?.apply {
            // 先摘掉 client 再停加载，避免销毁过程中回调进已释放的状态
            webViewClient = android.webkit.WebViewClient()
            stopLoading()
            loadUrl("about:blank")
            (parent as? android.view.ViewGroup)?.removeView(this)
            destroy()
        }
    }

    companion object {
        const val DEFAULT_JS_TIMEOUT_MS = 10_000L

        /**
         * `evaluateJavascript` 返回的是 JSON 字面量：字符串带引号，null 是 "null"。
         * 这里剥掉外层引号并解转义，还原成普通字符串。
         */
        fun decodeJsResult(raw: String?): String {
            if (raw == null) return ""
            if (raw == "null") return ""
            if (raw.length >= 2 && raw.startsWith("\"") && raw.endsWith("\"")) {
                return raw.substring(1, raw.length - 1)
                    .replace("\\n", "\n")
                    .replace("\\t", "\t")
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\")
            }
            return raw
        }

        /** 把 WebView 的错误码翻译成人能读的原因。 */
        fun describeError(errorCode: Int, description: String): String = when (errorCode) {
            ERROR_HOST_LOOKUP -> "域名解析失败（$description）"
            ERROR_CONNECT -> "连接失败（$description）"
            ERROR_TIMEOUT -> "加载超时（$description）"
            ERROR_IO -> "网络传输中断（$description）"
            ERROR_UNSUPPORTED_SCHEME -> "不支持的协议"
            ERROR_PROXY_AUTH -> "代理认证失败"
            else -> description.ifBlank { "错误码 $errorCode" }
        }

        private const val ERROR_HOST_LOOKUP = -2
        private const val ERROR_CONNECT = -6
        private const val ERROR_TIMEOUT = -8
        private const val ERROR_IO = -7
        private const val ERROR_UNSUPPORTED_SCHEME = -3
        private const val ERROR_PROXY_AUTH = -9
    }
}
