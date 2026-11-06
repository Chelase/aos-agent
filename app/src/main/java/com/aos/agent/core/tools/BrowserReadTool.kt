package com.aos.agent.core.tools

import com.aos.agent.core.web.BrowserScripts
import com.aos.agent.core.web.BrowserSession
import com.aos.agent.core.web.BrowserSessionHolder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * 读当前浏览器页的正文。
 *
 * **只读**，不点不填不提交，所以 `permission = Auto`。
 *
 * 页面没打开时返回结构化降级（`status = no_session`），不抛异常——
 * 模型据此知道"是没开浏览器"还是"页面确实是空的"，两者的下一步动作不同。
 */
class BrowserReadTool(
    private val sessionProvider: () -> BrowserSession? = { BrowserSessionHolder.current() },
) : Tool {

    override val name: String = "browser_read"
    override val version: Int = 1
    override val category: String = "web"
    override val permission: ToolPermission = ToolPermission.Auto

    override val description: String =
        "读取内置浏览器当前打开页面的正文，返回页面标题、URL 与正文文本。" +
            "只能读用户已经打开的页面，不能自己打开新页面——" +
            "要看新页面请先让用户在浏览器页里打开，或用 web_search 找链接。" +
            "页面未打开或仍在加载时返回对应状态，不会返回空内容冒充成功。"

    override val inputSchema: kotlinx.serialization.json.JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            put("max_chars", buildJsonObject {
                put("type", "integer")
                put("description", "最多返回多少字符，默认 8000，上限 20000")
            })
        })
    }

    override suspend fun execute(args: kotlinx.serialization.json.JsonObject): ToolResult {
        val session = sessionProvider()
            ?: return ToolResult.Ok(
                buildJsonObject {
                    put("status", "no_session")
                    put("detail", "浏览器页没有打开。请先让用户打开内置浏览器页并加载一个页面")
                    put("content", "")
                },
            )
        if (!session.isAttached) {
            return ToolResult.Ok(
                buildJsonObject {
                    put("status", "no_session")
                    put("detail", "浏览器页没有打开")
                    put("content", "")
                },
            )
        }

        // 页面还在加载时如实报 loading——返回半截正文比明说"还在加载"更糟。
        // 但 onPageFinished 与 DOM 渲染完成之间有间隙，这段间隙里读到的是
        // 空 body。所以在宿主侧重试几次，覆盖这个间隙。
        var lastDetail = "页面尚未渲染出内容"
        var attempt = 0
        while (attempt < READ_RETRIES) {
            attempt++
            if (session.isLoading) {
                lastDetail = "页面还在加载，稍后重试"
                kotlinx.coroutines.delay(RETRY_INTERVAL_MS)
                continue
            }
            val outcome = session.evaluate(BrowserScripts.readPage(), JS_TIMEOUT_MS)
            if (outcome is BrowserSession.JsOutcome.Failed) {
                return ToolResult.Ok(
                    buildJsonObject {
                        put("status", "error")
                        put("detail", outcome.reason)
                        put("content", "")
                    },
                )
            }
            val parsed = BrowserScripts.parseRead((outcome as BrowserSession.JsOutcome.Ok).value)
            when (parsed) {
                is BrowserScripts.ReadResult.Failed -> return ToolResult.Ok(
                    buildJsonObject {
                        put("status", "error")
                        put("detail", parsed.reason)
                        put("content", "")
                    },
                )

                // 页面还在渲染：让模型知道"稍后重试"，而不是"页面是空的"
                is BrowserScripts.ReadResult.Loading -> {
                    lastDetail = parsed.reason
                    kotlinx.coroutines.delay(RETRY_INTERVAL_MS)
                }

                is BrowserScripts.ReadResult.Ok -> return ToolResult.Ok(
                    buildJsonObject {
                        put("status", "ok")
                        put("url", parsed.url)
                        put("title", parsed.title)
                        put("content", parsed.content)
                        put("truncated", parsed.truncated)
                    },
                )
            }
        }
        // 重试用完仍未就绪：如实报 loading，不返回空内容冒充成功
        return ToolResult.Ok(
            buildJsonObject {
                put("status", "loading")
                put("url", session.currentUrl().orEmpty())
                put("detail", lastDetail)
                put("content", "")
            },
        )
    }

    private companion object {
        const val JS_TIMEOUT_MS = 10_000L
        const val READ_RETRIES = 4
        const val RETRY_INTERVAL_MS = 300L
    }
}
