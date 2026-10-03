package com.aos.agent.system.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import com.aos.agent.core.update.Checksum
import com.aos.agent.core.update.InstallGates
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 下载升级包并交给系统安装器。
 *
 * 三道硬门：地址必须在官方白名单内 → 下载完必须逐位对 SHA-256 → 才允许拉起安装器。
 * 校验不过就把文件删掉，绝不让一个对不上号的 APK 停在缓存里被人误装。
 * 装不了的情况一律回给界面去说明原因，这里不静默。
 */
class ApkInstaller(
    context: Context,
    private val client: OkHttpClient = OkHttpClient(),
) {

    private val appContext = context.applicationContext

    enum class DownloadResult { DOWNLOADED, UNTRUSTED_URL, CHECKSUM_MISMATCH, NETWORK, NO_SPACE }

    /** 下载到缓存目录并校验；成功时返回文件。 */
    suspend fun download(url: String, expectedSha256: String, onProgress: (Int) -> Unit): DownloadOutcome =
        withContext(Dispatchers.IO) {
            if (InstallGates.refuseUntrusted(url)) {
                return@withContext DownloadOutcome(DownloadResult.UNTRUSTED_URL, null)
            }
            val target = File(updateDir(), fileNameOf(url))
            runCatching {
                target.parentFile?.mkdirs()
                target.delete()
                val request = Request.Builder().url(url).build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw java.io.IOException("HTTP ${response.code}")
                    val body = response.body ?: throw java.io.IOException("空响应")
                    val total = body.contentLength().takeIf { it > 0 }
                    var lastPercent = -1
                    body.byteStream().use { input ->
                        FileOutputStream(target).use { output ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE shl 2)
                            var written = 0L
                            while (true) {
                                ensureActive()
                                val read = input.read(buffer)
                                if (read < 0) break
                                output.write(buffer, 0, read)
                                written += read
                                if (total != null) {
                                    val percent = ((written * 100) / total).toInt().coerceIn(0, 100)
                                    if (percent != lastPercent) {
                                        lastPercent = percent
                                        onProgress(percent)
                                    }
                                }
                            }
                        }
                    }
                }
                // 校验要重开流：上面那条已经到底了
                if (!Checksum.matches(target.inputStream(), expectedSha256)) {
                    target.delete()
                    return@withContext DownloadOutcome(DownloadResult.CHECKSUM_MISMATCH, null)
                }
                DownloadOutcome(DownloadResult.DOWNLOADED, target)
            }.getOrElse {
                target.delete()
                DownloadOutcome(DownloadResult.NETWORK, null)
            }
        }

    /** 系统是否允许本应用安装未知来源应用。 */
    fun canRequestInstalls(): Boolean = runCatching {
        appContext.packageManager.canRequestPackageInstalls()
    }.getOrDefault(false)

    /** 这台机器上有没有能处理安装意图的程序（量产车机镜像常没有）。 */
    fun installerPresent(): Boolean = runCatching {
        appContext.packageManager.resolveActivity(installIntent(dummyUri()), 0) != null
    }.getOrDefault(false)

    /** 拉起系统安装器；返回是否真的发出去了。 */
    fun launchInstall(file: File): Boolean = runCatching {
        appContext.startActivity(installIntent(FileProvider.getUriForFile(appContext, authority(), file)))
        true
    }.getOrDefault(false)

    /** 跳到"允许安装未知应用"的设置页，让用户自己开。 */
    fun openInstallPermissionSettings() {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
            data = Uri.parse("package:${appContext.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { appContext.startActivity(intent) }
    }

    private fun installIntent(uri: Uri): Intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, APK_MIME)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private fun dummyUri(): Uri = Uri.parse("content://${authority()}/install-probe")

    private fun authority() = "${appContext.packageName}.fileprovider"

    private fun updateDir() = File(appContext.cacheDir, UPDATE_DIR)

    /**
     * 文件名来自远端清单，必须剥掉路径成分——
     * 否则一个 `../../x.apk` 就能把文件写到缓存目录之外。
     */
    private fun fileNameOf(url: String): String {
        val leaf = url.substringBefore('?').substringAfterLast('/')
        val safe = leaf.filter { it.isLetterOrDigit() || it in "._-" }
        return if (safe.endsWith(".apk") && safe.length > 4) safe else "update.apk"
    }

    data class DownloadOutcome(val result: DownloadResult, val file: File?)

    private companion object {
        const val UPDATE_DIR = "update"
        const val APK_MIME = "application/vnd.android.package-archive"
    }
}
