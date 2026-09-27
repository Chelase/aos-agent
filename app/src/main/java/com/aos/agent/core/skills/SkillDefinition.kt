package com.aos.agent.core.skills

/**
 * skill 风险档位，对齐父计划决策 7 的控车分级。
 * 本期只有 `query` 可执行；其余档位解析出来也一律不注册（见 SkillRegistry）。
 */
enum class SkillPermission(val wire: String) {
    Query("query"),
    Comfort("comfort"),
    Motion("motion"),
    HighRisk("high_risk"),
    ;

    companion object {
        fun fromWire(value: String?): SkillPermission? = entries.firstOrNull { it.wire == value }
    }
}

/**
 * 一份 skill 定义。字段形状同时兼顾两处消费方：
 * `triggers`/`tools`/`prompt` 给本地引擎，`tags`/`inputModes`/`outputModes`
 * 是二期 AOC capability 上报的映射位（本期只解析不使用）。
 */
data class SkillDefinition(
    val id: String,
    val name: String,
    val description: String,
    val triggers: List<String>,
    val tools: List<String>,
    val prompt: String,
    val permission: SkillPermission,
    val fallback: String? = null,
    val resultHint: String? = null,
    val tags: List<String> = emptyList(),
    val inputModes: List<String> = emptyList(),
    val outputModes: List<String> = emptyList(),
) {
    /**
     * 只保留引擎里真实存在的工具名。
     * skill 写了不存在的名字（含控制类工具）时静默过滤掉，而不是让调用失败——
     * 定义文件出错不该把一次正常对话变成一轮报错。
     */
    fun effectiveToolNames(available: Set<String>): Set<String> = tools.filter { it in available }.toSet()
}
