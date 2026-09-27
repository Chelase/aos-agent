package com.aos.agent.core.tools

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * 三级权限。`Ask` 在没有确认器时**默认拒绝**（fail-closed），
 * 系统弹窗不是主路径——车机上没有可靠的通用授权 UI。
 */
enum class ToolPermission { Auto, Ask, Forbid }

/** 工具不可用的原因，结构化到字段级，便于模型判断"是没法读"还是"读出来是空"。 */
enum class DegradeStatus(val wire: String) {
    PermissionDenied("permission_denied"),
    NoVhal("no_vhal"),
    Unsupported("unsupported"),
    NotConfigured("not_configured"),
}

sealed interface ToolResult {
    /**
     * 成功。[fields] 里放结构化值（数字/枚举/布尔），不要塞散文——
     * 自然语言由引擎或模型再生成。单个字段取不到时，它自己的值可以是一个
     * 带 `status` 的对象，见 [degradedField]。
     */
    data class Ok(val fields: JsonObject) : ToolResult

    /** 权限或确认环节挡下了，没有执行。 */
    data class Rejected(val reason: String) : ToolResult

    data class TimedOut(val timeoutMillis: Long) : ToolResult

    data class Error(val cause: String) : ToolResult
}

/** 生成 `{status: ..., detail: ...}` 形态的降级字段值。 */
fun degradedField(status: DegradeStatus, detail: String? = null): JsonObject = buildJsonObject {
    put("status", status.wire)
    detail?.let { put("detail", it) }
}

/**
 * 工具契约。`name` / `version` / `category` 三元组对齐 Android 16
 * `@AppFunctionSchemaDefinition` 的语义，二期把同一批工具经 `AppFunctionManager`
 * 暴露给系统 Agent 时不需要重新设计（见 mvp-core-plan 决策 10）。
 */
interface Tool {
    val name: String
    val version: Int
    val category: String

    /** 面向模型的说明：必须写清单位与取值枚举，降低畸形 function-call 概率。 */
    val description: String
    val permission: ToolPermission
    val inputSchema: JsonObject

    /**
     * 按参数细分权限。默认沿用 [permission]；
     * shell 这类"同一工具、不同命令风险不同"的场景覆写它。
     */
    fun permissionFor(args: JsonObject): ToolPermission = permission

    suspend fun execute(args: JsonObject): ToolResult
}
