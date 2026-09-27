package com.aos.agent.core.tools

import com.aos.agent.core.llm.ToolDefinition
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject

/**
 * 工具注册表与执行闸口：查找、三级权限判定、超时、异常包装。
 *
 * 一切失败都收敛成 [ToolResult]，不向引擎抛异常——半截异常会让模型看不到
 * "为什么没结果"，从而反复重试同一个调用。
 */
class ToolSystem(
    private val confirmer: ToolConfirmer? = null,
    private val timeoutMillis: Long = DEFAULT_TIMEOUT_MS,
) {
    private val tools = LinkedHashMap<String, Tool>()

    val all: List<Tool> get() = tools.values.toList()

    fun register(tool: Tool) {
        tools[tool.name] = tool
    }

    fun find(name: String): Tool? = tools[name]

    /**
     * Step 4 的 Skill 只暴露自己声明的工具子集。
     * 未知名字直接忽略而不是报错：skill 定义写错不该把引擎拖崩。
     */
    fun exposedFor(names: Set<String>? = null): List<Tool> =
        if (names == null) all else all.filter { it.name in names }

    fun definitions(exposed: List<Tool> = all): List<ToolDefinition> =
        exposed.map { ToolDefinition(it.name, it.description, it.inputSchema) }

    suspend fun execute(name: String, args: JsonObject): ToolResult = try {
        val tool = tools[name] ?: return ToolResult.Error("unknown tool: $name")
        dispatch(tool, args)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Throwable) {
        // 只带异常类型：消息与参数可能含用户数据或凭据。
        ToolResult.Error("tool $name threw ${error.javaClass.simpleName}")
    }

    private suspend fun dispatch(tool: Tool, args: JsonObject): ToolResult =
        when (tool.permissionFor(args)) {
        ToolPermission.Forbid -> ToolResult.Rejected("${tool.name} 属禁用级，本期不可调用")

        ToolPermission.Ask -> {
            val currentConfirmer = confirmer
            if (currentConfirmer == null) {
                ToolResult.Rejected("${tool.name} 需要确认，但当前没有注入确认器")
            } else if (!currentConfirmer.confirm(tool.name, args)) {
                ToolResult.Rejected("${tool.name} 的确认被拒绝")
            } else {
                withDeadline(tool, args)
            }
        }

        ToolPermission.Auto -> withDeadline(tool, args)
    }

    private suspend fun withDeadline(tool: Tool, args: JsonObject): ToolResult = try {
        withTimeout(timeoutMillis) { tool.execute(args) }
    } catch (timeout: TimeoutCancellationException) {
        ToolResult.TimedOut(timeoutMillis)
    }

    companion object {
        const val DEFAULT_TIMEOUT_MS = 30_000L
    }
}
