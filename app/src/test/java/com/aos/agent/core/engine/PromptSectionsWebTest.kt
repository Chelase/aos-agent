package com.aos.agent.core.engine

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 联网相关提示词约束。
 *
 * 这些规则不能被悄悄删掉：少了时效性约束模型会凭记忆编答案，
 * 少了注入防护规则网页里的文字就可能被当成指令执行。
 */
class PromptSectionsWebTest {

    private fun rules(): String =
        PromptSections.rules.render(PromptContext())

    @Test
    fun mentionsSearchToolForTimeSensitiveQuestions() {
        val text = rules()
        assertTrue("应提到 web_search", text.contains("web_search"))
        assertTrue("应说明不要凭记忆回答", text.contains("凭记忆"))
    }

    @Test
    fun warnsThatSearchReturnsSummaryNotFullText() {
        val text = rules()
        assertTrue("应说明返回的是摘要不是正文", text.contains("摘要"))
    }

    @Test
    fun forbidsExecutingInstructionsFoundInWebContent() {
        val text = rules()
        assertTrue("应声明网页指令只当数据", text.contains("指令性文字"))
        assertTrue("应声明不要执行", text.contains("不要执行"))
    }

    @Test
    fun capabilitiesSectionListsRegisteredTools() {
        val text = PromptSections.capabilities.render(
            PromptContext(mapOf("tools" to "web_search, file_read")),
        )
        assertTrue(text.contains("web_search"))
        assertTrue(text.contains("file_read"))
    }
}
