package com.aos.agent.core.tools

import com.aos.agent.core.web.BrowserSession
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 浏览器工具的行为契约。
 *
 * 重点验证三件事：
 * - **降级**：页面没开、还在加载时给结构化状态，不返回空内容冒充成功；
 * - **权限分级**：只读动作放行，改状态的走人工确认；
 * - **如实转述**：脚本失败时把原因带给模型，不吞掉。
 */
class BrowserToolTest {

    /** 假会话：按脚本内容返回预置结果，并记录收到的脚本。 */
    private class FakeSession(
        private val response: String = """{"status":"ok","url":"https://a.com","title":"T","content":"正文"}""",
        override val isAttached: Boolean = true,
        override val isLoading: Boolean = false,
    ) : BrowserSession {
        val scripts = mutableListOf<String>()

        override fun currentUrl(): String? = "https://a.com"
        override fun currentTitle(): String? = "T"

        override suspend fun evaluate(script: String, timeoutMillis: Long): BrowserSession.JsOutcome {
            scripts += script
            return BrowserSession.JsOutcome.Ok(response)
        }
    }

    private fun args(vararg pairs: Pair<String, String>): kotlinx.serialization.json.JsonObject =
        buildJsonObject { pairs.forEach { (k, v) -> put(k, v) } }

    // ------------------------------------------------------------ browser_read

    @Test
    fun readReturnsPageContentWhenSessionIsOpen() = runTest {
        val session = FakeSession()
        val result = BrowserReadTool { session }.execute(args())
        val fields = (result as ToolResult.Ok).fields
        assertEquals("ok", fields["status"]?.jsonPrimitive?.content)
        assertEquals("正文", fields["content"]?.jsonPrimitive?.content)
        assertEquals("https://a.com", fields["url"]?.jsonPrimitive?.content)
    }

    @Test
    fun readDegradesWhenNoSessionIsOpen() = runTest {
        val result = BrowserReadTool { null }.execute(args())
        val fields = (result as ToolResult.Ok).fields
        // 没开浏览器是"没法读"，不是"读到空内容"
        assertEquals("no_session", fields["status"]?.jsonPrimitive?.content)
        assertTrue(fields["detail"]?.jsonPrimitive?.content?.isNotBlank() == true)
    }

    @Test
    fun readReportsLoadingInsteadOfEmptyContent() = runTest {
        val session = FakeSession(isLoading = true)
        val result = BrowserReadTool { session }.execute(args())
        val fields = (result as ToolResult.Ok).fields
        assertEquals("loading", fields["status"]?.jsonPrimitive?.content)
        assertEquals("", fields["content"]?.jsonPrimitive?.content)
    }

    @Test
    fun readSurfacesScriptFailureDetail() = runTest {
        val failing = object : BrowserSession {
            override val isAttached = true
            override val isLoading = false
            override fun currentUrl(): String? = null
            override fun currentTitle(): String? = null
            override suspend fun evaluate(script: String, timeoutMillis: Long) =
                BrowserSession.JsOutcome.Failed("脚本执行超时")
        }
        val result = BrowserReadTool { failing }.execute(args())
        val fields = (result as ToolResult.Ok).fields
        assertEquals("error", fields["status"]?.jsonPrimitive?.content)
        assertEquals("脚本执行超时", fields["detail"]?.jsonPrimitive?.content)
    }

    // ------------------------------------------------------------ browser_act

    @Test
    fun scrollAndWaitAreAutoPermission() {
        val tool = BrowserActTool()
        assertEquals(ToolPermission.Auto, tool.permissionFor(args("action" to "scroll")))
        assertEquals(ToolPermission.Auto, tool.permissionFor(args("action" to "wait")))
    }

    @Test
    fun clickAndFillRequireConfirmation() {
        val tool = BrowserActTool()
        assertEquals(ToolPermission.Ask, tool.permissionFor(args("action" to "click")))
        assertEquals(ToolPermission.Ask, tool.permissionFor(args("action" to "fill")))
        // 未知动作按最严处理
        assertEquals(ToolPermission.Ask, tool.permissionFor(args("action" to "delete")))
    }

    @Test
    fun unknownActionIsRejectedWithAvailableList() = runTest {
        val session = FakeSession()
        val result = BrowserActTool { session }.execute(args("action" to "delete"))
        assertTrue(result is ToolResult.Error)
        assertTrue((result as ToolResult.Error).cause.contains("scroll"))
    }

    @Test
    fun clickRequiresSelectorOrText() = runTest {
        val session = FakeSession()
        val result = BrowserActTool { session }.execute(args("action" to "click"))
        assertTrue(result is ToolResult.Error)
    }

    @Test
    fun fillRequiresTextValue() = runTest {
        val session = FakeSession()
        val result = BrowserActTool { session }.execute(args("action" to "fill", "selector" to "#q"))
        assertTrue(result is ToolResult.Error)
    }

    @Test
    fun clickSendsScriptAndReportsSuccess() = runTest {
        val session = FakeSession("""{"status":"ok","tag":"BUTTON","label":"登录"}""")
        val result = BrowserActTool { session }.execute(args("action" to "click", "text" to "登录"))
        val fields = (result as ToolResult.Ok).fields
        assertEquals("ok", fields["status"]?.jsonPrimitive?.content)
        assertEquals("BUTTON", fields["tag"]?.jsonPrimitive?.content)
        assertTrue(session.scripts.single().contains("登录"))
    }

    @Test
    fun clickReportsNotFoundDistinctly() = runTest {
        val session = FakeSession("""{"status":"not_found"}""")
        val result = BrowserActTool { session }.execute(args("action" to "click", "text" to "不存在"))
        val fields = (result as ToolResult.Ok).fields
        assertEquals("not_found", fields["status"]?.jsonPrimitive?.content)
    }

    @Test
    fun actDegradesWhenNoSessionIsOpen() = runTest {
        val result = BrowserActTool { null }.execute(args("action" to "scroll", "to_y" to "100"))
        val fields = (result as ToolResult.Ok).fields
        assertEquals("no_session", fields["status"]?.jsonPrimitive?.content)
    }

    @Test
    fun waitReturnsAsSoonAsSelectorAppears() = runTest {
        var call = 0
        val session = object : BrowserSession {
            override val isAttached = true
            override val isLoading = false
            override fun currentUrl(): String? = "https://a.com"
            override fun currentTitle(): String? = null
            override suspend fun evaluate(script: String, timeoutMillis: Long):
                BrowserSession.JsOutcome {
                call++
                // 前两次没命中，第三次命中
                val body = if (call >= 3) {
                    """{"status":"ok","found":true}"""
                } else {
                    """{"status":"not_found","found":false}"""
                }
                return BrowserSession.JsOutcome.Ok(body)
            }
        }
        val result = BrowserActTool { session }.execute(
            args("action" to "wait", "selector" to "#done", "timeout_ms" to "5000"),
        )
        val fields = (result as ToolResult.Ok).fields
        assertEquals("ok", fields["status"]?.jsonPrimitive?.content)
        assertEquals(3, call)
    }

    @Test
    fun waitTimesOutWithReadableReason() = runTest {
        val session = FakeSession("""{"status":"not_found","found":false}""")
        val result = BrowserActTool { session }.execute(
            args("action" to "wait", "selector" to "#never", "timeout_ms" to "200"),
        )
        val fields = (result as ToolResult.Ok).fields
        assertEquals("not_found", fields["status"]?.jsonPrimitive?.content)
        assertTrue(fields["detail"]?.jsonPrimitive?.content?.contains("#never") == true)
    }

    // ------------------------------------------------- 经 ToolSystem 的权限闸

    @Test
    fun clickIsRejectedWhenNoConfirmerIsInjected() = runTest {
        // 无确认器即拒（fail-closed）：车机上没有可靠的通用授权 UI
        val session = FakeSession()
        val system = ToolSystem(confirmer = null)
        system.register(BrowserActTool { session })
        val result = system.execute(
            "browser_act",
            args("action" to "click", "text" to "登录"),
        )
        assertTrue(result is ToolResult.Rejected)
        assertEquals(
            RejectionReason.ApprovalUnavailable,
            (result as ToolResult.Rejected).reason,
        )
        // 被拒时不能有任何脚本发出去
        assertTrue(session.scripts.isEmpty())
    }

    @Test
    fun fillIsRejectedWhenNoConfirmerIsInjected() = runTest {
        val session = FakeSession()
        val system = ToolSystem(confirmer = null)
        system.register(BrowserActTool { session })
        val result = system.execute(
            "browser_act",
            args("action" to "fill", "selector" to "#q", "text" to "x"),
        )
        assertTrue(result is ToolResult.Rejected)
        assertEquals(
            RejectionReason.ApprovalUnavailable,
            (result as ToolResult.Rejected).reason,
        )
        assertTrue(session.scripts.isEmpty())
    }

    @Test
    fun scrollRunsWithoutConfirmation() = runTest {
        // 只读动作不该打扰用户：没有确认器也要能执行
        val session = FakeSession("""{"status":"ok"}""")
        val system = ToolSystem(confirmer = null)
        system.register(BrowserActTool { session })
        val result = system.execute(
            "browser_act",
            args("action" to "scroll", "to_y" to "100"),
        )
        assertTrue(result is ToolResult.Ok)
        assertEquals(1, session.scripts.size)
    }

    @Test
    fun clickRunsAfterExplicitConfirmation() = runTest {
        val session = FakeSession("""{"status":"ok","tag":"BUTTON"}""")
        var confirmed = false
        val confirmer = ToolConfirmer { _, _ ->
            confirmed = true
            true
        }
        val system = ToolSystem(confirmer = confirmer)
        system.register(BrowserActTool { session })
        val result = system.execute(
            "browser_act",
            args("action" to "click", "text" to "登录"),
        )
        assertTrue(confirmed)
        assertTrue(result is ToolResult.Ok)
        assertEquals(1, session.scripts.size)
    }

    @Test
    fun readToolNeedsNoConfirmation() = runTest {
        val session = FakeSession()
        val system = ToolSystem(confirmer = null)
        system.register(BrowserReadTool { session })
        val result = system.execute("browser_read", args())
        assertTrue(result is ToolResult.Ok)
        assertEquals(1, session.scripts.size)
    }
}
