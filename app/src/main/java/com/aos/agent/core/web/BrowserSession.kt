package com.aos.agent.core.web

/**
 * 浏览器会话的最小接口。
 *
 * Agent 工具只依赖这个接口，不依赖具体的 [BrowserController]——
 * 这样单测能注入假实现，不必真的建 WebView（`WebView` 是 final，无法 mock，
 * 真建一个要主线程 + 几十 MB 内存，跑不进 JVM 单测）。
 */
interface BrowserSession {

    /** 是否已绑定页面。Agent 工具据此判断"浏览器页开没开"。 */
    val isAttached: Boolean

    /** 当前页 URL。没有页面时为 null。 */
    fun currentUrl(): String?

    /** 当前页标题。没有页面时为 null。 */
    fun currentTitle(): String?

    /**
     * 执行 JS 并取回原始结果。
     *
     * 返回 [JsOutcome.Failed] 而不抛异常——失败原因要如实转述给模型。
     */
    suspend fun evaluate(script: String, timeoutMillis: Long): JsOutcome

    /** 是否正在加载。 */
    val isLoading: Boolean

    /** JS 执行结果。 */
    sealed interface JsOutcome {
        data class Ok(val value: String) : JsOutcome
        data class Failed(val reason: String) : JsOutcome
    }
}
