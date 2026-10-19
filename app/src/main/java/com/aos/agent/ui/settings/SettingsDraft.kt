package com.aos.agent.ui.settings

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 尚未保存的设置表单，归当前应用 UI 会话持有。
 * API Key 只留内存，不进入 Activity 的保存状态、日志或另一份持久化存储。
 */
@Stable
class SettingsDraft {
    var baseUrl by mutableStateOf("")
    var model by mutableStateOf("")
    var apiKey by mutableStateOf("")
    var mcpName by mutableStateOf("fixture")
    var mcpUrl by mutableStateOf("http://localhost:9101/mcp")
    var hint by mutableStateOf<String?>(null)
    var agentNameDraft by mutableStateOf("")
    var showKey by mutableStateOf(false)
    internal var modelDraftLoaded = false
    internal var modelDraftEdited = false
    internal var agentNameEdited = false
}
