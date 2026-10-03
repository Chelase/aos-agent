package com.aos.agent.data.store

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
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

/** 语音偏好快照，供状态机同步读取。 */
data class VoiceSettings(
    val ttsEnabled: Boolean = true,
    val continuous: Boolean = false,
    /** 人声打断播报：需要设备有回声消除，默认关（否则助手会把自己听成人声）。 */
    val bargeInEnabled: Boolean = false,
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
            )
        }
        .stateIn(
            scope,
            SharingStarted.Eagerly,
            VoiceSettings(DEFAULT_TTS, DEFAULT_CONTINUOUS, DEFAULT_BARGE_IN),
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

    private companion object {
        val KEY_TTS = booleanPreferencesKey("tts_enabled")
        val KEY_CONTINUOUS = booleanPreferencesKey("continuous")
        val KEY_BARGE_IN = booleanPreferencesKey("barge_in_enabled")
        const val DEFAULT_TTS = true
        const val DEFAULT_CONTINUOUS = false
        const val DEFAULT_BARGE_IN = false
    }
}
