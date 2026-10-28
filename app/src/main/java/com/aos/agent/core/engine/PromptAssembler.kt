package com.aos.agent.core.engine

import java.util.concurrent.ConcurrentHashMap

/** One independently rendered prompt contribution. */
data class PromptSection(
    val name: String,
    val order: Int,
    /**
     * 稳定节：渲染结果与会话上下文无关（纯常量文本），首帧渲染后逐字复用。
     * 依赖环境的节（时间、工具清单、CLI 是否存在）必须保持 `false`，
     * 否则快照会把过期的环境信息固化下来。
     */
    val stable: Boolean = false,
    val render: (PromptContext) -> String,
)

data class PromptContext(
    val values: Map<String, String> = emptyMap(),
) {
    operator fun get(key: String): String = values[key].orEmpty()
}

/**
 * 按 order 拼装系统提示分节。
 *
 * 稳定节首帧渲染后快照复用，动态节每轮重算——与 Claude Code 的
 * `systemPromptSnapshot` 同一思路：不变的语义逐字保持，变化的上下文始终新鲜。
 */
class PromptAssembler(sections: List<PromptSection>) {
    private val sections = sections.toList()
    private val snapshots = ConcurrentHashMap<String, String>()

    init {
        require(this.sections.map { it.name }.distinct().size == this.sections.size) {
            "Prompt section names must be unique"
        }
    }

    fun assemble(context: PromptContext): String = sections
        .sortedWith(compareBy(PromptSection::order, PromptSection::name))
        .map { section ->
            if (section.stable) {
                snapshots.computeIfAbsent(section.name) { section.render(context).trim() }
            } else {
                section.render(context).trim()
            }
        }
        .filter(String::isNotEmpty)
        .joinToString("\n\n")

    /** 稳定节内容随外部条件变化时调用（如用户切换语言）。 */
    fun resetSnapshots() {
        snapshots.clear()
    }
}