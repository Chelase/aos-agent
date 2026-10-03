package com.aos.agent.core.update

import java.io.IOException
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Test
import org.junit.Assert.assertEquals

/**
 * 网络层判定测试：用 OkHttp 拦截器直接回一个假响应，不打真网络。
 *
 * 覆盖"清单正常 → 有新版/已最新"、"500 → 网络"、"404 → 未发布"、"脏 body → 清单坏"，
 * 这几条正是车机上用户会实际看到的四种回音。
 */
class UpdateCheckerTest {

    private val sha = "b".repeat(64)

    private fun checker(code: Int, body: String, localVersion: Int = 1) = HttpUpdateChecker(
        client = okhttp3.OkHttpClient.Builder()
            .addInterceptor(
                Interceptor { chain ->
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(code)
                        .message(if (code == 200) "OK" else "error")
                        .body(body.toResponseBody("application/json".toMediaType()))
                        .build()
                },
            )
            .build(),
        manifestUrl = "https://github.com/Chelase/aos-agent/releases/latest/download/update.json",
        localVersionCode = { localVersion },
    )

    private fun manifestBody(versionCode: Int) =
        """{"versionCode":$versionCode,"versionName":"1.2.3",""" +
            """"apkUrl":"https://github.com/Chelase/aos-agent/releases/download/v1.2.3/a.apk","sha256":"$sha"}"""

    @Test
    fun goodManifestWithHigherVersionOffersUpdate() = runTest {
        val outcome = checker(200, manifestBody(10203)).check()
        assertEquals(UpdateOutcome.Available::class, outcome::class)
        assertEquals("1.2.3", (outcome as UpdateOutcome.Available).manifest.versionName)
    }

    @Test
    fun goodManifestWithSameVersionSaysUpToDate() = runTest {
        assertEquals(UpdateOutcome.UpToDate, checker(200, manifestBody(1), localVersion = 1).check())
    }

    @Test
    fun serverErrorReportsNetworkFailure() = runTest {
        assertEquals(
            UpdateOutcome.Failed(UpdateFailure.NETWORK),
            checker(500, "boom").check(),
        )
    }

    @Test
    fun missingReleaseReportsBadManifestNotNetwork() = runTest {
        assertEquals(
            UpdateOutcome.Failed(UpdateFailure.BAD_MANIFEST),
            checker(404, "<html>not found</html>").check(),
        )
    }

    @Test
    fun unreadableBodyReportsBadManifest() = runTest {
        assertEquals(
            UpdateOutcome.Failed(UpdateFailure.BAD_MANIFEST),
            checker(200, "<html>proxy intercepted me</html>").check(),
        )
    }

    @Test
    fun unreachableHostReportsNetworkFailure() = runTest {
        val failing = HttpUpdateChecker(
            client = okhttp3.OkHttpClient.Builder()
                .addInterceptor(Interceptor { throw IOException("connect failed") })
                .build(),
            localVersionCode = { 1 },
        )
        assertEquals(UpdateOutcome.Failed(UpdateFailure.NETWORK), failing.check())
    }
}
