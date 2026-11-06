package com.aos.agent.ui.browser

import android.content.Intent
import android.webkit.WebView
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.aos.agent.MainActivity
import com.aos.agent.R
import com.aos.agent.core.tools.BrowserActTool
import com.aos.agent.core.tools.BrowserReadTool
import com.aos.agent.core.tools.ToolResult
import com.aos.agent.core.web.BrowserController
import com.aos.agent.core.web.BrowserScripts
import com.aos.agent.core.web.BrowserSessionHolder
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Agent 工具与浏览器页的共享会话。
 *
 * 这是 Phase 3 的核心接线问题：Agent 工具跑在 runtime 里，浏览器页跑在
 * ViewModel 里，两边必须操作**同一个 WebView 实例**。各建一个的话，
 * Agent 读到的永远是另一个空页面。
 *
 * **不依赖外网**：共享会话的契约与网络无关。页面内容用本地固定 HTML，
 * 这样测试不因网络波动而假失败。真实站点的连通性由浏览器页的用例覆盖。
 */
class BrowserSessionShareTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    private fun launch(): ActivityScenario<MainActivity> {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        return ActivityScenario.launch(Intent(context, MainActivity::class.java))
    }

    /** 建一个加载固定内容的本地会话，替掉真实网络。 */
    private fun localSession(html: String): BrowserController {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val controller = BrowserController()
        lateinit var webView: WebView
        // 页面加载是异步的：onPageFinished 之前 evaluateJavascript 返回 null。
        // 这里用 client 明确等到加载完，测试才稳定。
        val loaded = java.util.concurrent.CountDownLatch(1)
        instrumentation.runOnMainSync {
            webView = WebView(context)
            webView.settings.javaScriptEnabled = true
            webView.webViewClient = object : android.webkit.WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    loaded.countDown()
                }
            }
            webView.loadDataWithBaseURL("https://example.com/page", html, "text/html", "utf-8", null)
        }
        assertTrue("页面应在 10 秒内加载完", loaded.await(10, java.util.concurrent.TimeUnit.SECONDS))
        controller.attach(webView)
        controller.setPageInfo("https://example.com/page", "测试页")
        BrowserSessionHolder.register(controller)
        return controller
    }

    // ----------------------------------------------------- 与浏览器页的接线

    @Test
    fun sessionIsRegisteredWhileBrowserPageIsOpen() {
        launch().use {
            val context = instrumentation.targetContext
            compose.onAllNodesWithText(context.getString(R.string.nav_browser)).onFirst().performClick()
            compose.waitForIdle()

            val session = BrowserSessionHolder.current()
            assertNotNull("浏览器页打开后应登记会话", session)
            assertTrue("会话应已绑定 WebView", session!!.isAttached)
        }
    }

    @Test
    fun sessionIsUnregisteredAfterLeavingBrowserPage() {
        launch().use {
            val context = instrumentation.targetContext
            compose.onAllNodesWithText(context.getString(R.string.nav_browser)).onFirst().performClick()
            compose.waitForIdle()
            assertNotNull(BrowserSessionHolder.current())

            // 离开浏览器页：WebView 被销毁，会话必须注销，
            // 否则 Agent 工具会拿到一个已销毁的实例
            compose.onAllNodesWithText(context.getString(R.string.nav_home)).onFirst().performClick()
            compose.waitForIdle()
            assertNull(BrowserSessionHolder.current())
        }
    }

    // ------------------------------------------------------ Agent 读当前页

    @Test
    fun readToolGetsContentOfThePageUserOpened() {
        val controller = localSession(
            "<html><head><meta charset=\"utf-8\"><title>测试页</title></head>" +
                "<body><article><h1>标题在此</h1><p>这是正文的第一段。</p>" +
                "<p>这是正文的第二段。</p></article></body></html>",
        )
        try {
            val result = runBlocking { BrowserReadTool { controller }.execute(buildJsonObject { }) }
            val fields = (result as ToolResult.Ok).fields
            val status = fields["status"]?.jsonPrimitive?.content
            val detail = fields["detail"]?.jsonPrimitive?.content
            assertEquals("状态不对，detail=$detail", "ok", status)

            val content = fields["content"]?.jsonPrimitive?.content.orEmpty()
            assertTrue("读到的正文不该是空的，实际：$content", content.contains("这是正文的第一段"))
            assertEquals("https://example.com/page", fields["url"]?.jsonPrimitive?.content)
            assertEquals("测试页", fields["title"]?.jsonPrimitive?.content)
        } finally {
            BrowserSessionHolder.unregister(controller)
        }
    }

    @Test
    fun readToolTruncatesOversizedContent() {
        val controller = localSession(
            "<html><body><article><p>${"很长的内容".repeat(5000)}</p></article></body></html>",
        )
        try {
            val result = runBlocking { BrowserReadTool { controller }.execute(buildJsonObject { }) }
            val fields = (result as ToolResult.Ok).fields
            assertEquals("ok", fields["status"]?.jsonPrimitive?.content)
            assertEquals(true, fields["truncated"]?.jsonPrimitive?.content?.toBoolean())
        } finally {
            BrowserSessionHolder.unregister(controller)
        }
    }

    // ------------------------------------------------------ Agent 操作页面

    @Test
    fun actToolClicksElementByVisibleText() {
        val controller = localSession(
            "<html><body><button onclick=\"document.title='clicked'\">登录</button></body></html>",
        )
        try {
            val result = runBlocking {
                BrowserActTool { controller }.execute(
                    buildJsonObject {
                        put("action", "click")
                        put("text", "登录")
                    },
                )
            }
            val fields = (result as ToolResult.Ok).fields
            val detail = fields["detail"]?.jsonPrimitive?.content
            assertEquals("状态不对，detail=$detail", "ok", fields["status"]?.jsonPrimitive?.content)
            assertEquals("BUTTON", fields["tag"]?.jsonPrimitive?.content)
        } finally {
            BrowserSessionHolder.unregister(controller)
        }
    }

    @Test
    fun actToolFillsInputAndFrameworkSeesIt() {
        // React 风格的受控输入：直接设 value 不会触发 onChange，
        // 页面读到的还是旧值。原生 setter + 事件派发后才会更新。
        val controller = localSession(
            """
            <html><body>
              <input id="q" type="text" />
              <script>
                var q = document.getElementById('q');
                var seen = '';
                q.addEventListener('input', function(){ seen = q.value; });
                window.getSeen = function(){ return seen; };
              </script>
            </body></html>
            """.trimIndent(),
        )
        try {
            val result = runBlocking {
                BrowserActTool { controller }.execute(
                    buildJsonObject {
                        put("action", "fill")
                        put("selector", "#q")
                        put("text", "车机测试")
                    },
                )
            }
            val fields = (result as ToolResult.Ok).fields
            val detail = fields["detail"]?.jsonPrimitive?.content
            assertEquals("状态不对，detail=$detail", "ok", fields["status"]?.jsonPrimitive?.content)

            // 关键断言：页面的 input 监听真的收到了值
            val seen = runBlocking {
                controller.evaluate("String(window.getSeen ? window.getSeen() : '')", 5_000L)
            }
            assertEquals(
                "页面监听器应收到填入的值",
                "车机测试",
                (seen as com.aos.agent.core.web.BrowserSession.JsOutcome.Ok).value,
            )
        } finally {
            BrowserSessionHolder.unregister(controller)
        }
    }

    @Test
    fun actToolScrollsThePage() {
        val controller = localSession(
            "<html><body><div style='height:5000px'>长页面</div></body></html>",
        )
        try {
            val result = runBlocking {
                BrowserActTool { controller }.execute(
                    buildJsonObject {
                        put("action", "scroll")
                        put("to_y", "800")
                    },
                )
            }
            val fields = (result as ToolResult.Ok).fields
            val detail = fields["detail"]?.jsonPrimitive?.content
            assertEquals("状态不对，detail=$detail", "ok", fields["status"]?.jsonPrimitive?.content)

            val scrolled = runBlocking {
                controller.evaluate("String(window.scrollY)", 5_000L)
            }
            assertEquals(
                "页面应已滚动到指定位置",
                "800",
                (scrolled as com.aos.agent.core.web.BrowserSession.JsOutcome.Ok).value,
            )
        } finally {
            BrowserSessionHolder.unregister(controller)
        }
    }

    @Test
    fun actToolReportsNotFoundForMissingElement() {
        val controller = localSession("<html><body><p>没有按钮</p></body></html>")
        try {
            val result = runBlocking {
                BrowserActTool { controller }.execute(
                    buildJsonObject {
                        put("action", "click")
                        put("text", "不存在的按钮文字")
                    },
                )
            }
            val fields = (result as ToolResult.Ok).fields
            val detail = fields["detail"]?.jsonPrimitive?.content
            // 找不到元素不能报成功——否则 Agent 会以为已经点过了
            assertEquals("状态不对，detail=$detail", "not_found", fields["status"]?.jsonPrimitive?.content)
        } finally {
            BrowserSessionHolder.unregister(controller)
        }
    }

    @Test
    fun actToolWaitsForSelectorToAppear() {
        val controller = localSession(
            """
            <html><body>
              <script>
                setTimeout(function(){
                  var d = document.createElement('div');
                  d.id = 'later';
                  d.textContent = '稍后出现';
                  document.body.appendChild(d);
                }, 700);
              </script>
            </body></html>
            """.trimIndent(),
        )
        try {
            val result = runBlocking {
                BrowserActTool { controller }.execute(
                    buildJsonObject {
                        put("action", "wait")
                        put("selector", "#later")
                        put("timeout_ms", "5000")
                    },
                )
            }
            val fields = (result as ToolResult.Ok).fields
            val detail = fields["detail"]?.jsonPrimitive?.content
            assertEquals("状态不对，detail=$detail", "ok", fields["status"]?.jsonPrimitive?.content)
        } finally {
            BrowserSessionHolder.unregister(controller)
        }
    }

    @Test
    fun actToolReportsTimeoutForSelectorThatNeverAppears() {
        val controller = localSession("<html><body><p>空页面</p></body></html>")
        try {
            val result = runBlocking {
                BrowserActTool { controller }.execute(
                    buildJsonObject {
                        put("action", "wait")
                        put("selector", "#never")
                        put("timeout_ms", "300")
                    },
                )
            }
            val fields = (result as ToolResult.Ok).fields
            val detail = fields["detail"]?.jsonPrimitive?.content
            assertEquals("状态不对，detail=$detail", "not_found", fields["status"]?.jsonPrimitive?.content)
            assertTrue("原因应说明等的是谁：$detail", detail?.contains("#never") == true)
        } finally {
            BrowserSessionHolder.unregister(controller)
        }
    }

    /**
     * 每个脚本都要能被页面解析。
     *
     * **语法错误的脚本不会报错，只会让 evaluateJavascript 返回 null**——
     * 症状是"脚本没有执行"，与"页面没准备好"完全一样，很难区分。
     * 所以这里逐个跑一遍，拿不到结果就判定为语法错误。
     */
    @Test
    fun everyScriptParsesOnThePage() {
        val controller = localSession("<html><body><article><p>正文</p></article></body></html>")
        try {
            val scripts = listOf(
                "read" to BrowserScripts.readPage(),
                "click" to BrowserScripts.click("p", null),
                "clickByText" to BrowserScripts.click(null, "正文"),
                "fill" to BrowserScripts.fill("input", "值", null),
                "fillByLabel" to BrowserScripts.fill(null, "值", "email"),
                "scrollBySelector" to BrowserScripts.scroll("p", null),
                "scrollByPosition" to BrowserScripts.scroll(null, 100),
                "wait" to BrowserScripts.waitFor("p"),
            )
            for ((name, script) in scripts) {
                val outcome = runBlocking { controller.evaluate(script, 5_000L) }
                assertTrue(
                    "$name 脚本应能执行，实际返回：$outcome",
                    outcome is com.aos.agent.core.web.BrowserSession.JsOutcome.Ok,
                )
                val value = (outcome as com.aos.agent.core.web.BrowserSession.JsOutcome.Ok).value
                assertTrue(
                    "$name 脚本应返回可解析的 JSON，实际返回：$value",
                    value.trim().startsWith("{"),
                )
            }
        } finally {
            BrowserSessionHolder.unregister(controller)
        }
    }
}
