package com.aos.agent.system.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.aos.agent.core.voice.SpeechSynthesizer

/**
 * 系统 [TextToSpeech] 封装 + 助手音轨焦点。
 *
 * 播报走 `USAGE_ASSISTANT` + `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`：播报时媒体/导航自动压低，
 * 播完立即放弃焦点交还。焦点对象必须成对复用——每次新建 request 会让 abandon 失效。
 * 引擎初始化是异步的，就绪前的请求只保留最新一条（不堆旧答案）。
 */
class AndroidSpeechSynthesizer(context: Context) : SpeechSynthesizer {

    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(AudioManager::class.java)
    private val mainHandler = Handler(Looper.getMainLooper())

    private var tts: TextToSpeech? = null
    private var ready = false
    private var pending: Pair<String, () -> Unit>? = null
    private var currentDone: (() -> Unit)? = null
    private var focusRequest: AudioFocusRequest? = null

    override val available: Boolean get() = ready

    init {
        tts = TextToSpeech(appContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (!ready) {
                finishCurrent()
                return@TextToSpeech
            }
            pending?.let { (text, onDone) ->
                pending = null
                speakNow(text, onDone)
            }
        }
    }

    override fun speak(text: String, onDone: () -> Unit) {
        if (text.isBlank()) {
            onDone()
            return
        }
        if (!ready) {
            pending = text to onDone
            return
        }
        speakNow(text, onDone)
    }

    /** 打断：立即停播并回调完成，让状态机可以继续回到聆听。 */
    override fun stop() {
        runCatching { tts?.stop() }
        finishCurrent()
    }

    override fun release() {
        runCatching { tts?.shutdown() }
        tts = null
        ready = false
        finishCurrent()
    }

    private fun speakNow(text: String, onDone: () -> Unit) {
        val engine = tts ?: run { onDone(); return }
        requestFocus()
        currentDone = onDone
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) = Unit

            override fun onDone(id: String?) = completeOnMain(onDone)

            @Deprecated("系统接口要求保留该重载")
            override fun onError(id: String?) = completeOnMain(onDone)

            override fun onStop(id: String?, cancelled: Boolean) = completeOnMain(onDone)
        })
        val result = runCatching {
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, text.hashCode().toString())
        }.getOrDefault(TextToSpeech.ERROR)
        // speak 未成功排队时不会有任何进度回调，兜底完成，避免状态机卡在播报态
        if (result != TextToSpeech.SUCCESS) completeOnMain(onDone)
    }

    private fun completeOnMain(onDone: () -> Unit) {
        mainHandler.post {
            if (currentDone === onDone || currentDone == null) {
                currentDone = null
                abandonFocus()
                onDone()
            }
        }
    }

    private fun finishCurrent() {
        val onDone = currentDone
        currentDone = null
        abandonFocus()
        mainHandler.post { onDone?.invoke() }
    }

    private fun requestFocus() {
        if (focusRequest != null) return
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setWillPauseWhenDucked(false)
            .setOnAudioFocusChangeListener { }
            .build()
        focusRequest = request
        runCatching { audioManager?.requestAudioFocus(request) }
    }

    private fun abandonFocus() {
        val request = focusRequest ?: return
        focusRequest = null
        runCatching { audioManager?.abandonAudioFocusRequest(request) }
    }
}
