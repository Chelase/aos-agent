package com.aos.agent.ui

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.aos.agent.MainActivity
import com.aos.agent.data.store.LlmConfigStore
import com.aos.agent.core.llm.LlmConfig
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * 聊天 → 浏览器 → 返回聊天的栈式导航。
 *
 * 这是内置浏览器最关键的串联：用户在对话里看到链接，点开进浏览器看完，
 * 返回时对话还在（不是回首页从头再来）。
 */
class BrowserStackNavigationTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    private fun launch(): ActivityScenario<MainActivity> {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        return ActivityScenario.launch(Intent(context, MainActivity::class.java))
    }

    @Test
    fun clickingAnswerLinkOpensBrowserAndBackReturnsToChat() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val store = LlmConfigStore(context)
        val previous = runBlocking { store.current() }
        val link = "https://example.com/answer"
        val requested = CountDownLatch(1)
        val release = CountDownLatch(1)
        val reply = "参考 $link 了解更多"

        // 本机若设了系统代理，OkHttp 会把到 127.0.0.1 的请求也送进代理，
        // fixture 服务器就永远收不到。测前关掉，测完原样恢复。
        val proxyBefore = readProxy()
        setProxyOff()

        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { server ->
            server.soTimeout = 20_000
            val worker = thread(isDaemon = true, name = "browser-nav-fixture") {
                server.accept().use { socket ->
                    socket.soTimeout = 20_000
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
                    check(release.await(20, TimeUnit.SECONDS))
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
                runBlocking {
                    store.save(LlmConfig("http://127.0.0.1:${server.localPort}/v1", "browser-fixture", ""))
                }
                launch().use {
                    // 先在对话里发一句，等回答里出现链接
                    compose.onAllNodesWithText("对话").onFirst().performClick()
                    compose.waitUntil(20_000) {
                        compose.onAllNodesWithText("模型已就绪").fetchSemanticsNodes().isNotEmpty()
                    }
                    compose.onNode(hasSetTextAction()).performTextInput("给我一个链接")
                    compose.onNodeWithText("发送").performClick()
                    compose.waitUntil(20_000) { requested.count == 0L }
                    release.countDown()
                    compose.waitUntil(20_000) {
                        compose.onAllNodesWithText(link, substring = true).fetchSemanticsNodes().isNotEmpty()
                    }

                    // 点链接 → 进浏览器
                    compose.onAllNodesWithText(link, substring = true).onFirst().performClick()
                    compose.waitForIdle()
                    compose.onAllNodesWithText("浏览器", substring = true).fetchSemanticsNodes()
                    val browserTitle = "浏览器"
                    compose.onAllNodesWithText(browserTitle).onFirst().assertIsDisplayed()

                    // 返回 → 回对话，且对话内容还在
                    compose.onAllNodesWithText("返回").onFirst().performClick()
                    compose.waitForIdle()
                    compose.onNodeWithText("给我一个链接").assertIsDisplayed()
                    compose.onAllNodesWithText(link, substring = true).onFirst().assertIsDisplayed()
                }
            } finally {
                release.countDown()
                runBlocking { if (previous == null) store.clear() else store.save(previous) }
                worker.join(1_000)
                setProxy(proxyBefore)
            }
        }
    }

    /** 读系统代理设置。 */
    private fun readProxy(): String = shell("settings get global http_proxy").trim()

    /** 关闭系统代理。 */
    private fun setProxyOff() {
        shell("settings put global http_proxy :0")
    }

    /** 恢复系统代理。 */
    private fun setProxy(value: String) {
        if (value.isBlank() || value == "null") return
        shell("settings put global http_proxy $value")
    }

    /** 通过 UiAutomation 执行 shell 命令——app 进程自己无权改全局设置。 */
    private fun shell(command: String): String {
        val descriptor = instrumentation.uiAutomation.executeShellCommand(command)
        val out = java.io.ByteArrayOutputStream()
        android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { stream ->
            stream.copyTo(out)
        }
        return out.toString()
    }
}
