package com.aos.agent.core.web

/**
 * HTML → 纯文本提取。纯函数，不碰网络与 Android 上下文，方便单测。
 *
 * 目标不是"完美还原网页"，而是**给模型看的可读正文**：
 * 剥掉脚本样式噪声、保留段落结构、解实体、折叠空白。
 */
object HtmlTextExtractor {

    data class Extracted(
        val title: String,
        val text: String,
        val links: List<Link>,
    )

    data class Link(val text: String, val href: String)

    private val BLOCK_TAGS = setOf(
        "p", "div", "section", "article", "header", "footer", "main", "aside",
        "br", "li", "ul", "ol", "tr", "table", "h1", "h2", "h3", "h4", "h5", "h6",
        "blockquote", "pre", "hr", "form", "figure",
    )

    private val DROP_WITH_CONTENT = listOf("script", "style", "noscript", "template", "svg", "head")

    /** 抽取正文、标题与链接。 */
    fun extract(html: String): Extracted {
        // 标题要在剥噪声之前取：<title> 在 <head> 里，而 head 会被整块丢掉
        val title = extractTitle(html)
        val cleaned = stripNoise(html)
        val links = extractLinks(cleaned)
        val text = toText(cleaned)
        return Extracted(title = title, text = text, links = links)
    }

    /** 只取可读正文。 */
    fun toText(html: String): String {
        var body = stripNoise(html)
        body = removeComments(body)
        body = replaceTag(body, "br", "\n")
        body = replaceTag(body, "li", "\n")
        body = replaceTag(body, "p", "\n\n")
        body = replaceTag(body, "div", "\n")
        body = replaceTag(body, "section", "\n")
        body = replaceTag(body, "article", "\n")
        body = replaceTag(body, "tr", "\n")
        body = replaceTag(body, "h1", "\n\n")
        body = replaceTag(body, "h2", "\n\n")
        body = replaceTag(body, "h3", "\n\n")
        body = replaceTag(body, "h4", "\n\n")
        body = replaceTag(body, "h5", "\n\n")
        body = replaceTag(body, "h6", "\n\n")
        body = replaceTag(body, "blockquote", "\n")
        body = replaceTag(body, "pre", "\n")
        // 剩下所有标签去掉，只留内容
        body = body.replace(Regex("<[^>]*>"), "")
        body = decodeEntities(body)
        return collapseWhitespace(body)
    }

    private fun extractTitle(html: String): String {
        val match = Regex("<title[^>]*>(.*?)</title>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .find(html) ?: return ""
        return decodeEntities(match.groupValues[1]).trim()
    }

    private fun extractLinks(html: String): List<Link> {
        val pattern = Regex(
            "<a[^>]*href\\s*=\\s*[\"']([^\"']+)[\"'][^>]*>(.*?)</a>",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        )
        return pattern.findAll(html).map { match ->
            val href = decodeEntities(match.groupValues[1]).trim()
            val label = decodeEntities(
                match.groupValues[2].replace(Regex("<[^>]*>"), "")
            ).trim()
            Link(text = label, href = href)
        }.filter { it.href.isNotEmpty() }.take(MAX_LINKS).toList()
    }

    /** 去掉脚本/样式等整块内容，避免噪声混进正文。 */
    private fun stripNoise(html: String): String {
        var result = html
        for (tag in DROP_WITH_CONTENT) {
            result = result.replace(
                Regex("<$tag[^>]*>.*?</$tag>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)),
                " ",
            )
            // 自闭合或未闭合的残留
            result = result.replace(Regex("<$tag[^>]*/?>"), " ")
        }
        return result
    }

    private fun removeComments(html: String): String =
        html.replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), " ")

    /** 把标签替换成换行/段落标记。 */
    private fun replaceTag(html: String, tag: String, marker: String): String {
        val open = Regex("<$tag(\\s[^>]*)?>", RegexOption.IGNORE_CASE)
        val close = Regex("</$tag\\s*>", RegexOption.IGNORE_CASE)
        return html.replace(open, marker).replace(close, marker)
    }

    private fun decodeEntities(text: String): String {
        var result = text
        for ((entity, decoded) in ENTITIES) {
            result = result.replace(entity, decoded)
        }
        // 数字实体 &#123; / &#x1F;
        result = result.replace(Regex("&#x([0-9a-fA-F]+);")) { match ->
            match.groupValues[1].toIntOrNull(16)?.let { codePointToString(it) } ?: match.value
        }
        result = result.replace(Regex("&#(\\d+);")) { match ->
            match.groupValues[1].toIntOrNull()?.let { codePointToString(it) } ?: match.value
        }
        return result
    }

    private fun codePointToString(code: Int): String =
        if (code in 1..0x10FFFF) String(Character.toChars(code)) else ""

    /** 折叠空白：保留段落换行，压掉行内多余空格。 */
    private fun collapseWhitespace(text: String): String {
        val normalized = text.replace("\r\n", "\n").replace('\r', '\n')
        val lines = normalized.split('\n').map { line ->
            line.replace(Regex("[\\t\\u00A0\\u200B]+"), " ").replace(Regex(" {2,}"), " ").trim()
        }
        return lines.joinToString("\n").replace(Regex("\n{3,}"), "\n\n").trim()
    }

    private val ENTITIES = listOf(
        "&nbsp;" to " ",
        "&amp;" to "&",
        "&lt;" to "<",
        "&gt;" to ">",
        "&quot;" to "\"",
        "&apos;" to "'",
        "&#39;" to "'",
        "&hellip;" to "…",
        "&mdash;" to "—",
        "&ndash;" to "–",
        "&ldquo;" to "“",
        "&rdquo;" to "”",
        "&lsquo;" to "‘",
        "&rsquo;" to "’",
    )

    private const val MAX_LINKS = 200
}
