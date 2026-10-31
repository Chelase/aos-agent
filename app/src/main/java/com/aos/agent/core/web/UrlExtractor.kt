package com.aos.agent.core.web

import java.net.URI

/**
 * 从文本里抽出可点击的 URL。
 *
 * 聊天里的回答常带链接，用户应该能直接点开进内置浏览器，而不是手动抄到地址栏。
 * 抽出来的链接必须是 https 公网地址——不然会把车机引向内网或明文页面。
 */
object UrlExtractor {

    /** 抽出的一个链接及其在原文本中的位置。 */
    data class Link(val url: String, val start: Int, val end: Int)

    /**
     * 抽出文本中的 https 链接。
     *
     * 只认 `https://` 开头的：明文 http 不该成为可点入口——用户一点就离开了
     * 安全边界，而这些链接多半来自模型输出，不能默认可信。
     *
     * 抽出的地址还要过 [UrlGuard]：内网地址、回环、云元数据一律剔除。
     */
    fun extract(text: String): List<Link> {
        val links = mutableListOf<Link>()
        var index = 0
        while (index < text.length) {
            val start = text.indexOf(SCHEME, index)
            if (start < 0) break
            val end = findUrlEnd(text, start)
            if (end <= start) {
                index = start + SCHEME.length
                continue
            }
            val candidate = text.substring(start, end)
            val normalized = normalize(candidate)
            if (normalized != null && UrlGuard.check(normalized).allowed) {
                links += Link(normalized, start, end)
            }
            index = end
        }
        return links
    }

    /**
     * URL 的结束位置。
     *
     * 遇到空白和常见的 URL 终止符就停。中文标点也算终止符——模型输出常写成
     * "见 https://example.com。"，句号不该进 URL。
     */
    private fun findUrlEnd(text: String, start: Int): Int {
        var index = start
        while (index < text.length) {
            val ch = text[index]
            if (ch.isWhitespace() || ch in TERMINATORS) break
            index++
        }
        // 去掉结尾的标点
        while (index > start && text[index - 1] in TRAILING) index--
        return index
    }

    /**
     * 规范化 URL。解析不了或不是 https 就返回 null。
     */
    private fun normalize(raw: String): String? = runCatching {
        val uri = URI(raw)
        if (!uri.scheme.equals("https", ignoreCase = true)) return null
        if (uri.host.isNullOrBlank()) return null
        uri.toString()
    }.getOrNull()

    private const val SCHEME = "https://"

    /** URL 内部不应出现的字符。 */
    private val TERMINATORS = charArrayOf('"', '\'', '<', '>', '`', ' ', '\n', '\r', '\t', '（', '）', '【', '】', '，', '。', '；', '：', '、')

    /** URL 结尾可能被误收的标点。 */
    private val TRAILING = charArrayOf('.', ',', ';', ':', '!', '?', '，', '。', '；', '：')
}
