package com.aos.agent.core.tools

import com.aos.agent.core.web.BrowserScripts
import com.aos.agent.core.web.BrowserSession
import com.aos.agent.core.web.BrowserSessionHolder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * 操作当前浏览器页面：点按钮、填表单、滚动、等元素。
 *
 * **按动作细分权限**——这是本工具与方案初稿的一处收紧：
 *
 * | 动作 | 权限 | 理由 |
 * |---|---|---|
 * | `click` | Ask | 点按钮可能触发提交、支付、下单等真实后果 |
 * | `fill` | Ask | 填表单会改页面状态，可能带入账号信息 |
 * | `scroll` | Auto | 只改视口位置，不改任何数据 |
 * | `wait` | Auto | 只读探测 |
 *
 * 方案里写的是"整体 Ask"。收紧到按动作分级后，Agent 能自己滚动和等待元素，
 * 不必为了一次滚动去打扰用户——但任何会改状态的点击与填写仍然必须人工确认。
 *
 * `permission = Ask` 在没有确认器时**默认拒绝**（fail-closed），与现有闸一致。
 */
class BrowserActTool(
    private val sessionProvider: () -> BrowserSession? = { BrowserSessionHolder.current() },
) : Tool {

    override val name: String = "browser_act"
    override val version: Int = 1
    override val category: String = "web"
    override val permission: ToolPermission = ToolPermission.Ask

    override val description: String =
        "操作内置浏览器当前打开的页面。action 取值：" +
            "click（按 selector 或可见文本点击元素）、fill（按 selector 或字段名填写输入框）、" +
            "scroll（滚到某元素或滚到指定纵坐标）、wait（等待某选择器出现）。" +
            "click 与 fill 会改动页面状态，需要人工确认；scroll 与 wait 不改数据，直接执行。" +
            "只能操作用户已经打开的页面，不能自己打开新页面。"

    override val inputSchema: JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            put("action", buildJsonObject {
                put("type", "string")
                put("description", "click | fill | scroll | wait")
            })
            put("selector", buildJsonObject {
                put("type", "string")
                put("description", "CSS 选择器，如 button.submit、#search-input")
            })
            put("text", buildJsonObject {
                put("type", "string")
                put("description", "click 时为元素可见文本；fill 时为要填入的值")
            })
            put("label", buildJsonObject {
                put("type", "string")
                put("description", "fill 时用于按字段名/占位符定位输入框，如 email、password")
            })
            put("to_y", buildJsonObject {
                put("type", "integer")
                put("description", "scroll 时滚动到的纵坐标（像素），不给则配合 selector 滚动到元素")
            })
            put("timeout_ms", buildJsonObject {
                put("type", "integer")
                put("description", "wait 的超时毫秒数，默认 5000，上限 20000")
            })
        })
        putJsonArray("required") { add("action") }
    }

    override fun permissionFor(args: JsonObject): ToolPermission {
        // 只读动作直接放行；改状态的必须人工确认
        return when (args.string("action")?.trim()?.lowercase()) {
            "scroll", "wait" -> ToolPermission.Auto
            else -> ToolPermission.Ask
        }
    }

    override suspend fun execute(args: JsonObject): ToolResult {
        val action = args.string("action")?.trim()?.lowercase() ?: ""
        if (action.isEmpty()) return ToolResult.Error("缺少 action 参数")

        val session = sessionProvider()
            ?: return degrade("no_session", "浏览器页没有打开。请先让用户打开内置浏览器页并加载一个页面")
        if (!session.isAttached) {
            return degrade("no_session", "浏览器页没有打开")
        }

        return when (action) {
            "click" -> actClick(session, args)
            "fill" -> actFill(session, args)
            "scroll" -> actScroll(session, args)
            "wait" -> actWait(session, args)
            else -> ToolResult.Error(
                "不支持的 action：$action（可用：click、fill、scroll、wait）",
            )
        }
    }

    private suspend fun actClick(session: BrowserSession, args: JsonObject): ToolResult {
        val selector = args.string("selector")
        val text = args.string("text")
        if (selector.isNullOrBlank() && text.isNullOrBlank()) {
            return ToolResult.Error("click 需要 selector 或 text 至少一个")
        }
        val outcome = session.evaluate(
            BrowserScripts.click(selector, text),
            JS_TIMEOUT_MS,
        )
        return actOutcome(outcome, "点击")
    }

    private suspend fun actFill(session: BrowserSession, args: JsonObject): ToolResult {
        val value = args.string("text")
        if (value == null) return ToolResult.Error("fill 需要 text 参数作为要填入的值")
        val selector = args.string("selector")
        val label = args.string("label")
        if (selector.isNullOrBlank() && label.isNullOrBlank()) {
            return ToolResult.Error("fill 需要 selector 或 label 至少一个")
        }
        val outcome = session.evaluate(
            BrowserScripts.fill(selector, value, label),
            JS_TIMEOUT_MS,
        )
        return actOutcome(outcome, "填写")
    }

    private suspend fun actScroll(session: BrowserSession, args: JsonObject): ToolResult {
        val selector = args.string("selector")
        val toY = args.int("to_y")
        if (selector.isNullOrBlank() && toY == null) {
            return ToolResult.Error("scroll 需要 selector 或 to_y 至少一个")
        }
        val outcome = session.evaluate(
            BrowserScripts.scroll(selector, toY),
            JS_TIMEOUT_MS,
        )
        return actOutcome(outcome, "滚动")
    }

    /**
     * 等选择器出现。
     *
     * **轮询在宿主做，不在脚本里 await**：注入的脚本不能长时间阻塞，
     * 否则 WebView 的事件循环被卡住，页面自身也动不了。
     */
    private suspend fun actWait(session: BrowserSession, args: JsonObject): ToolResult {
        val selector = args.string("selector")?.trim().orEmpty()
        if (selector.isEmpty()) return ToolResult.Error("wait 需要 selector 参数")
        val timeout = (args.int("timeout_ms") ?: DEFAULT_WAIT_MS).coerceIn(100, MAX_WAIT_MS)
        val deadline = System.currentTimeMillis() + timeout
        val script = BrowserScripts.waitFor(selector)
        while (true) {
            when (val outcome = session.evaluate(script, JS_TIMEOUT_MS)) {
                is BrowserSession.JsOutcome.Failed -> return degrade("error", outcome.reason)
                is BrowserSession.JsOutcome.Ok -> if (BrowserScripts.parseWait(outcome.value)) {
                    return ToolResult.Ok(
                        buildJsonObject {
                            put("status", "ok")
                            put("found", true)
                            put("url", session.currentUrl().orEmpty())
                        },
                    )
                }
            }
            if (System.currentTimeMillis() >= deadline) {
                return degrade(
                    "not_found",
                    "等待 $selector 超过 ${timeout}ms 仍未出现",
                )
            }
            kotlinx.coroutines.delay(POLL_INTERVAL_MS)
        }
    }

    private fun actOutcome(
        outcome: BrowserSession.JsOutcome,
        actionName: String,
    ): ToolResult = when (outcome) {
        is BrowserSession.JsOutcome.Failed -> degrade("error", outcome.reason)
        is BrowserSession.JsOutcome.Ok -> when (val parsed = BrowserScripts.parseAct(outcome.value)) {
            is BrowserScripts.ActResult.Ok -> ToolResult.Ok(
                buildJsonObject {
                    put("status", "ok")
                    put("action", actionName)
                    parsed.tag?.let { put("tag", it) }
                    parsed.label?.let { put("label", it) }
                },
            )

            is BrowserScripts.ActResult.NotFound -> degrade("not_found", parsed.reason)
            is BrowserScripts.ActResult.Failed -> degrade("error", parsed.reason)
        }
    }

    private fun degrade(status: String, detail: String): ToolResult = ToolResult.Ok(
        buildJsonObject {
            put("status", status)
            put("detail", detail)
        },
    )

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it !is kotlinx.serialization.json.JsonNull }?.contentOrNull

    private fun JsonObject.int(key: String): Int? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.toIntOrNull()

    private companion object {
        const val JS_TIMEOUT_MS = 10_000L
        const val DEFAULT_WAIT_MS = 5_000
        const val MAX_WAIT_MS = 20_000
        const val POLL_INTERVAL_MS = 300L
    }
}
