package com.aos.agent.core.session

import com.aos.agent.core.llm.LlmMessage
import com.aos.agent.core.llm.LlmRole
import com.aos.agent.core.llm.LlmToolCall
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * 会话 JSONL 持久化：append-only，每轮一条。
 *
 * 落盘位置是应用私有目录（卸载即清），不是备份介质——
 * 作用是进程被杀后能恢复最近一轮，而不是做长期存档。
 */
object SessionLog {

    private val JSON = Json { ignoreUnknownKeys = true }

    /** 一轮落盘记录。工具交换序列原样保留配对关系。 */
    data class TurnRecord(
        val timestamp: Long,
        val user: String,
        val toolExchange: List<LlmMessage> = emptyList(),
        val assistant: String,
    )

    fun append(dir: File, record: TurnRecord) {
        dir.mkdirs()
        val line = buildJsonObject {
            put("ts", record.timestamp)
            put("user", record.user)
            if (record.toolExchange.isNotEmpty()) {
                put("tool_exchange", JsonArray(record.toolExchange.map { it.toJson() }))
            }
            put("assistant", record.assistant)
        }.toString()
        File(dir, LOG_NAME).appendText(line + "\n")
    }

    /** 读回最近 [lastTurns] 轮。文件损坏的行跳过，不让整段历史作废。 */
    fun readRecent(dir: File, lastTurns: Int = 1): List<TurnRecord> {
        val log = File(dir, LOG_NAME)
        if (!log.exists()) return emptyList()
        return log.readLines()
            .mapNotNull { runCatching { parseLine(it) }.getOrNull() }
            .takeLast(lastTurns.coerceAtLeast(1))
    }

    /** 新会话：另起一段，旧文件保留作排查证据。 */
    fun rotate(dir: File, timestamp: Long = System.currentTimeMillis()) {
        val log = File(dir, LOG_NAME)
        if (log.exists()) {
            log.renameTo(File(dir, "session-$timestamp.jsonl"))
        }
    }

    private fun parseLine(line: String): TurnRecord? {
        val obj = runCatching { JSON.parseToJsonElement(line) as? JsonObject }.getOrNull() ?: return null
        val user = (obj["user"] as? JsonPrimitive)?.contentOrNull ?: return null
        val assistant = (obj["assistant"] as? JsonPrimitive)?.contentOrNull ?: return null
        val exchange = (obj["tool_exchange"] as? JsonArray)
            ?.mapNotNull { (it as? JsonObject)?.toMessage() }
            .orEmpty()
        return TurnRecord(
            timestamp = (obj["ts"] as? JsonPrimitive)?.contentOrNull?.toLongOrNull() ?: 0L,
            user = user,
            toolExchange = exchange,
            assistant = assistant,
        )
    }

    private fun LlmMessage.toJson(): JsonObject = buildJsonObject {
        put("role", role.name)
        put("content", content)
        toolCallId?.let { put("tool_call_id", it) }
        toolName?.let { put("tool_name", it) }
        if (toolCalls.isNotEmpty()) {
            put("tool_calls", JsonArray(toolCalls.map { call ->
                buildJsonObject {
                    put("id", call.id)
                    put("name", call.name)
                    put("arguments", call.argumentsJson)
                }
            }))
        }
    }

    private fun JsonObject.toMessage(): LlmMessage? {
        val role = runCatching { LlmRole.valueOf((this["role"] as? JsonPrimitive)?.contentOrNull ?: "") }
            .getOrNull() ?: return null
        val calls = (this["tool_calls"] as? JsonArray)?.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val id = (obj["id"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
            LlmToolCall(
                id = id,
                name = (obj["name"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
                argumentsJson = (obj["arguments"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
            )
        }.orEmpty()
        return LlmMessage(
            role = role,
            content = (this["content"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
            toolCalls = calls,
            toolCallId = (this["tool_call_id"] as? JsonPrimitive)?.contentOrNull,
            toolName = (this["tool_name"] as? JsonPrimitive)?.contentOrNull,
        )
    }

    internal const val LOG_NAME = "session.jsonl"
}
