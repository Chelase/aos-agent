package com.aos.agent.core.llm

import java.io.IOException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

private val JSON = Json { ignoreUnknownKeys = true }

/** SSE 单行解析结果。 */
internal sealed interface SseEvent {
    data class Delta(val text: String) : SseEvent
    data object Done : SseEvent

    /** 空行、心跳、非增量字段，以及解析不了的内容——都不视为失败，不能因此掐断整条流。 */
    data object Ignored : SseEvent
}

/**
 * 纯函数：把 `data:` 后面的 payload 转成事件。
 * 与网络分离是为了让畸形 chunk 能被单测覆盖，而不是只在联调时撞见。
 */
internal fun parseSseData(payload: String): SseEvent {
    val body = payload.trim()
    if (body.isEmpty()) return SseEvent.Ignored
    if (body == "[DONE]") return SseEvent.Done

    val root = runCatching { JSON.parseToJsonElement(body) as? JsonObject }.getOrNull()
        ?: return SseEvent.Ignored
    val choices = root["choices"] as? JsonArray ?: return SseEvent.Ignored
    val first = choices.firstOrNull() as? JsonObject ?: return SseEvent.Ignored
    val delta = first["delta"] as? JsonObject ?: return SseEvent.Ignored
    val content = delta["content"] ?: return SseEvent.Ignored
    if (content is JsonNull) return SseEvent.Ignored

    val text = (content as? JsonPrimitive)?.contentOrNull ?: return SseEvent.Ignored
    return SseEvent.Delta(text)
}

/** OpenAI 兼容协议的流式实现。MVP 只做这一种 provider。 */
class OpenAiCompatibleProvider(
    private val client: OkHttpClient,
    private val config: LlmConfig,
    override val id: String = "openai-compatible",
) : LlmProvider {

    override fun stream(request: LlmRequest): Flow<LlmStreamChunk> = callbackFlow {
        val httpRequest = Request.Builder()
            .url(config.chatCompletionsUrl)
            .header("Accept", "text/event-stream")
            .apply {
                if (config.apiKey.isNotBlank()) {
                    header("Authorization", "Bearer ${config.apiKey}")
                }
                config.extraHeaders.forEach { (name, value) -> header(name, value) }
            }
            .post(requestBody(request).toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val call = client.newCall(httpRequest)
        call.enqueue(
            object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    trySend(LlmStreamChunk.Failed(e))
                    close()
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use { resp ->
                        if (!resp.isSuccessful) {
                            // 只带状态码：响应体可能回显请求头，不入库不外传。
                            trySend(LlmStreamChunk.Failed(IOException("HTTP ${resp.code}")))
                            close()
                            return
                        }
                        val source = resp.body?.source()
                        if (source == null) {
                            trySend(LlmStreamChunk.Failed(IOException("empty response body")))
                            close()
                            return
                        }
                        readStream(source) { chunk -> trySend(chunk) }
                        trySend(LlmStreamChunk.Done)
                        close()
                    }
                }
            },
        )
        awaitClose { call.cancel() }
    }

    private fun readStream(
        source: okio.BufferedSource,
        emit: (LlmStreamChunk) -> Unit,
    ) {
        runCatching {
            while (true) {
                val line = source.readUtf8Line() ?: break
                if (!line.startsWith(DATA_PREFIX)) continue
                when (val event = parseSseData(line.substring(DATA_PREFIX.length))) {
                    is SseEvent.Delta -> emit(LlmStreamChunk.Text(event.text))
                    SseEvent.Done -> break
                    SseEvent.Ignored -> Unit
                }
            }
        }.onFailure { emit(LlmStreamChunk.Failed(it)) }
    }

    private fun requestBody(request: LlmRequest): String = buildJsonObject {
        put("model", request.modelOverride ?: config.model)
        put("stream", true)
        put(
            "messages",
            JsonArray(
                request.messages.map { message ->
                    buildJsonObject {
                        put("role", message.role.wire())
                        put("content", message.content)
                    }
                },
            ),
        )
        if (request.toolDefinitions.isNotEmpty()) {
            put(
                "tools",
                JsonArray(
                    request.toolDefinitions.map { tool ->
                        buildJsonObject {
                            put("type", "function")
                            put(
                                "function",
                                buildJsonObject {
                                    put("name", tool.name)
                                    put("description", tool.description)
                                    put("parameters", tool.parameters)
                                },
                            )
                        }
                    },
                ),
            )
        }
    }.toString()

    private companion object {
        const val DATA_PREFIX = "data:"
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
