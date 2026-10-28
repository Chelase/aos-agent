package com.aos.agent.core.engine

import com.aos.agent.core.llm.LlmMessage
import com.aos.agent.core.llm.LlmRole
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * 上下文分级降级。
 *
 * 对标 Claude Code 的 C4 结论：上下文超限要分级降级，不要一次性丢历史。
 * 一级压工具输出（问答脉络还在），二级丢整轮（最伤，放最后）。
 */
object ContextDegradation {

    /** 工具结果里单个字符串字段压缩后保留的字符数。 */
    const val DEFAULT_FIELD_CHARS = 160

    /** 最近 N 条工具结果保持原样：模型多半正要基于它继续追问。 */
    const val DEFAULT_KEEP_RECENT = 2

    private val JSON = Json { ignoreUnknownKeys = true }

    /**
     * 一级降级：压缩旧工具结果里的长字段，保留 JSON 结构与关键路径字段。
     *
     * 只动工具返回的原文，不动问答，也不拆散 tool_call 与 tool 结果的配对——
     * OpenAI 兼容协议要求 tool 消息前必须有对应的 assistant(tool_calls)。
     */
    fun compactToolResults(
        messages: List<LlmMessage>,
        fieldChars: Int = DEFAULT_FIELD_CHARS,
        keepRecent: Int = DEFAULT_KEEP_RECENT,
    ): List<LlmMessage> {
        val toolIndices = messages.indices.filter { messages[it].role == LlmRole.Tool }
        if (toolIndices.isEmpty()) return messages
        // keepRecent=0 表示全压；dropLast(0) 会返回空列表，这里显式处理。
        val stale = if (keepRecent <= 0) toolIndices else toolIndices.dropLast(keepRecent)
        if (stale.isEmpty()) return messages
        val targets = stale.toSet()
        return messages.mapIndexed { index, message ->
            if (index in targets) message.copy(content = compactContent(message.content, fieldChars)) else message
        }
    }

    /**
     * 二级降级：按**回合组**丢弃最旧的历史，而不是逐条丢。
     *
     * 逐条丢会拆散 `assistant(tool_calls)` 与其后 `tool` 消息的配对，
     * OpenAI 兼容服务端会直接拒整个请求。一组 = 一条 user 消息 + 其后连续的
     * assistant / tool 消息。
     */
    fun dropOldestTurns(messages: List<LlmMessage>, targetTokens: Int, budget: TokenBudget): List<LlmMessage> {
        var kept = messages.toMutableList()
        var total = kept.sumOf { budget.estimate(it.content) }
        while (total > targetTokens) {
            val groupSize = oldestGroupSize(kept)
            // 剩下不足一组（只剩当前这轮）就不再丢
            if (groupSize <= 0 || kept.size - groupSize < 1) break
            repeat(groupSize) { total -= budget.estimate(kept.removeAt(0).content) }
        }
        return kept
    }

    /**
     * 最旧一组的大小：从头找第一条 user 消息，它之前的内容不算一组；
     * 该 user 及其后连续的 assistant / tool 消息构成一组。
     */
    private fun oldestGroupSize(messages: List<LlmMessage>): Int {
        val start = messages.indexOfFirst { it.role == LlmRole.User }
        if (start < 0) return 0
        var end = start + 1
        while (end < messages.size && messages[end].role != LlmRole.User) end++
        return end
    }

    /**
     * 工具结果正文：JSON 保留结构只压长字段（`full_output` 这类路径字段因此得以保留），
     * 非 JSON 直接截断。
     */
    private fun compactContent(content: String, fieldChars: Int): String {
        if (content.length <= fieldChars) return content
        val obj = runCatching { JSON.parseToJsonElement(content) as? JsonObject }.getOrNull()
            ?: return "…（原文 ${content.length} 字符，已压缩）"
        val compacted = JsonObject(obj.mapValues { (_, value) -> compactValue(value, fieldChars) })
        return buildJsonObject {
            compacted.forEach { (key, value) -> put(key, value) }
            put("compacted", true)
            put("original_chars", content.length)
        }.toString()
    }

    private fun compactValue(value: JsonElement, limit: Int): JsonElement = when {
        value is JsonPrimitive && value.isString && value.content.length > limit ->
            JsonPrimitive("…（已压缩 ${value.content.length} 字符）")

        value is JsonObject -> JsonObject(value.mapValues { (_, nested) -> compactValue(nested, limit) })
        value is JsonArray -> JsonArray(value.map { compactValue(it, limit) })
        else -> value
    }
}
