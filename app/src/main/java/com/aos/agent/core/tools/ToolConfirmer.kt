package com.aos.agent.core.tools

import kotlinx.serialization.json.JsonObject

/**
 * `Ask` 级工具的人工确认口。由宿主注入（界面确认、AOC 远程确认、测试桩）。
 * 未注入即视为拒绝，不降级为自动放行。
 */
fun interface ToolConfirmer {
    /** 返回 true 才执行。实现方自己负责超时；[ToolSystem] 的执行超时会罩住确认之后的部分。 */
    suspend fun confirm(toolName: String, args: JsonObject): Boolean
}
