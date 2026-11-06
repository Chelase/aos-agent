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

    @Test
    fun mentionsBrowserToolsForCurrentPageWork() {
        val text = rules()
        assertTrue("应提到 browser_read", text.contains("browser_read"))
        assertTrue("应提到 browser_act", text.contains("browser_act"))
    }

    @Test
    fun statesThatClickAndFillNeedConfirmation() {
        val text = rules()
        // 改页面状态的动作必须人工确认，提示词里要说清楚
        assertTrue("应说明 click/fill 需要确认", text.contains("确认"))
        assertTrue("应说明只读动作直接执行", text.contains("直接执行"))
    }

    @Test
    fun keepsBrowserRulesWithinBudget() {
        // 提示词增长要受控，否则每轮对话都在烧 token
        val text = rules()
        assertTrue(
            "浏览器相关规则不应超过 400 字，实际 ${text.length}",
            text.length < 1200,
        )
    }
}
