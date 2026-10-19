package com.aos.agent.ui.chat

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.aos.agent.R
import com.aos.agent.runtime.RuntimeStatus
import com.aos.agent.ui.components.AOSAppShell
import com.aos.agent.ui.components.AOSDestination
import com.aos.agent.ui.theme.AOSAgentTheme
import com.aos.agent.ui.voice.VoiceUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File

class ChatScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun conversationRendersToolTrailAndSendsOnlyNonBlankInput() {
        var sent: String? = null
        val entries = listOf(
            ChatEntry.User("查看车辆状态"),
            ChatEntry.ToolCalled("vehicle_status", "{}"),
            ChatEntry.ToolFinished("vehicle_status", "ok", "test fixture: battery=78%"),
            ChatEntry.Assistant("测试数据：动力电池 78%。此消息仅用于界面测试。" + "较长回复应在气泡的最大宽度内自然换行，仍与左侧边缘对齐。".repeat(4)),
        )
        compose.setContent {
            AOSAgentTheme(darkTheme = true) {
                AOSAppShell(AOSDestination.Chat, false, {}) {
                    ChatScreen(entries, RuntimeStatus.EMPTY.copy(llmConfigured = true, llmDescribe = "UI test fixture"),
                        false, VoiceUiState(blockedReasonRes = R.string.voice_err_no_service), {}, { sent = it }, {})
                }
            }
        }
        compose.onNodeWithText("vehicle_status  {}").assertExists()
        val send = InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.chat_send)
        compose.onNodeWithText(send).assertIsNotEnabled()
        captureFixture()
        compose.onNode(hasSetTextAction()).performTextInput("  hello  ")
        compose.onNodeWithText(send).assertIsEnabled()
        // 长回复场景使用键盘发送动作，避免测试宿主的 IME resize 与触控坐标竞争。
        // MainActivity 的真实发送按钮另由本地 HTTP + rail 切页用例覆盖。
        compose.onNode(hasSetTextAction()).performImeAction()
        assertEquals("hello", sent)
        compose.onNodeWithText(send).assertIsNotEnabled()
    }

    @Test fun microphoneWithOnlyPermissionMissingCanRequestPermission() {
        var requests = 0
        compose.setContent {
            AOSAgentTheme {
                ChatScreen(emptyList(), RuntimeStatus.EMPTY, false,
                    VoiceUiState(needsPermission = true, blockedReasonRes = R.string.voice_err_permission),
                    {}, {}, { requests++ })
            }
        }
        val label = InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.voice_action_listen)
        compose.onNodeWithContentDescription(label).assertIsEnabled().performClick()
        assertEquals(1, requests)
    }

    private fun captureFixture() {
        val args = InstrumentationRegistry.getArguments()
        val tag = args.getString("reviewTag") ?: return
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val image = instrumentation.uiAutomation.takeScreenshot() ?: error("原生截图失败")
        val directory = File(instrumentation.targetContext.filesDir, "v6-review/$tag").apply { mkdirs() }
        File(directory, "chat-fixture.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        image.recycle()
    }
}
