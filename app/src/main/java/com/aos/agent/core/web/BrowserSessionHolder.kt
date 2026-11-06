package com.aos.agent.core.web

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 进程级浏览器会话。
 *
 * **为什么必须是单例**：Agent 工具（跑在 [com.aos.agent.runtime.AgentRuntime] 里）
 * 和浏览器页（跑在 [com.aos.agent.ui.browser.BrowserViewModel] 里）要操作
 * **同一个 WebView 实例**。两边各建一个，Agent 读到的就是另一个空页面——
 * 这是 Phase 3 最核心的接线问题。
 *
 * 浏览器页负责创建与销毁 WebView，Agent 工具只读与注入。谁创建谁负责释放，
 * 这样"离开页面必须 destroy"这条约束仍然只有一处实现。
 */
object BrowserSessionHolder {

    private val _controller = MutableStateFlow<BrowserController?>(null)

    /** 当前会话。没有页面打开时为 null。 */
    val controller: StateFlow<BrowserController?> = _controller

    /** 页面创建 WebView 后登记自己。 */
    fun register(controller: BrowserController) {
        _controller.value = controller
    }

    /** 页面销毁 WebView 后注销自己。 */
    fun unregister(controller: BrowserController) {
        if (_controller.value === controller) {
            _controller.value = null
        }
    }

    /** 当前会话，没有则 null。 */
    fun current(): BrowserController? = _controller.value
}
