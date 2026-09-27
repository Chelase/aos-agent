package com.aos.agent.core.tools

import com.aos.agent.system.SystemInfoReader
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * 系统信息工具。复用 Batch 0 的 `SystemInfoReader`，不重复实现读取。
 *
 * 读取层用 `"Unavailable"` 字符串表达"拿不到"（那是给面板看的）；
 * 工具结果面向模型，所以在这里换成结构化的 `status`。
 */
class SystemInfoTool(
    private val reader: SystemInfoReader,
) : Tool {
    override val name: String = "system_info"
    override val version: Int = 1
    override val category: String = "system"
    override val permission: ToolPermission = ToolPermission.Auto

    override val description: String =
        "读取车机系统信息：android_version、sdk_int、manufacturer、brand、model、device、is_automotive、network_transport。无需参数，全部只读。"

    override val inputSchema: JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject { })
    }

    override suspend fun execute(args: JsonObject): ToolResult = buildJsonObject {
        putText("android_version", reader.androidVersion())
        put("sdk_int", reader.sdkInt())
        putText("manufacturer", reader.manufacturer())
        putText("brand", reader.brand())
        putText("model", reader.model())
        putText("device", reader.device())
        put("is_automotive", reader.isAutomotive())
        put("network_transport", reader.networkTransport().name.lowercase())
    }.let { ToolResult.Ok(it) }

    private fun kotlinx.serialization.json.JsonObjectBuilder.putText(key: String, value: String) {
        if (value.isBlank() || value == UNAVAILABLE) {
            put(key, degradedField(DegradeStatus.Unsupported, "系统未提供该信息"))
        } else {
            put(key, value)
        }
    }

    private companion object {
        const val UNAVAILABLE = "Unavailable"
    }
}
