package com.aos.agent.ui.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.aos.agent.core.tools.mcp.McpServerConfig
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * 设置页的界面契约：保存按钮在必填项为空时必须禁用，点击后回传的就是输入框里的值。
 *
 * 用 Compose 测试而不是 adb 输入：这台模拟器的 `input text` 会把 `.` `/` `:`
 * 打成全角字符，测不出真实输入链路。
 */
class SettingsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun saveModelButtonIsDisabledUntilUrlAndModelAreFilled() {
        var saved: Triple<String, String, String>? = null
        composeRule.setContent {
            SettingsScreen(
                currentLlm = null,
                mcpServers = emptyList(),
                onBackClick = {},
                onSaveModel = { url, model, key -> saved = Triple(url, model, key) },
                onSaveMcp = { _, _ -> },
                onDeleteMcp = {},
            )
        }

        composeRule.onNodeWithText("保存模型配置").performScrollTo().assertIsNotEnabled()

        composeRule.onNodeWithText("Base URL").performScrollTo().performTextInput("https://relay.example/v1")
        composeRule.onNodeWithText("保存模型配置").assertIsNotEnabled()

        composeRule.onNodeWithText("模型名").performScrollTo().performTextInput("gpt-ish")
        composeRule.onNodeWithText("保存模型配置").performScrollTo().assertIsEnabled().performClick()

        assertEquals(Triple("https://relay.example/v1", "gpt-ish", ""), saved)
        composeRule.onNodeWithText("已保存").assertExists()
    }

    @Test
    fun existingSourcesAreListedAndDeletableOnlyWhenSelected() {
        val servers = listOf(McpServerConfig(id = "fixture", url = "http://localhost:9101/mcp"))
        var deleted: String? = null
        composeRule.setContent {
            SettingsScreen(
                currentLlm = Triple("https://a/v1", "m", "k"),
                mcpServers = servers,
                onBackClick = {},
                onSaveModel = { _, _, _ -> },
                onSaveMcp = { _, _ -> },
                onDeleteMcp = { deleted = it },
            )
        }

        composeRule.onNodeWithText("http://localhost:9101/mcp · ask").performScrollTo().assertExists()
        // 来源名默认填 fixture，命中已有条目，删除按钮应可用
        composeRule.onNodeWithText("删除来源").performScrollTo().assertIsEnabled().performClick()
        assertEquals("fixture", deleted)
    }

    @Test
    fun noSourcesShowsEmptyStateAndDeleteIsBlocked() {
        composeRule.setContent {
            SettingsScreen(
                currentLlm = null,
                mcpServers = emptyList(),
                onBackClick = {},
                onSaveModel = { _, _, _ -> },
                onSaveMcp = { _, _ -> },
                onDeleteMcp = {},
            )
        }

        composeRule.onNodeWithText("尚未添加任何 MCP 来源").performScrollTo().assertExists()
        composeRule.onNodeWithText("删除来源").performScrollTo().assertIsNotEnabled()
    }
}
