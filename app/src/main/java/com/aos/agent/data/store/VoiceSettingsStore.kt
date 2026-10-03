package com.aos.agent.data.store

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.aos.agent.core.voice.WakeVariant
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

private val Context.voiceStore: DataStore<Preferences> by preferencesDataStore(name = "voice_prefs")

/** 没起名字前的默认唤醒对象，保持与旧版本一致的"你好副驾"。 */
const val DEFAULT_AGENT_NAME = "副驾"

/** 语音偏好快照，供状态机同步读取。 */
data class VoiceSettings(
    val ttsEnabled: Boolean = true,
    val continuous: Boolean = false,
    /** 人声打断播报：需要设备有回声消除，默认关（否则助手会把自己听成人声）。 */
    val bargeInEnabled: Boolean = false,
    /** 离线唤醒词：默认关——常驻麦克风是隐私红线，必须用户显式开启。 */
    val wakeWordEnabled: Boolean = false,
    /** Agent 名字：唤醒词由它生成。目前唯一消费者是唤醒，将来界面要显示名字时再拆出去。 */
    val agentName: String = DEFAULT_AGENT_NAME,
    /** 勾选的唤醒说法（你好X / Hi X / 裸名）。默认只带"你好"前缀，裸名误唤醒高。 */
    val wakeVariants: Set<WakeVariant> = setOf(WakeVariant.HELLO),
)

/**
 * 语音偏好持久化：播报开关与连续对话开关。
 *
 * 只有一份真相（DataStore）；[settings] 用 Eagerly 共享，保证状态机随时读到最新值，
 * 读取异常回落默认值而不是让界面卡住。
 */
class VoiceSettingsStore(context: Context) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val settings: StateFlow<VoiceSettings> = appContext.voiceStore.data
        .catch { error ->
            // 首次读损坏的 prefs 不能让流死掉，否则语音开关永远停在默认值
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { prefs ->
            VoiceSettings(
                ttsEnabled = prefs[KEY_TTS] ?: DEFAULT_TTS,
                continuous = prefs[KEY_CONTINUOUS] ?: DEFAULT_CONTINUOUS,
                bargeInEnabled = prefs[KEY_BARGE_IN] ?: DEFAULT_BARGE_IN,
                wakeWordEnabled = prefs[KEY_WAKE] ?: DEFAULT_WAKE,
                agentName = prefs[KEY_AGENT_NAME]?.takeIf { it.isNotBlank() } ?: DEFAULT_AGENT_NAME,
                wakeVariants = prefs[KEY_WAKE_VARIANTS].toWakeVariants(),
            )
        }
        .stateIn(
            scope,
            SharingStarted.Eagerly,
            VoiceSettings(
                ttsEnabled = DEFAULT_TTS,
                continuous = DEFAULT_CONTINUOUS,
                bargeInEnabled = DEFAULT_BARGE_IN,
                wakeWordEnabled = DEFAULT_WAKE,
                agentName = DEFAULT_AGENT_NAME,
            ),
        )

    suspend fun setTtsEnabled(enabled: Boolean) {
        appContext.voiceStore.edit { it[KEY_TTS] = enabled }
    }

    suspend fun setContinuous(enabled: Boolean) {
        appContext.voiceStore.edit { it[KEY_CONTINUOUS] = enabled }
    }

    suspend fun setBargeInEnabled(enabled: Boolean) {
        appContext.voiceStore.edit { it[KEY_BARGE_IN] = enabled }
    }

    suspend fun setWakeWordEnabled(enabled: Boolean) {
        appContext.voiceStore.edit { it[KEY_WAKE] = enabled }
    }

    suspend fun setAgentName(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        appContext.voiceStore.edit { it[KEY_AGENT_NAME] = trimmed }
    }

    suspend fun setWakeVariants(variants: Set<WakeVariant>) {
        // 一个都不留会让唤醒永远叫不应，兜底回默认的"你好X"
        val value = variants.ifEmpty { setOf(WakeVariant.HELLO) }
        appContext.voiceStore.edit { it[KEY_WAKE_VARIANTS] = value.map { it.name }.toSet() }
    }

    private companion object {
        val KEY_TTS = booleanPreferencesKey("tts_enabled")
        val KEY_CONTINUOUS = booleanPreferencesKey("continuous")
        val KEY_BARGE_IN = booleanPreferencesKey("barge_in_enabled")
        val KEY_WAKE = booleanPreferencesKey("wake_word_enabled")
        val KEY_AGENT_NAME = stringPreferencesKey("agent_name")
        val KEY_WAKE_VARIANTS = stringSetPreferencesKey("wake_variants")
        const val DEFAULT_TTS = true
        const val DEFAULT_CONTINUOUS = false
        const val DEFAULT_BARGE_IN = false
        const val DEFAULT_WAKE = false
    }
}

/** 存的是枚举名字符串；读到脏值（改名/手改 prefs）就当没有，不让一个坏值毁掉整条流。 */
private fun Set<String>?.toWakeVariants(): Set<WakeVariant> {
    if (this == null) return setOf(WakeVariant.HELLO)
    val parsed = mapNotNull { name -> WakeVariant.entries.firstOrNull { it.name == name } }.toSet()
    return parsed.ifEmpty { setOf(WakeVariant.HELLO) }
}
