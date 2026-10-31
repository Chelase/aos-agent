package com.aos.agent.ui.browser

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.lifecycle.AndroidViewModel
import com.aos.agent.core.web.BrowserController
import com.aos.agent.core.web.UrlGuard
import com.aos.agent.core.web.WebCapability
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 浏览器页状态。
 *
 * [BrowserUiState.Unavailable] 表示本机做不出 WebView——这是"能力缺失就明说"的场景，
 * 渲染原因而不是白屏。
 */
sealed interface BrowserUiState {
    data object Idle : BrowserUiState

    /** provider 缺失或不可用。 */
    data class Unavailable(val reason: String) : BrowserUiState

    data class Ready(
        val url: String = "",
        val title: String = "",
        val canGoBack: Boolean = false,
        val canGoForward: Boolean = false,
        val loading: Boolean = false,
        val progress: Int = 0,
        val error: String? = null,
        /** 已就绪的 WebView 实例。UI 侧只做托管，不重建。 */
        val webView: WebView? = null,
        /**
         * 是否已经加载过页面。
         *
         * 没加载过时 WebView 是一片黑——车机上非常突兀。首屏用起始页盖住，
         * 加载真实页面后再撤掉。
         */
        val hasContent: Boolean = false,
    ) : BrowserUiState
}

/**
 * 浏览器页宿主：持有 [BrowserController]，把 WebView 回调折叠成 UI 状态。
 *
 * WebView 必须在主线程创建，所以这里不做异步；页面销毁时显式 `release()`
 * ——native 内存不受 `maxHeap` 限制，不释放会持续涨并触发 LMK。
 */
class BrowserViewModel(application: Application) : AndroidViewModel(application) {

    private val controller = BrowserController()

    private val _state = MutableStateFlow<BrowserUiState>(BrowserUiState.Idle)
    val state: StateFlow<BrowserUiState> = _state

    private val _addressBar = MutableStateFlow("")
    val addressBar: StateFlow<String> = _addressBar

    /** Agent 工具用的会话句柄；页面没开时工具据此降级。 */
    val session: BrowserController get() = controller

    private var webView: WebView? = null

    /**
     * 创建并绑定 WebView。只能调一次（重复创建会泄漏前一个）。
     *
     * 创建失败（无 WebView provider）时进入降级态，页面渲染原因说明。
     */
    @SuppressLint("SetJavaScriptEnabled")
    fun attachWebView(context: Context) {
        if (webView != null) return
        val probe = WebCapability.probe(context)
        val created = probe.webView
        if (created == null) {
            val reason = (probe.availability as? WebCapability.Availability.Unavailable)?.reason
                ?: "本机无法创建 WebView"
            _state.value = BrowserUiState.Unavailable(reason)
            return
        }
        created.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            // 车机屏幕宽，让页面按桌面宽度排版更合理
            builtInZoomControls = false
            displayZoomControls = false
        }
        created.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                update {
                    it.copy(
                        url = url,
                        loading = true,
                        progress = 0,
                        error = null,
                        hasContent = true,
                    )
                }
                _addressBar.value = url
            }

            override fun onPageFinished(view: WebView, url: String) {
                update {
                    it.copy(
                        url = url,
                        title = view.title.orEmpty(),
                        loading = false,
                        progress = 100,
                        error = null,
                        canGoBack = view.canGoBack(),
                        canGoForward = view.canGoForward(),
                    )
                }
                _addressBar.value = url
            }

            override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
                update {
                    it.copy(
                        url = url,
                        canGoBack = view.canGoBack(),
                        canGoForward = view.canGoForward(),
                    )
                }
            }

            override fun onReceivedError(
                view: WebView,
                errorCode: Int,
                description: String,
                failingUrl: String,
            ) {
                // 只把主文档的错误当失败：子资源失败不该让整页报错
                if (failingUrl != view.url) return
                update {
                    it.copy(
                        loading = false,
                        error = BrowserController.describeError(errorCode, description),
                    )
                }
            }
        }
        created.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                update { it.copy(progress = newProgress, loading = newProgress < 100) }
            }
        }
        controller.attach(created)
        webView = created
        _state.value = BrowserUiState.Ready(webView = created)
    }

    /** 导航到 URL；非 URL 文本当搜索词处理。 */
    fun navigate(input: String) {
        val target = webView ?: return
        val url = BrowserInput.resolve(input) ?: return
        // 安全边界：只放行 https 公网。http 明确拒绝——明文可被中间人改内容。
        // 搜索链接是内置固定端点，不经过 URL 守卫。
        if (!url.startsWith(BrowserInput.SEARCH_PREFIX)) {
            val verdict = UrlGuard.check(url)
            if (!verdict.allowed) {
                update { it.copy(error = verdict.reason, loading = false) }
                return
            }
        }
        target.loadUrl(url)
    }

    fun goBack() {
        webView?.takeIf { it.canGoBack() }?.goBack()
    }

    fun goForward() {
        webView?.takeIf { it.canGoForward() }?.goForward()
    }

    fun reload() {
        webView?.reload()
    }

    /** 页面被移除时调用：显式释放 WebView，避免 native 内存持续涨。 */
    fun releaseWebView() {
        webView = null
        controller.release()
    }

    override fun onCleared() {
        releaseWebView()
        super.onCleared()
    }

    private fun update(block: (BrowserUiState.Ready) -> BrowserUiState.Ready) {
        val current = _state.value
        if (current is BrowserUiState.Ready) {
            _state.value = block(current)
        }
    }
}
