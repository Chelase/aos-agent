package com.aos.agent.data.store

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.aos.agent.core.tools.mcp.McpServerConfig
import com.aos.agent.core.tools.mcp.McpServerConfigCodec
import com.aos.agent.core.tools.mcp.McpServerConfigSource
import kotlinx.coroutines.flow.first

private val Context.mcpServerDataStore: DataStore<Preferences> by preferencesDataStore(name = "mcp_servers")

/**
 * MCP server 配置的私有持久化。
 *
 * 整个列表以一段 JSON 存在首选项里（条目数量很小，不值得建表）；
 * token 与 LLM apiKey 同级：只出现在这里与请求头，日志走 [McpServerConfig.toString] 的掩码形态。
 */
class McpServerStore(
    private val context: Context,
) : McpServerConfigSource {

    override suspend fun servers(): List<McpServerConfig> =
        McpServerConfigCodec.decode(context.mcpServerDataStore.data.first()[KEY_SERVERS])

    suspend fun save(configs: List<McpServerConfig>) {
        context.mcpServerDataStore.edit { prefs ->
            if (configs.isEmpty()) {
                prefs.remove(KEY_SERVERS)
            } else {
                prefs[KEY_SERVERS] = McpServerConfigCodec.encode(configs)
            }
        }
    }

    suspend fun clear() {
        context.mcpServerDataStore.edit { it.clear() }
    }

    private companion object {
        val KEY_SERVERS = stringPreferencesKey("servers")
    }
}
