package com.aos.agent.core.tools

import com.aos.agent.system.vehicle.VehiclePropertySpec
import com.aos.agent.system.vehicle.VehiclePropertyAllowlist
import com.aos.agent.system.vehicle.VehicleReading
import com.aos.agent.system.vehicle.VehicleReader
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * 唯一的车辆只读工具。
 *
 * 刻意不做成 CarToolForge 那种按数据类型泛型展开的 `getStringProperty` /
 * `setIntProperty` 工具族——那会把"选哪个工具"的负担压给模型（他们自述需要
 * >20B 模型才好用）。可用字段完全由 allowlist 决定，改字段不改代码。
 */
class VehicleBasicTool(
    private val allowlist: VehiclePropertyAllowlist,
    private val reader: VehicleReader,
) : Tool {
    override val name: String = "vehicle_basic"
    override val version: Int = 1
    override val category: String = "car-property"
    override val permission: ToolPermission = ToolPermission.Auto

    override val description: String = buildString {
        append("读取车辆只读状态，返回带单位的结构化字段。当前可用字段：")
        if (allowlist.readable.isEmpty()) {
            append("（无）")
        } else {
            allowlist.readable.forEach { spec ->
                append(spec.name)
                append("[")
                append(spec.unit ?: "无单位")
                append("] ")
            }
        }
        append("读不到的字段会返回 {status, detail}，不要把缺失当成 0 或空。")
    }

    override val inputSchema: JsonObject = buildJsonObject {
        put("type", "object")
        put(
            "properties",
            buildJsonObject {
                put(
                    "fields",
                    buildJsonObject {
                        put("type", "array")
                        put("description", "可选；省略则返回全部可用字段。可选值：${allowlist.names.sorted()}")
                        put("items", buildJsonObject { put("type", "string") })
                    },
                )
            },
        )
    }

    override suspend fun execute(args: JsonObject): ToolResult {
        val fields = buildJsonObject {
            requestedNames(args).forEach { rawName ->
                val spec = allowlist.spec(rawName)
                if (spec == null) {
                    put(rawName, degradedField(DegradeStatus.Unsupported, "不在 allowlist 中（可能是写属性或需平台签名）"))
                } else {
                    put(rawName, readOne(spec))
                }
            }
        }
        return ToolResult.Ok(fields)
    }

    private fun requestedNames(args: JsonObject): List<String> {
        val requested = (args["fields"] as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNullSafe() }
            ?.map { it.uppercase() }
            ?.distinct()
            .orEmpty()
        return if (requested.isEmpty()) allowlist.readable.map { it.name } else requested
    }

    private suspend fun readOne(spec: VehiclePropertySpec): JsonObject = when (val reading = reader.read(spec)) {
        is VehicleReading.Number -> buildJsonObject {
            put("status", "ok")
            put("value", reading.value)
            spec.unit?.let { put("unit", it) }
        }

        is VehicleReading.Text -> buildJsonObject {
            put("status", "ok")
            put("value", reading.value)
            spec.unit?.let { put("unit", it) }
        }

        is VehicleReading.Missing -> degradedField(reading.status, reading.detail)
    }

    private fun JsonPrimitive.contentOrNullSafe(): String? =
        if (this is kotlinx.serialization.json.JsonNull) null else content
}
