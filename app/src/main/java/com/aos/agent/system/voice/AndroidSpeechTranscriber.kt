package com.aos.agent.system.voice

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.aos.agent.core.voice.SpeechTranscriber
import com.aos.agent.core.voice.VoiceError

/**
 * 系统 [SpeechRecognizer] 封装（Google 语音服务，支持流式部分结果）。
 *
 * 必须在主线程创建与调用——识别器内部绑定创建线程的 Looper。
 * 识别文本只进内存管道，不落盘不缓存。
 */
class AndroidSpeechTranscriber(context: Context) : SpeechTranscriber {

    private val appContext = context.applicationContext
    private var recognizer: SpeechRecognizer? = null
    private var listening = false

    override val available: Boolean
        get() = SpeechRecognizer.isRecognitionAvailable(appContext) ||
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).resolveActivity(appContext.packageManager) != null

    override fun start(
        onPartial: (String) -> Unit,
        onFinal: (String) -> Unit,
        onError: (VoiceError) -> Unit,
    ) {
        if (denied()) {
            onError(VoiceError.PERMISSION)
            return
        }
        if (!available) {
            onError(VoiceError.NO_SERVICE)
            return
        }
        // 每次聆听都用新实例：复用同一个 SpeechRecognizer 时，部分设备上一轮的
        // 结束态会残留，导致下一轮不再回调（实测过桥接式复用的坑）。
        runCatching { recognizer?.destroy() }
        val fresh = SpeechRecognizer.createSpeechRecognizer(appContext).also { recognizer = it }
        fresh.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                listening = true
            }

            override fun onBeginningOfSpeech() = Unit

            override fun onRmsChanged(rmsdB: Float) = Unit

            override fun onBufferReceived(buffer: ByteArray?) = Unit

            override fun onEndOfSpeech() = Unit

            override fun onError(error: Int) {
                listening = false
                onError(mapError(error))
            }

            override fun onResults(results: Bundle?) {
                listening = false
                onFinal(results.bestText().orEmpty())
            }

            override fun onPartialResults(partialResults: Bundle?) {
                onPartial(partialResults.bestText().orEmpty())
            }

            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
        fresh.startListening(recognizerIntent())
    }

    override fun stop() {
        if (listening) runCatching { recognizer?.stopListening() }
    }

    override fun cancel() {
        runCatching { recognizer?.cancel() }
        listening = false
    }

    override fun release() {
        runCatching { recognizer?.destroy() }
        recognizer = null
        listening = false
    }

    private fun denied(): Boolean =
        appContext.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED

    private fun recognizerIntent(): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        .putExtra(
            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
        )
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE, preferredLanguage())
        .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)

    /** 跟随应用当前语言（中英切换后识别语言同步切换），而不是系统语言。 */
    private fun preferredLanguage(): String =
        appContext.resources.configuration.locales[0].language

    private fun Bundle?.bestText(): String? = this
        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        ?.firstOrNull()

    private fun mapError(error: Int): VoiceError = when (error) {
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> VoiceError.TIMEOUT
        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> VoiceError.NO_MATCH
        SpeechRecognizer.ERROR_AUDIO, SpeechRecognizer.ERROR_SERVER,
        SpeechRecognizer.ERROR_CLIENT, SpeechRecognizer.ERROR_NETWORK,
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS,
        -> VoiceError.AUDIO_IN_USE
        else -> VoiceError.GENERIC
    }
}
