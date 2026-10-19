package com.aos.agent.ui

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.aos.agent.R
import com.aos.agent.MainActivity
import com.aos.agent.data.store.ThemeStore
import com.aos.agent.data.store.LlmConfigStore
import com.aos.agent.core.llm.LlmConfig
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/** 导航与驾驶裁剪的行为契约；传入 reviewTag 时同时保存原生走查证据。 */
class V6NavigationTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val args get() = InstrumentationRegistry.getArguments()

    private fun launch(restricted: Boolean = false): ActivityScenario<MainActivity> {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        runBlocking { ThemeStore(context).saveDark(args.getString("reviewDark") == "true") }
        return ActivityScenario.launch(Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_PREVIEW_DRIVE_RESTRICTED, restricted))
    }

    @Test fun railNavigatesSixDestinationsAndSystemBackReturnsHome() {
        launch().use { scenario ->
            compose.onNodeWithText("AOSAgent").assertIsDisplayed()
            capture("home")
            listOf(
                Triple(R.string.action_chat, R.string.chat_empty_title, "chat"),
                Triple(R.string.action_terminal, R.string.terminal_subtitle, "terminal"),
                Triple(R.string.nav_panel, R.string.panel_title, "panel"),
                Triple(R.string.action_settings, R.string.settings_capabilities, "settings"),
                Triple(R.string.nav_engineer, R.string.engineer_title, "engineer"),
            ).forEach { (labelRes, expectedRes, name) ->
                var label = ""
                var expected = ""
                scenario.onActivity { label = it.getString(labelRes); expected = it.getString(expectedRes) }
                compose.onAllNodesWithText(label).onFirst().performClick()
                compose.onNodeWithText(expected).assertExists()
                compose.waitForIdle()
                capture(name)
                scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
                compose.onNodeWithText("AOSAgent").assertIsDisplayed()
            }
        }
    }

    @Test fun drivingDisablesTechnicalEntriesAndCoversChatUntilReturningHome() {
        launch(restricted = true).use {
            compose.onAllNodesWithText("终端").assertAll(isNotEnabled())
            compose.onNodeWithText("工程").assertIsNotEnabled()
            compose.onAllNodesWithText("对话").onFirst().performClick()
            compose.onNodeWithText("行驶中 · 只用语音").assertIsDisplayed()
            capture("drive-mask")
            compose.onNodeWithText("返回首页").performClick()
            compose.onNodeWithText("AOSAgent").assertIsDisplayed()
        }
    }

    @Test fun railPreservesUnsentChatAndSettingsDrafts() {
        launch().use {
            compose.onNodeWithText("设置").performClick()
            compose.onNodeWithText("模型名").performScrollTo().performTextInput("unsaved-model-draft")
            compose.onNodeWithText("首页").performClick()
            compose.onNodeWithText("设置").performClick()
            compose.onNodeWithText("unsaved-model-draft").performScrollTo().assertIsDisplayed()
            compose.onAllNodesWithText("对话").onFirst().performClick()
            compose.onNode(hasSetTextAction()).performTextInput("unsent-chat-draft")
            compose.onNodeWithText("面板").performClick()
            compose.onAllNodesWithText("对话").onFirst().performClick()
            compose.onNodeWithText("unsent-chat-draft").assertIsDisplayed()
        }
    }

    @Test fun activeReplySurvivesRailNavigation() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val store = LlmConfigStore(context)
        val previous = runBlocking { store.current() }
        val requested = CountDownLatch(1)
        val release = CountDownLatch(1)
        val reply = "Navigation fixture reply"
        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { server ->
            server.soTimeout = 15_000
            val worker = thread(isDaemon = true, name = "navigation-ui-fixture") {
                server.accept().use { socket ->
                    socket.soTimeout = 15_000
                    val input = socket.getInputStream()
                    val header = ByteArrayOutputStream()
                    var tail = 0
                    while (tail != 0x0d0a0d0a) {
                        val byte = input.read()
                        check(byte >= 0)
                        header.write(byte)
                        tail = (tail shl 8) or byte
                    }
                    val length = header.toString("UTF-8").lineSequence()
                        .first { it.startsWith("Content-Length:", ignoreCase = true) }
                        .substringAfter(':').trim().toInt()
                    repeat(length) { check(input.read() >= 0) }
                    requested.countDown()
                    check(release.await(15, TimeUnit.SECONDS))
                    val body = "data: {\"choices\":[{\"delta\":{\"content\":\"$reply\"}}]}\n\ndata: [DONE]\n\n"
                        .toByteArray()
                    socket.getOutputStream().apply {
                        write("HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray())
                        write(body)
                        flush()
                    }
                }
            }
            try {
                runBlocking { store.save(LlmConfig("http://127.0.0.1:${server.localPort}/v1", "ui-fixture", "")) }
                launch().use {
                    compose.onAllNodesWithText("对话").onFirst().performClick()
                    compose.waitUntil(15_000) { compose.onAllNodesWithText("模型已就绪").fetchSemanticsNodes().isNotEmpty() }
                    compose.onNode(hasSetTextAction()).performTextInput("navigation fixture request")
                    compose.onNodeWithText("发送").performClick()
                    compose.waitUntil(15_000) { requested.count == 0L }
                    compose.onNodeWithText("设置").performClick()
                    release.countDown()
                    compose.onAllNodesWithText("对话").onFirst().performClick()
                    compose.waitUntil(15_000) { compose.onAllNodesWithText(reply).fetchSemanticsNodes().isNotEmpty() }
                    compose.onNodeWithText("navigation fixture request").assertExists()
                    compose.onNodeWithText(reply).assertIsDisplayed()
                }
            } finally {
                release.countDown()
                runBlocking { if (previous == null) store.clear() else store.save(previous) }
                worker.join(1_000)
            }
        }
    }

    private fun capture(name: String) {
        val tag = args.getString("reviewTag") ?: return
        compose.waitForIdle()
        instrumentation.waitForIdleSync()
        // waitForIdle 只等待 Compose；再留一帧给 AAOS 窗口合成器提交画面。
        Thread.sleep(350)
        val image = instrumentation.uiAutomation.takeScreenshot() ?: error("原生截图失败")
        val directory = File(instrumentation.targetContext.filesDir, "v6-review/$tag").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        image.recycle()
    }
}
