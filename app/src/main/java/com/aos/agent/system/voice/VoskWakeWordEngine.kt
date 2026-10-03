package com.aos.agent.system.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import com.aos.agent.core.voice.WakeError
import com.aos.agent.core.voice.WakeWordEngine
import com.aos.agent.core.voice.WakeWordMatcher
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model
import org.vosk.Recognizer

/**
 * Vosk 离线唤醒词引擎。
 *
 * 判定全在本地：音频只进这块内存缓冲做声学匹配，不落盘、不进识别服务、不进 Agent；
 * 命中后只交出一个"叫到我了吗"的事件，唤醒词那半句音频随缓冲丢弃。
 *
 * 语法收死成 `["你好副驾", "[unk]"]`：开放语法下中文小模型会把相近音节都转成词，
 * 误唤醒会多到不能用；收死后非唤醒词一律 `[unk]`。
 *
 * 循环跑在 IO 线程，回调统一切回主线程（唤醒要拉起界面）。唤醒后不退出，靠冷却窗防连发。
 */
class VoskWakeWordEngine(
    context: Context,
    private val modelPath: String,
    private val keyword: String,
    private val scope: CoroutineScope,
) : WakeWordEngine {

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val running = AtomicBoolean(false)
    private var job: Job? = null
    private var lastWakeAt = 0L

    override val available: Boolean
        get() = nativeUsable && File(modelPath).isDirectory && micPermissionGranted()

    override fun start(onWake: () -> Unit, onError: (WakeError) -> Unit) {
        if (!running.compareAndSet(false, true)) return
        if (!micPermissionGranted()) {
            running.set(false)
            mainHandler.post { onError(WakeError.NO_MIC_PERMISSION) }
            return
        }
        if (!File(modelPath).isDirectory) {
            running.set(false)
            mainHandler.post { onError(WakeError.MODEL_MISSING) }
            return
        }
        job = scope.launch(Dispatchers.IO) { listenLoop(onWake, onError) }
    }

    override fun stop() {
        running.set(false)
        job?.cancel()
        job = null
    }

    override fun release() {
        stop()
    }

    private fun listenLoop(onWake: () -> Unit, onError: (WakeError) -> Unit) {
        var model: Model? = null
        var recognizer: Recognizer? = null
        var record: AudioRecord? = null
        try {
            model = Model(modelPath)
            recognizer = Recognizer(model, SAMPLE_RATE, grammar())
            record = newAudioRecord()
            if (record.state != AudioRecord.STATE_INITIALIZED) throw IllegalStateException("麦克风不可用")
            record.startRecording()
            if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                throw IllegalStateException("麦克风被占用")
            }
            val buffer = ShortArray(FRAMES_PER_CHUNK)
            while (running.get()) {
                val read = record.read(buffer, 0, buffer.size)
                if (read <= 0) continue
                if (!recognizer.acceptWaveForm(buffer, read)) continue
                val text = WakeWordMatcher.textOf(recognizer.result)
                recognizer.reset()
                if (!WakeWordMatcher.isWake(text, keyword)) continue
                val now = SystemClock.elapsedRealtime()
                if (now - lastWakeAt < WAKE_COOLDOWN_MS) continue
                lastWakeAt = now
                mainHandler.post(onWake)
            }
        } catch (error: Throwable) {
            // 原生层抛的不一定是 Exception（UnsatisfiedLinkError 也在这一层），统一降级成错误码
            Log.w(TAG, "唤醒循环结束：${error.javaClass.simpleName} ${error.message}")
            mainHandler.post { onError(mapFailure(error)) }
        } finally {
            runCatching { record?.stop() }
            runCatching { record?.release() }
            runCatching { recognizer?.close() }
            runCatching { model?.close() }
            running.set(false)
        }
    }

    private fun newAudioRecord(): AudioRecord {
        val minBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE.toInt(),
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        ).coerceAtLeast(FRAMES_PER_CHUNK * BYTES_PER_FRAME)
        return AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE.toInt(),
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minBuffer * BUFFERS,
        )
    }

    /** 受限语法是 JSON 字符串；词表来自资源，这里只兜住引号/反斜杠把语法打烂的情况。 */
    private fun grammar(): String {
        val safe = keyword.replace("\\", "").replace("\"", "")
        return "[\"$safe\", \"$UNKNOWN_TOKEN\"]"
    }

    private fun micPermissionGranted(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    private fun mapFailure(error: Throwable): WakeError = when {
        error is SecurityException -> WakeError.NO_MIC_PERMISSION
        error is UnsatisfiedLinkError -> WakeError.NATIVE_UNAVAILABLE
        error.message.orEmpty().contains("占用") ||
            error.message.orEmpty().contains("in use", ignoreCase = true) -> WakeError.AUDIO_IN_USE
        else -> WakeError.GENERIC
    }

    private companion object {
        const val TAG = "AOSWakeWord"
        const val SAMPLE_RATE = 16_000f
        const val FRAMES_PER_CHUNK = 3_200
        const val BYTES_PER_FRAME = 2
        const val BUFFERS = 2
        const val UNKNOWN_TOKEN = "[unk]"

        /** 一次唤醒后这段时间内不再重复触发，防止尾音把界面反复拉起。 */
        const val WAKE_COOLDOWN_MS = 2_000L
    }
}

/** 原生库可用性：ABI 不匹配时 `LibVosk` 的静态初始化就会失败，只判一次。 */
private val nativeUsable: Boolean = runCatching {
    LibVosk.setLogLevel(LogLevel.WARNINGS)
    true
}.getOrDefault(false)
