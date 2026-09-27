package com.aos.agent.core.tools

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * 模型吐出的 function-call 参数经常是畸形 JSON（带 markdown 围栏、少个右括号、
 * 尾逗号）。直接判失败会让模型以为"工具坏了"从而反复重试，所以先做一次保守修复
 * 再交给工具执行；修不好才报错。思路来自 OpenMinis 的 ToolJsonRepair。
 */
object ToolJsonRepair {
    private val JSON = Json { ignoreUnknownKeys = true }
    private val FENCE = Regex("```(json)?", RegexOption.IGNORE_CASE)

    fun parseArguments(raw: String): JsonObject? {
        val base = normalize(raw) ?: return null
        return sequenceOf(base, removeTrailingCommas(base), balanceAndTrim(base), balanceAndTrim(removeTrailingCommas(base)))
            .filter { it.isNotBlank() }
            .mapNotNull { candidate -> runCatching { JSON.parseToJsonElement(candidate) as? JsonObject }.getOrNull() }
            .firstOrNull()
    }

    private fun normalize(raw: String): String? {
        val stripped = raw.replace(FENCE, "").trim()
        if (stripped.isEmpty()) return null
        // 模型偶尔在对象后面追加解释文字，截到最后一个右花括号为止。
        val lastBrace = stripped.lastIndexOf('}')
        return if (stripped.startsWith("{") && lastBrace in 1 until stripped.length) {
            stripped.substring(0, lastBrace + 1)
        } else {
            stripped
        }
    }

    private fun removeTrailingCommas(json: String): String =
        Regex(",\\s*([}\\]])").replace(json) { it.groupValues[1] }

    private fun balanceAndTrim(json: String): String {
        var openBraces = 0
        var openBrackets = 0
        json.forEach { char ->
            when (char) {
                '{' -> openBraces++
                '}' -> openBraces--
                '[' -> openBrackets++
                ']' -> openBrackets--
            }
        }
        if (openBraces <= 0 && openBrackets <= 0) return json
        return json.trimEnd(',', ' ', '\n') + "]".repeat(openBrackets.coerceAtLeast(0)) + "}".repeat(openBraces.coerceAtLeast(0))
    }
}
