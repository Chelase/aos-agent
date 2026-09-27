package com.aos.agent.data.store

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.aos.agent.core.llm.LlmConfig
import com.aos.agent.core.llm.LlmConfigSource
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

private val Context.llmConfigStore: DataStore<Preferences> by preferencesDataStore(name = "llm_config")

/**
 * 模型服务配置的持久化。
 *
 * 用应用私有 DataStore 而非 SharedPreferences（架构约束）；apiKey 属于敏感值，
 * 只允许出现在这一份私有存储与请求头里，日志与异常信息一律走 [LlmConfig.describe] 的掩码形式。
 */
class LlmConfigStore(
    private val context: Context,
) : LlmConfigSource {

    override suspend fun current(): LlmConfig? {
        val prefs = context.llmConfigStore.data.first()
        val baseUrl = prefs[KEY_BASE_URL] ?: return null
        val model = prefs[KEY_MODEL] ?: return null
        return LlmConfig(
            baseUrl = baseUrl,
            model = model,
            apiKey = prefs[KEY_API_KEY].orEmpty(),
            extraHeaders = decodeHeaders(prefs[KEY_HEADERS]),
        )
    }

    suspend fun save(config: LlmConfig) {
        context.llmConfigStore.edit { prefs ->
            prefs[KEY_BASE_URL] = config.baseUrl
            prefs[KEY_MODEL] = config.model
            prefs[KEY_API_KEY] = config.apiKey
            prefs[KEY_HEADERS] = encodeHeaders(config.extraHeaders)
        }
    }

    suspend fun clear() {
        context.llmConfigStore.edit { it.clear() }
    }

    private fun encodeHeaders(headers: Map<String, String>): String =
        buildJsonObject { headers.forEach { (name, value) -> put(name, value) } }.toString()

    private fun decodeHeaders(raw: String?): Map<String, String> {
        if (raw.isNullOrBlank()) return emptyMap()
        val object_ = runCatching { Json.parseToJsonElement(raw).jsonObject }.getOrNull()
            ?: return emptyMap()
        return object_.entries.mapNotNull { (key, value) ->
            (value as? JsonPrimitive)?.content?.let { key to it }
        }.toMap()
    }

    private companion object {
        val KEY_BASE_URL = stringPreferencesKey("base_url")
        val KEY_MODEL = stringPreferencesKey("model")
        val KEY_API_KEY = stringPreferencesKey("api_key")
        val KEY_HEADERS = stringPreferencesKey("extra_headers")
    }
}
