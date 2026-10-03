package com.aos.agent.core.update

import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/** 清单地址 404：仓库还没发过版本。 */
class MissingReleaseException : IOException("release 未发布")

/** 检查更新的动作抽象；单测灌假实现，不打网络。 */
interface UpdateChecker {
    suspend fun check(): UpdateOutcome
}

/**
 * 拉官方 Release 里的 `update.json` 并判定。
 *
 * 用 `releases/latest/download/…` 这个固定别名，而不是 Releases API：
 * API 匿名限 60 次/小时还要处理认证与分页，一个静态文件最不容易坏。
 * 当前版本号由构造参数注入（Android 侧读 PackageInfo），这样纯逻辑能离线单测。
 */
class HttpUpdateChecker(
    private val client: OkHttpClient = OkHttpClient(),
    private val manifestUrl: String = MANIFEST_URL,
    private val localVersionCode: () -> Int,
) : UpdateChecker {

    override suspend fun check(): UpdateOutcome = withContext(Dispatchers.IO) {
        val json = try {
            fetch()
        } catch (missing: MissingReleaseException) {
            // 404 的真相是"还没发过版本"，不是"连不上"——文案不能混着说
            return@withContext UpdateOutcome.Failed(UpdateFailure.BAD_MANIFEST)
        } catch (network: Exception) {
            return@withContext UpdateOutcome.Failed(UpdateFailure.NETWORK)
        }

        val manifest = UpdateDecision.parse(json)
            ?: return@withContext UpdateOutcome.Failed(UpdateFailure.BAD_MANIFEST)
        UpdateDecision.decide(localVersionCode(), manifest)
    }

    private fun fetch(): String {
        val request = Request.Builder().url(manifestUrl).build()
        client.newCall(request).execute().use { response ->
            if (response.code == 404) throw MissingReleaseException()
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            return response.body?.string() ?: throw IOException("空响应")
        }
    }

    companion object {
        const val MANIFEST_URL = "https://github.com/Chelase/aos-agent/releases/latest/download/update.json"
    }
}
