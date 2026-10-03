package com.aos.agent.system.voice

import android.content.Context
import android.util.Log
import androidx.annotation.StringRes
import com.aos.agent.R
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipInputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request

/** 唤醒模型的安装状态；界面按这个渲染，不靠猜。 */
sealed interface WakeModelState {
    /** 磁盘上没有可用模型 */
    data object Missing : WakeModelState

    /** 正在下载，[percent] 为已下载百分比（-1 表示长度未知） */
    data class Downloading(val percent: Int) : WakeModelState

    /** 已解压且校验过关键文件，可以加载 */
    data object Ready : WakeModelState

    /** 失败：[reasonRes] 必须能直接显示给用户，重试入口保持可用 */
    data class Failed(@StringRes val reasonRes: Int) : WakeModelState
}

/**
 * Vosk 中文小模型下载与解压。
 *
 * 42MB 不能打进 APK（也守不住常驻内存基线），首启按需下载。三条硬约束：
 * 1. 先下到 `.zip` 临时文件、再解压到 `.tmp` 目录、成功后整体改名——中断不会留下半套模型，
 *    否则下次 `Model()` 加载半成品会直接崩；
 * 2. 解压必须防 zip-slip（entry 名带 `../` 能写到应用目录外）；
 * 3. 就绪判据看关键文件存在，不看目录存在——空目录也算"存在"；
 */
class WakeModelInstaller(context: Context, private val scope: CoroutineScope) {

    private val appContext = context.applicationContext
    private val root = File(appContext.filesDir, MODEL_ROOT)
    private val modelDir = File(root, MODEL_DIR_NAME)
    private val client = OkHttpClient()

    private val _state = MutableStateFlow(initialState())
    val state: StateFlow<WakeModelState> = _state.asStateFlow()

    /** 已就绪时返回模型目录绝对路径，否则 null。 */
    fun readyModelPath(): String? = modelDir.absolutePath.takeIf { isModelComplete(modelDir) }

    /**
     * 按磁盘现状重算状态。
     *
     * 界面侧下载完，常驻侧那份实例并不知道；服务每次被拉起时调一下，两边才对得上。
     * 下载中不打断——那会儿状态正被进度驱动。
     */
    fun refresh() {
        if (_state.value is WakeModelState.Downloading) return
        _state.value = initialState()
    }

    fun download() {
        if (_state.value is WakeModelState.Downloading) return
        scope.launch(Dispatchers.IO) { runInstall() }
    }

    private fun initialState(): WakeModelState =
        if (readyModelPath() != null) WakeModelState.Ready else WakeModelState.Missing

    private fun runInstall() {
        _state.value = WakeModelState.Downloading(-1)
        val archive = File(root, ARCHIVE_NAME)
        val staging = File(root, "$MODEL_DIR_NAME${STAGING_SUFFIX}")
        runCatching {
            root.mkdirs()
            fetchTo(archive)
            unzipTo(staging, archive)
            // 官方包带一层同名顶层目录，直接在 staging 根上找会永远"缺关键文件"
            val located = listOf(staging, File(staging, MODEL_DIR_NAME)).firstOrNull { isModelComplete(it) }
                ?: throw IOException("解压结果缺关键文件")
            if (modelDir.exists()) modelDir.deleteRecursively()
            if (!located.renameTo(modelDir)) throw IOException("模型目录改名失败")
        }.onSuccess {
            archive.delete()
            staging.deleteRecursively()
            _state.value = WakeModelState.Ready
        }.onFailure { error ->
            // 界面只给"下载失败请重试"这种可读文案，技术原因（HTTP 码/DNS/TLS）留在这里
            Log.w(TAG, "唤醒模型安装失败", error)
            archive.delete()
            staging.deleteRecursively()
            _state.value = WakeModelState.Failed(R.string.wake_err_download_failed)
        }
    }

    private fun fetchTo(target: File) {
        val request = Request.Builder().url(MODEL_URL).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val body = response.body ?: throw IOException("空响应")
            val total = body.contentLength().takeIf { it > 0 }
            var lastPercent = -1
            body.byteStream().use { input ->
                FileOutputStream(target).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE shl 1)
                    var read = 0L
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        read += n
                        if (total != null) {
                            val percent = ((read * 100) / total).toInt().coerceIn(0, 100)
                            // 进度只按整百分点回调，避免每 8KB 触发一次重组
                            if (percent != lastPercent) {
                                lastPercent = percent
                                _state.value = WakeModelState.Downloading(percent)
                            }
                        }
                    }
                }
            }
            if (total == null) _state.value = WakeModelState.Downloading(-1)
        }
    }

    private fun unzipTo(destination: File, archive: File) {
        destination.mkdirs()
        val canonicalDestination = destination.canonicalPath
        ZipInputStream(archive.inputStream().buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val target = File(destination, entry.name)
                // zip-slip：entry 名带 ../ 会写到应用目录外，必须按规范化路径判边界
                if (!target.canonicalPath.startsWith(canonicalDestination + File.separator)) {
                    throw IOException("非法压缩包路径：${entry.name}")
                }
                if (entry.isDirectory) {
                    target.mkdirs()
                } else {
                    target.parentFile?.mkdirs()
                    target.outputStream().use { out -> zip.copyTo(out) }
                }
                zip.closeEntry()
            }
        }
    }

    private fun isModelComplete(dir: File): Boolean =
        File(dir, "am/final.mdl").isFile && File(dir, "conf/model.conf").isFile

    private companion object {
        const val TAG = "AOSWakeModel"
        const val MODEL_ROOT = "vosk"
        const val MODEL_DIR_NAME = "vosk-model-small-cn-0.22"
        const val ARCHIVE_NAME = "model.zip"
        const val STAGING_SUFFIX = ".tmp"
        const val MODEL_URL = "https://alphacephei.com/vosk/models/vosk-model-small-cn-0.22.zip"
    }
}
