package com.aos.agent.core.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * HTML 文本提取测试。
 *
 * 重点是"噪声不进正文"——脚本内容泄漏进正文会让模型把 JS 代码
 * 当成页面内容读，是很典型的脏数据来源。
 */
class HtmlTextExtractorTest {

    @Test
    fun extractsVisibleText() {
        val html = "<html><body><h1>标题</h1><p>第一段。</p><p>第二段。</p></body></html>"
        val text = HtmlTextExtractor.toText(html)
        assertTrue(text.contains("标题"))
        assertTrue(text.contains("第一段。"))
        assertTrue(text.contains("第二段。"))
    }

    @Test
    fun scriptContentDoesNotLeakIntoText() {
        val html = """
            <html><head><script>var secret = "TOKEN_ABC123";</script></head>
            <body><script>console.log("also secret")</script><p>正文内容</p></body></html>
        """.trimIndent()
        val text = HtmlTextExtractor.toText(html)
        assertTrue(text.contains("正文内容"))
        assertFalse("脚本内容不应进正文：$text", text.contains("TOKEN_ABC123"))
        assertFalse("脚本内容不应进正文：$text", text.contains("also secret"))
    }

    @Test
    fun styleContentIsRemoved() {
        val html = "<html><style>.a{color:red}</style><body><p>文本</p></body></html>"
        val text = HtmlTextExtractor.toText(html)
        assertTrue(text.contains("文本"))
        assertFalse(text.contains("color:red"))
    }

    @Test
    fun commentsAreRemoved() {
        val html = "<body><!-- 注释里的内容 --><p>正文</p></body>"
        val text = HtmlTextExtractor.toText(html)
        assertFalse(text.contains("注释里的内容"))
        assertTrue(text.contains("正文"))
    }

    @Test
    fun decodesEntities() {
        val html = "<p>A &amp; B &lt;tag&gt; &quot;quoted&quot; &#39;single&#39; &nbsp;end</p>"
        val text = HtmlTextExtractor.toText(html)
        assertTrue(text.contains("A & B"))
        assertTrue(text.contains("<tag>"))
        assertTrue(text.contains("\"quoted\""))
        assertTrue(text.contains("'single'"))
    }

    @Test
    fun decodesNumericEntities() {
        val html = "<p>&#65;&#x42;&#x1F600;</p>"
        val text = HtmlTextExtractor.toText(html)
        assertTrue(text.contains("AB"))
    }

    @Test
    fun extractsTitle() {
        val html = "<html><head><title>页面标题</title></head><body>x</body></html>"
        assertEquals("页面标题", HtmlTextExtractor.extract(html).title)
    }

    @Test
    fun extractsLinks() {
        val html = """
            <body>
              <a href="https://example.com/a">链接甲</a>
              <a href="https://example.com/b">链接乙</a>
            </body>
        """.trimIndent()
        val links = HtmlTextExtractor.extract(html).links
        assertEquals(2, links.size)
        assertEquals("https://example.com/a", links[0].href)
        assertEquals("链接甲", links[0].text)
    }

    @Test
    fun keepsBlockStructureAsLineBreaks() {
        val html = "<body><p>一</p><p>二</p><ul><li>甲</li><li>乙</li></ul></body>"
        val text = HtmlTextExtractor.toText(html)
        assertTrue("段落之间应换行：\n$text", text.contains("\n"))
        assertTrue(text.contains("甲"))
        assertTrue(text.contains("乙"))
    }

    @Test
    fun collapsesExcessiveWhitespace() {
        val html = "<body><p>很   多    空格</p><p>\n\n\n换行</p></body>"
        val text = HtmlTextExtractor.toText(html)
        assertTrue(text.contains("很 多 空格"))
        assertFalse(text.contains("   "))
    }

    @Test
    fun handlesEmptyAndMalformedInput() {
        assertEquals("", HtmlTextExtractor.toText(""))
        assertEquals("", HtmlTextExtractor.toText("   "))
        // 标签没闭合也不能抛异常
        HtmlTextExtractor.toText("<div><p>未闭合")
    }

    @Test
    fun handlesRealWorldShape() {
        // 搜索结果页的典型结构
        val html = """
            <div class="result">
              <h2 class="result__title"><a class="result__a" href="//duckduckgo.com/l/?uddg=https%3A%2F%2Fexample.com%2Fdoc&amp;rut=x">示例文档</a></h2>
              <a class="result__snippet">这是摘要内容。</a>
            </div>
        """.trimIndent()
        val extracted = HtmlTextExtractor.extract(html)
        assertTrue(extracted.text.contains("示例文档"))
        assertTrue(extracted.text.contains("这是摘要内容。"))
        assertEquals(1, extracted.links.size)
    }
}
