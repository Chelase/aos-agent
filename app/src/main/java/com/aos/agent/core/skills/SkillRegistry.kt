package com.aos.agent.core.skills

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * skill 注册表：加载定义、按触发词命中。
 *
 * 命中用**声明式触发词**而不是"再问一次模型该用哪个 skill"：结果可穷举测试、
 * 可解释，且省一次往返（座舱对首包延迟敏感）。二期 AOC 远程下发 `skill_request`
 * 时按 [byId] 直接命中，共用同一张表。
 *
 * 未命中一律安静回退通用对话——这是父计划的硬要求。
 */
class SkillRegistry private constructor(
    val skills: List<SkillDefinition>,
    val skipped: List<Skipped>,
) {
    data class Skipped(val source: String, val reason: String)

    fun byId(id: String): SkillDefinition? = skills.firstOrNull { it.id == id }

    /** 触发词命中数最多的胜出；同分按 id 字典序，保证结果稳定可测。 */
    fun match(query: String): SkillDefinition? {
        val normalized = query.trim()
        if (normalized.isEmpty()) return null
        val hits = skills.mapNotNull { skill ->
            val score = skill.triggers.count { it.isNotBlank() && normalized.contains(it, ignoreCase = true) }
            skill.takeIf { score > 0 }?.let { it to score }
        }
        if (hits.isEmpty()) return null
        val best = hits.maxOf { it.second }
        return hits.filter { it.second == best }.minBy { it.first.id }.first
    }

    /** 注入系统提示用的能力清单：未命中时也让她知道车机有哪些本事。 */
    fun capabilitySummary(): String = skills.joinToString("; ") { "${it.name}：${it.description}" }

    companion object {
        private val JSON = Json { ignoreUnknownKeys = true }

        /**
         * @param sources (来源标识, 文件内容) 列表。坏 JSON 与不支持的档位进 [Skipped]，
         *                不抛异常——一份写坏的 skill 不该拖垮整个注册表。
         */
        fun parseAll(sources: List<Pair<String, String>>): SkillRegistry {
            val parsed = mutableListOf<SkillDefinition>()
            val skipped = mutableListOf<Skipped>()
            sources.forEach { (source, raw) ->
                when (val outcome = parseOne(source, raw)) {
                    is ParseOutcome.Ok -> parsed += outcome.skill
                    is ParseOutcome.Ignored -> skipped += Skipped(source, outcome.reason)
                }
            }
            return SkillRegistry(parsed.sortedBy { it.id }, skipped)
        }

        private sealed interface ParseOutcome {
            data class Ok(val skill: SkillDefinition) : ParseOutcome
            data class Ignored(val reason: String) : ParseOutcome
        }

        private fun parseOne(source: String, raw: String): ParseOutcome {
            val root = runCatching { JSON.parseToJsonElement(raw) as? JsonObject }.getOrNull()
                ?: return ParseOutcome.Ignored("不是合法 JSON 对象")
            val id = root.string("id") ?: return ParseOutcome.Ignored("缺少 id")
            val name = root.string("name") ?: return ParseOutcome.Ignored("缺少 name")
            val description = root.string("description") ?: return ParseOutcome.Ignored("缺少 description")
            val permission = SkillPermission.fromWire(root.string("permission"))
                ?: return ParseOutcome.Ignored("permission 取值非法：${root.string("permission")}")
            if (permission != SkillPermission.Query) {
                return ParseOutcome.Ignored("本期只允许 query 档，实际为 ${permission.wire}")
            }
            return ParseOutcome.Ok(
                SkillDefinition(
                    id = id,
                    name = name,
                    description = description,
                    triggers = root.strings("triggers"),
                    tools = root.strings("tools"),
                    prompt = root.string("prompt").orEmpty(),
                    permission = permission,
                    fallback = root.string("fallback"),
                    resultHint = root.string("result_hint"),
                    tags = root.strings("tags"),
                    inputModes = root.strings("input_modes"),
                    outputModes = root.strings("output_modes"),
                ),
            )
        }

        private fun JsonObject.string(key: String): String? =
            (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull

        private fun JsonObject.strings(key: String): List<String> =
            (this[key] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()
    }
}
