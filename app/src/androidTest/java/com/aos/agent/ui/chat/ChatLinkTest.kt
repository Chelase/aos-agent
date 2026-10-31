package com.aos.agent.ui.chat

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.aos.agent.R
import com.aos.agent.runtime.RuntimeStatus
import com.aos.agent.ui.components.AOSAppShell
import com.aos.agent.ui.components.AOSDestination
import com.aos.agent.ui.theme.AOSAgentTheme
import com.aos.agent.ui.voice.VoiceUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/**
 * 助手回答里的链接可点，并交给宿主去打开内置浏览器。
 */
class ChatLinkTest {

    @get:Rule
    val compose = createComposeRule()

    private fun render(entries: List<ChatEntry>, onOpenLink: (String) -> Unit) {
        compose.setContent {
            AOSAgentTheme(darkTheme = true) {
                AOSAppShell(AOSDestination.Chat, false, {}) {
                    ChatScreen(
                        entries = entries,
                        status = RuntimeStatus.EMPTY.copy(llmConfigured = true, llmDescribe = "fixture"),
                        busy = false,
                        voiceState = VoiceUiState(blockedReasonRes = R.string.voice_err_no_service),
                        onBackClick = {},
                        onSend = {},
                        onVoiceToggle = {},
                        onOpenLink = onOpenLink,
                    )
                }
            }
        }
    }

    @Test
    fun clickingLinkInAssistantReplyReportsTheUrl() {
        var opened: String? = null
        render(
            listOf(
                ChatEntry.Assistant("参考资料：https://example.com/docs 谢谢"),
            ),
        ) { opened = it }

        // 链接在语义树里是独立节点，用 substring 匹配
        compose.onNodeWithText("https://example.com/docs", substring = true).assertExists()
        compose.onAllNodesWithText("https://example.com/docs", substring = true).onFirst().performClick()
        assertEquals("https://example.com/docs", opened)
    }

    @Test
    fun internalAddressesAreNotRenderedAsLinks() {
        var opened: String? = null
        render(
            listOf(
                ChatEntry.Assistant("内网地址 https://192.168.0.1/ 不可点"),
            ),
        ) { opened = it }

        // 内网地址根本不该出现在可点链接里
        compose.onNodeWithText("内网地址 https://192.168.0.1/ 不可点").assertIsDisplayed()
        assertNull(opened)
    }

    @Test
    fun plainHttpIsNotRenderedAsLink() {
        var opened: String? = null
        render(
            listOf(
                ChatEntry.Assistant("明文地址 http://example.com 不该可点"),
            ),
        ) { opened = it }

        compose.onNodeWithText("明文地址 http://example.com 不该可点").assertIsDisplayed()
        assertNull(opened)
    }
}
