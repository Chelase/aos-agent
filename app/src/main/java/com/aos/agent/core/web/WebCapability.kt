package com.aos.agent.core.web

import android.content.Context
import android.webkit.WebView

/**
 * WebView provider 可用性探测。
 *
 * **判定方式就是"真的建一个"**——这看起来重，但比任何查包名/反射的办法都可靠：
 *
 * - 厂商的 WebView provider 包名各不相同（Google / 三星 / 小米 / 华为各一套），
 *   写死包名会在非 Google 设备上误报"不可用"；
 * - 查 `android.webkit.WebViewFactory` 是 hidden API，反射在不同 ROM 上行为不一致；
 * - provider 包"装了但运行时缺失"的情况只有真建一次才能发现。
 *
 * 代价是几十 MB 内存和几百毫秒。但这一步只在浏览器页真正打开时发生一次，
 * 换来的是"能用就说能用，不能用就说明确原因"——符合产品承诺。
 */
object WebCapability {

    sealed interface Availability {
        data object Available : Availability
        data class Unavailable(val reason: String) : Availability
    }

    /**
     * 探测当前设备能否创建 WebView。
     *
     * **必须在主线程调用**——WebView 的构造绑定线程。
     *
     * 探测成功时会把实例化的 WebView 交回，避免调用方再建一次：
     * 探测用的实例和真正用的实例是两个 Chromium 运行时，浪费内存。
     */
    fun probe(context: Context): ProbeResult {
        return try {
            val created = WebView(context)
            ProbeResult(Availability.Available, created)
        } catch (error: Throwable) {
            ProbeResult(
                Availability.Unavailable(
                    "本机无法创建 WebView（${error.javaClass.simpleName}: ${error.message ?: "无详情"}）",
                ),
                null,
            )
        }
    }

    /**
     * 探测结果。[webView] 非 null 表示探测成功且实例可直接使用。
     */
    data class ProbeResult(
        val availability: Availability,
        val webView: WebView?,
    )
}
