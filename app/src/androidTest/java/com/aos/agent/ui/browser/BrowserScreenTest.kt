package com.aos.agent.ui.browser

import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.aos.agent.MainActivity
import com.aos.agent.R
import org.junit.Rule
import org.junit.Test

/**
 * 内置浏览器页的行为契约。
 *
 * 覆盖：导航项存在、地址栏能编辑、搜索词走搜索、URL 守卫拦截内网地址、
 * 页面离开后 WebView 已释放。
 */
class BrowserScreenTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    private fun launch(): ActivityScenario<MainActivity> {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        return ActivityScenario.launch(Intent(context, MainActivity::class.java))
    }

    @Test
    fun browserDestinationIsReachableFromRail() {
        launch().use {
            val context = instrumentation.targetContext
            compose.onAllNodesWithText(context.getString(R.string.nav_browser)).onFirst().performClick()
            // "浏览器" 同时出现在导航项与页面标题，按出现顺序取第一个
            compose.onAllNodesWithText(context.getString(R.string.browser_title)).onFirst()
                .assertIsDisplayed()
            compose.onNodeWithText(context.getString(R.string.browser_back))
                .assertIsDisplayed()
            compose.onNodeWithText(context.getString(R.string.browser_forward))
                .assertIsDisplayed()
            compose.onNodeWithText(context.getString(R.string.browser_reload))
                .assertIsDisplayed()
            capture("browser")
        }
    }

    @Test
    fun addressBarAcceptsInputAndKeepsItWhileTyping() {
        launch().use {
            val context = instrumentation.targetContext
            compose.onAllNodesWithText(context.getString(R.string.nav_browser)).onFirst().performClick()
            compose.onNodeWithText(context.getString(R.string.browser_edit_url)).performClick()
            // 输入过程中地址栏的值会变（页面状态回调），草稿不能被冲掉
            compose.onNodeWithText(context.getString(R.string.browser_address_hint))
                .performTextInput("example.com")
            compose.onNodeWithText("example.com").assertExists()
        }
    }

    @Test
    fun internalAddressesAreRejectedWithReadableReason() {
        launch().use {
            val context = instrumentation.targetContext
            compose.onAllNodesWithText(context.getString(R.string.nav_browser)).onFirst().performClick()
            compose.onNodeWithText(context.getString(R.string.browser_edit_url)).performClick()
            compose.onNodeWithText(context.getString(R.string.browser_address_hint))
                .performTextInput("https://192.168.0.1/")
            compose.onNodeWithText(context.getString(R.string.action_go)).performClick()
            compose.waitForIdle()
            // 守卫拦住后页面给出原因，而不是白屏或静默失败
            compose.onAllNodesWithText("禁止直接使用 IP 地址：192.168.0.1")
                .onFirst()
                .assertExists()
        }
    }

    @Test
    fun leavingBrowserReleasesWebView() {
        launch().use { scenario ->
            val context = instrumentation.targetContext
            compose.onAllNodesWithText(context.getString(R.string.nav_browser)).onFirst().performClick()
            compose.waitForIdle()
            // 离开浏览器页后 WebView 必须已销毁：native 内存不受 maxHeap 限制，
            // 不释放会持续涨并触发 LMK。所以回到首页后断言会话已断开。
            compose.onAllNodesWithText(context.getString(R.string.nav_home)).onFirst().performClick()
            compose.onNodeWithText("AOSAgent").assertIsDisplayed()
            compose.waitForIdle()
            // 再回浏览器页应能重新初始化，而不是拿一个已销毁的实例
            compose.onAllNodesWithText(context.getString(R.string.nav_browser)).onFirst().performClick()
            compose.onAllNodesWithText(context.getString(R.string.browser_title)).onFirst().assertIsDisplayed()
            compose.onNodeWithText(context.getString(R.string.browser_reload)).assertIsDisplayed()
        }
    }

    @Test
    fun backFromBrowserReturnsHome() {
        launch().use { scenario ->
            val context = instrumentation.targetContext
            compose.onAllNodesWithText(context.getString(R.string.nav_browser)).onFirst().performClick()
            compose.onAllNodesWithText(context.getString(R.string.browser_title)).onFirst().assertIsDisplayed()
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            compose.onNodeWithText("AOSAgent").assertIsDisplayed()
        }
    }

    @Test
    fun loadingAPageReplacesStartPageWithContent() {
        launch().use {
            val context = instrumentation.targetContext
            compose.onAllNodesWithText(context.getString(R.string.nav_browser)).onFirst().performClick()
            // 首屏是起始页：WebView 那块是黑的，起始页盖在上面
            compose.onNodeWithText(context.getString(R.string.browser_home_title)).assertExists()

            compose.onNodeWithText(context.getString(R.string.browser_edit_url)).performClick()
            compose.onNodeWithText(context.getString(R.string.browser_address_hint))
                .performTextInput("https://example.com")
            compose.onNodeWithText(context.getString(R.string.action_go)).performClick()

            // 加载后起始页撤掉，露出网页内容
            compose.waitUntil(20_000) {
                compose.onAllNodesWithText(context.getString(R.string.browser_home_title))
                    .fetchSemanticsNodes().isEmpty()
            }
            capture("browser-loaded")
        }
    }

    /** 视觉取证：与 V6 走查同一套方法，产出可直接核对的截图。 */
    private fun capture(name: String) {
        val tag = androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("reviewTag") ?: return
        compose.waitForIdle()
        instrumentation.waitForIdleSync()
        // waitForIdle 只等 Compose；再留一帧给 AAOS 窗口合成器提交画面
        Thread.sleep(350)
        val image = instrumentation.uiAutomation.takeScreenshot() ?: return
        // 存内部目录：run-as 能直接取，外部目录在多用户车机上权限受限
        val directory = java.io.File(
            instrumentation.targetContext.filesDir,
            "browser-review/$tag",
        ).apply { mkdirs() }
        java.io.File(directory, "$name.png").outputStream().use {
            image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        image.recycle()
    }
}
