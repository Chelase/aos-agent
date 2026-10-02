package com.aos.agent.data.store

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.themeStore: DataStore<Preferences> by preferencesDataStore(name = "theme_prefs")

/**
 * 日间/夜间主题偏好的持久化（design.md §6.2 / §9）。
 *
 * 主题只有一份真相：存应用私有 DataStore，禁止另建缓存。
 * 默认日间（浅色）；写入后由调用方触发 Activity 重建完成全局换装。
 */
class ThemeStore(
    private val context: Context,
) {

    /** 同步读取，仅供 Activity onCreate 在 setContentView 前选窗口背景样式；UI 层请走 [isDark]。 */
    fun isDarkBlocking(): Boolean = runBlockingRead()

    suspend fun isDark(): Boolean = context.themeStore.data.first()[KEY_DARK_THEME] ?: DEFAULT_DARK

    suspend fun saveDark(dark: Boolean) {
        context.themeStore.edit { it[KEY_DARK_THEME] = dark }
    }

    private fun runBlockingRead(): Boolean =
        kotlinx.coroutines.runBlocking { isDark() }

    private companion object {
        val KEY_DARK_THEME = booleanPreferencesKey("dark_theme")

        /** 默认日间（浅色）——用户钉定的主脸。 */
        const val DEFAULT_DARK = false
    }
}
