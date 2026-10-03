package com.aos.agent.system.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import com.aos.agent.core.voice.VoiceFocusHandle

/**
 * 会话级音频焦点（[VoiceFocusHandle] 的 Android 实现）。
 *
 * 用 `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK` + `USAGE_ASSISTANT`：会话期间媒体压低但不停，
 * 导航播报这类更高优先级请求进来时收到 LOSS 回调，回路挂起；它说完系统回 GAIN，回路继续。
 * 焦点对象必须成对复用——每次新建 request 会让 abandon 失效（Phase A 实测过）。
 */
class AndroidVoiceFocus(context: Context) : VoiceFocusHandle {

    private val audioManager = context.applicationContext.getSystemService(AudioManager::class.java)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var request: AudioFocusRequest? = null

    override fun claim(onLost: () -> Unit, onGained: () -> Unit): Boolean {
        if (request != null) return true
        val listener = AudioManager.OnAudioFocusChangeListener { change ->
            when (change) {
                AudioManager.AUDIOFOCUS_LOSS,
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK,
                -> mainHandler.post(onLost)

                AudioManager.AUDIOFOCUS_GAIN -> mainHandler.post(onGained)
                else -> Unit
            }
        }
        val built = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setWillPauseWhenDucked(false)
            .setOnAudioFocusChangeListener(listener)
            .build()
        // request 内部持有 listener，所以只留 request 就够，不会被回收成弱引用监听
        request = built
        val result = runCatching { audioManager?.requestAudioFocus(built) }.getOrNull()
        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    override fun release() {
        val held = request ?: return
        request = null
        runCatching { audioManager?.abandonAudioFocusRequest(held) }
    }
}
