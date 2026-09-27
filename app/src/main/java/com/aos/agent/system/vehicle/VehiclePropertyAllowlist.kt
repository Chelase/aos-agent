package com.aos.agent.system.vehicle

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive

enum class VehiclePropertyAccess { Read, Write }

/**
 * 一条车辆属性声明。字段语义对齐 AOSP VHAL 属性与 CarToolForge 的 allowlist 形态，
 * 但**不含任何厂商硬编码**：换 OEM 只改 `assets/vehicle/vehicle_properties.json`。
 */
data class VehiclePropertySpec(
    val name: String,
    /** AOSP 系统属性不需要 id；vendor 扩展属性必须给整数 id。 */
    val id: Int?,
    val category: String,
    val permission: String,
    /** 运行环境实测出来的保护级别，例如 `normal` / `dangerous` / `signature|privileged`。 */
    val protection: String,
    val access: VehiclePropertyAccess,
    val unit: String?,
    val description: String,
) {
    val isPrivileged: Boolean get() = protection.contains("signature") || protection.contains("privileged")
}

/**
 * 车辆属性 allowlist。
 *
 * [skipped] 不是失败记录，而是**为什么这些字段本期不可用**的可观测证据：
 * 写属性与特权属性对普通可安装应用结构性不可得（见 mvp-core-plan Step 3 调整点 4），
 * 与其悄悄少几个字段，不如把原因留在数据里供工具和日志读取。
 */
class VehiclePropertyAllowlist private constructor(
    val readable: List<VehiclePropertySpec>,
    val skipped: List<Skipped>,
) {
    data class Skipped(val name: String, val reason: String)

    val names: Set<String> get() = readable.map { it.name }.toSet()

    fun spec(name: String): VehiclePropertySpec? = readable.firstOrNull { it.name == name }

    companion object {
        private val JSON = Json { ignoreUnknownKeys = true }

        /**
         * @param allowPrivilegedRead 系统级预装后（Batch 3）置 true，特权只读属性才会进可用集。
         */
        fun parse(raw: String, allowPrivilegedRead: Boolean = false): VehiclePropertyAllowlist {
            val root = JSON.parseToJsonElement(raw) as? JsonObject
                ?: return VehiclePropertyAllowlist(emptyList(), listOf(Skipped("<root>", "allowlist 不是 JSON 对象")))
            val entries = root["properties"] as? JsonArray ?: return VehiclePropertyAllowlist(emptyList(), emptyList())

            val readable = mutableListOf<VehiclePropertySpec>()
            val skipped = mutableListOf<Skipped>()
            entries.forEach { element ->
                val obj = element as? JsonObject
                val name = (obj?.get("name") as? JsonPrimitive)?.contentOrNullSafe()
                if (obj == null || name.isNullOrBlank()) {
                    skipped += Skipped("<unknown>", "缺少 name，无法注册")
                    return@forEach
                }
                val spec = obj.toSpec(name)
                when {
                    spec == null -> skipped += Skipped(name, "字段不完整或 access 取值非法")
                    spec.access == VehiclePropertyAccess.Write -> skipped += Skipped(name, "写属性本期不注册为工具")
                    spec.isPrivileged && !allowPrivilegedRead -> skipped += Skipped(name, "需要平台签名，普通应用不可得")
                    else -> readable += spec
                }
            }
            return VehiclePropertyAllowlist(readable, skipped)
        }

        private fun JsonObject.toSpec(name: String): VehiclePropertySpec? {
            val category = string("category") ?: return null
            val permission = string("permission") ?: return null
            val protection = string("protection") ?: return null
            val access = when (string("access")) {
                "read" -> VehiclePropertyAccess.Read
                "write" -> VehiclePropertyAccess.Write
                else -> return null
            }
            return VehiclePropertySpec(
                name = name,
                id = (this["id"] as? JsonPrimitive)?.intOrNull(),
                category = category,
                permission = permission,
                protection = protection,
                access = access,
                unit = string("unit"),
                description = string("description") ?: return null,
            )
        }

        private fun JsonObject.string(key: String): String? =
            (this[key] as? JsonPrimitive)?.contentOrNullSafe()

        private fun JsonPrimitive.contentOrNullSafe(): String? = if (this is kotlinx.serialization.json.JsonNull) null else content

        private fun JsonPrimitive.intOrNull(): Int? = runCatching { int }.getOrNull()
    }
}
