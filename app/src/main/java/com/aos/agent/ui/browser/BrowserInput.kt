package com.aos.agent.ui.browser

import java.net.URLEncoder

/**
 * 地址栏输入解析：把用户输入解析成要加载的 URL。
 *
 * 抽成纯函数是为了能单测——"这是网址还是搜索词"的判断最容易出错，
 * 判错就是白屏，而白屏在车上很难排查。
 */
object BrowserInput {

    const val SEARCH_PREFIX = "https://duckduckgo.com/?q="

    /**
     * 解析用户输入。无法解析时返回 null（调用方应忽略这次输入）。
     *
     * 规则：
     * - 显式 `http://` / `https://` 原样使用；
     * - 看起来像主机名（含点、无空格）的补 `https://`；
     * - 其余当搜索词，编码后拼到搜索端点。
     *
     * 注意：这里**不判断是否 http/https**——安全边界由 `UrlGuard` 在加载前把关。
     * 两件事分开，才能各自单测。
     */
    fun resolve(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null
        return when {
            trimmed.startsWith("https://") -> trimmed
            trimmed.startsWith("http://") -> trimmed
            looksLikeHost(trimmed) -> "https://$trimmed"
            else -> SEARCH_PREFIX + URLEncoder.encode(trimmed, "UTF-8")
        }
    }

    /**
     * 判断输入像不像主机名（含点且不像句子）。
     *
     * "example.com" 是地址，"天气怎么样" 是搜索词——靠点号区分是常见做法，
     * 但 "3.14" 这种会被误判，所以再要求不含空格。
     */
    fun looksLikeHost(text: String): Boolean =
        text.contains('.') && !text.contains(' ') && !text.endsWith('.') &&
            text.substringBefore('.').isNotBlank()
}
