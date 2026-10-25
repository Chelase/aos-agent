package com.aos.agent.data.store

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.aos.agent.core.llm.LlmProfile
import com.aos.agent.core.llm.LlmProfiles
import kotlinx.coroutines.flow.first

private val Context.llmProfileStore: DataStore<Preferences> by preferencesDataStore(name = "llm_profiles")

/**
 * 模型服务配置档案的持久化（地址簿）。
 *
 * 「当前生效」的配置仍在 [LlmConfigStore]；这里只存备选档案和一个
 * 「当前配置来自哪个档案」的提示位（界面手改后提示位清空，档案列表不动）。
 */
class LlmProfileStore(
    private val context: Context,
) {

    suspend fun profiles(): List<LlmProfile> =
        LlmProfiles.decode(context.llmProfileStore.data.first()[KEY_PROFILES])

    suspend fun save(profiles: List<LlmProfile>) {
        context.llmProfileStore.edit { it[KEY_PROFILES] = LlmProfiles.encode(profiles) }
    }

    suspend fun activeProfile(): String? =
        context.llmProfileStore.data.first()[KEY_ACTIVE_PROFILE]

    suspend fun saveActiveProfile(name: String?) {
        context.llmProfileStore.edit { prefs ->
            if (name == null) prefs.remove(KEY_ACTIVE_PROFILE) else prefs[KEY_ACTIVE_PROFILE] = name
        }
    }

    private companion object {
        val KEY_PROFILES = stringPreferencesKey("profiles_json")
        val KEY_ACTIVE_PROFILE = stringPreferencesKey("active_profile")
    }
}
