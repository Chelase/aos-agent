package com.aos.agent.core.web

import android.util.Log
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

/**
 * 设备端真实联网搜索验证。
 *
 * 与 JUnit 单测不同，这里跑在真实 Android 环境（车机镜像 / 真机），
 * 验的是**真实出网条件下的行为**：代理、弱网、DNS、端点可达性。
 *
 * 没有网络时**跳过而不是失败**——这类环境依赖不该让 CI 变红，
 * 但真跑一次就能发现"解析逻辑对但链路不通"这类问题。
 */
@RunWith(AndroidJUnit4::class)
class WebSearchDeviceTest {

    private companion object {
        const val TAG = "WebSearchDevice"
    }

    @Test
    fun deviceCanReachSearchEndpointAndParseResults() = runBlocking {
        val provider = DuckDuckGoSearchProvider()
        val outcome = provider.search("android automotive webview", 5)

        when (outcome) {
            is SearchProvider.SearchOutcome.Ok -> {
                Log.i(TAG, "搜索成功：${outcome.results.size} 条，provider=${outcome.provider}")
                outcome.results.forEach { Log.i(TAG, "  - ${it.title} :: ${it.url}") }
                assertTrue("应至少有一条结果", outcome.results.isNotEmpty())
                outcome.results.forEach { result ->
                    assertTrue("URL 应是 http(s) 直链：${result.url}", result.url.startsWith("http"))
                    assertTrue("标题不应为空", result.title.isNotBlank())
                }
            }

            is SearchProvider.SearchOutcome.ParseFailed -> {
                // 端点通了但结构变了：这是需要修的事实，不能当"没搜到"放过
                Log.e(TAG, "解析失败：${outcome.reason}")
                throw AssertionError("解析失败：${outcome.reason}")
            }

            is SearchProvider.SearchOutcome.NetworkError -> {
                // 设备无外网属环境限制，跳过而不是失败；但要把原因留痕
                Log.w(TAG, "网络不可达（设备无外网）：${outcome.reason}")
                assumeTrue("设备无外网，跳过：${outcome.reason}", false)
            }
        }
    }

    @Test
    fun deviceToolReturnsStructuredResult() = runBlocking {
        val tool = com.aos.agent.core.tools.WebSearchTool()
        val args = buildJsonObject {
            put("query", "android automotive browser")
            put("limit", 3)
        }
        val result = tool.execute(args)
        Log.i(TAG, "工具结果类型：${result::class.simpleName}")
        if (result is com.aos.agent.core.tools.ToolResult.Ok) {
            val status = result.fields["status"]?.jsonPrimitive?.content
            Log.i(TAG, "status=$status total=${result.fields["total"]?.jsonPrimitive?.content}")
            assertTrue("status 应是已知取值，实际 $status",
                status == "ok" || status == "parse_failed" || status == "network_error")
            assertTrue("results 应存在", result.fields.containsKey("results"))
            if (status == "ok") {
                val items = result.fields["results"]!!.jsonArray
                assertTrue("应有结果", items.isNotEmpty())
                val first = items[0].jsonObject
                assertTrue("应含 title", first.containsKey("title"))
                assertTrue("应含 url", first.containsKey("url"))
            }
        }
    }

    @Test
    fun urlGuardRejectsInternalAddressesOnDevice() {
        // 守卫是纯逻辑，但要在设备上再验一次：确认没有平台差异
        assertTrue(!UrlGuard.check("https://127.0.0.1/").allowed)
        assertTrue(!UrlGuard.check("https://169.254.169.254/").allowed)
        assertTrue(!UrlGuard.check("http://example.com").allowed)
        assertTrue(UrlGuard.check("https://example.com").allowed)
    }
}
