package com.aos.agent.core.tools.mcp

import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

class McpException(message: String) : IOException(message)

/** MCP 协议版本，按 2025-06-18 实现；server 回更高版本时只记录不协商。 */
const val MCP_PROTOCOL_VERSION = "2025-06-18"

private val JSON = Json { ignoreUnknownKeys = true }
private const val DATA_PREFIX = "data:"
private const val DONE_SENTINEL = "[DONE]"
private const val SESSION_HEADER = "Mcp-Session-Id"
private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

/**
 * 从 SSE 响应体里取出第一条 JSON-RPC 响应帧（`data:` 且以 `{` 开头）。
 * 心跳、注释行、`[DONE]`、非 JSON 内容都要跳过——这是"响应可能是 SSE"这条协议要求的落点。
 * 只用于 SSE 分支：`application/json` 响应直接整体解析，不经过这里。
 */
internal fun extractJsonRpcFrame(sseBody: String): String? =
    sseBody.lineSequence()
        .filter { it.startsWith(DATA_PREFIX) }
        .map { it.substring(DATA_PREFIX.length).trim() }
        .filter { it.startsWith("{") } // JSON-RPC 报文一定是对象；parseToJsonElement 对裸词也宽松，不能只靠它
        .firstOrNull { payload -> runCatching { JSON.parseToJsonElement(payload) }.isSuccess }

interface McpTransport {
    /** 发一个 JSON-RPC 请求并返回 `result`。传输失败、HTTP 非 2xx、JSON-RPC error 一律抛 [McpException]。 */
    suspend fun request(method: String, params: JsonObject): JsonElement

    /** 通知没有 id，也不期待响应体。 */
    suspend fun notify(method: String, params: JsonObject)
}

/**
 * Streamable HTTP 传输：一次 POST 对应一次请求，响应可能是 `application/json`，
 * 也可能是 `text/event-stream` 的单帧。会话 id 由 initialize 响应头给出，之后要回带。
 */
class StreamableHttpTransport(
    private val client: OkHttpClient,
    private val url: String,
    private val bearerToken: String? = null,
) : McpTransport {

    private var sessionId: String? = null
    private var nextId = 1

    override suspend fun request(method: String, params: JsonObject): JsonElement =
        post(buildRequestPayload(method, params))

    override suspend fun notify(method: String, params: JsonObject) {
        val body = buildJsonObject {
            put("jsonrpc", "2.0")
            put("method", method)
            put("params", params)
        }.toString()
        withContext(Dispatchers.IO) {
            execute(body) { response -> response.close() }
        }
    }

    private fun buildRequestPayload(method: String, params: JsonObject): String {
        val id = nextId++
        return buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", id)
            put("method", method)
            put("params", params)
        }.toString()
    }

    private suspend fun post(body: String): JsonElement = withContext(Dispatchers.IO) {
        execute(body) { response ->
            val text = response.body?.string() ?: throw McpException("empty response body")
            val payload = if (response.body?.contentType()?.subtype?.contains("event-stream") == true) {
                extractJsonRpcFrame(text) ?: throw McpException("no JSON-RPC frame in SSE response")
            } else {
                text
            }
            decodeResult(payload)
        }
    }

    private suspend fun <T> execute(body: String, handle: (Response) -> T): T =
        kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/json, text/event-stream")
                // 协议版本头只能在握手之后带：initialize 请求本身带它会被 server 判成
                // "还没协商就宣称版本"，官方 SDK server 直接 400。
                .apply {
                    sessionId?.let {
                        header(SESSION_HEADER, it)
                        header("MCP-Protocol-Version", MCP_PROTOCOL_VERSION)
                    }
                }
                .apply { bearerToken?.takeIf { it.isNotBlank() }?.let { header("Authorization", "Bearer $it") } }
                .post(body.toRequestBody(JSON_MEDIA_TYPE))
                .build()

            client.newCall(request).enqueue(
                object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        continuation.resumeWith(
                            Result.failure(McpException("transport failed: ${e.javaClass.simpleName}: ${e.message}")),
                        )
                    }

                    override fun onResponse(call: Call, response: Response) {
                        response.use { resp ->
                            val outcome = runCatching {
                                resp.header(SESSION_HEADER)?.let { sessionId = it }
                                if (!resp.isSuccessful) {
                                    // 状态码 + 截断的响应体：server 的 400 原因只写在 body 里。
                                    // 截断是防外溢——响应体可能回显我们自己的请求头或 token。
                                    val detail = runCatching { resp.body?.string()?.take(200) }.getOrNull()
                                    throw McpException("HTTP ${resp.code}" + (detail?.let { ": $it" } ?: ""))
                                }
                                handle(resp)
                            }
                            continuation.resumeWith(outcome)
                        }
                    }
                },
            )
        }

    private fun decodeResult(payload: String): JsonElement {
        val root = runCatching { JSON.parseToJsonElement(payload) as? JsonObject }
            .getOrNull() ?: throw McpException("response is not a JSON-RPC object")
        root["error"]?.let { error ->
            val message = (error as? JsonObject)?.get("message")?.toString().orEmpty()
            throw McpException("JSON-RPC error: $message")
        }
        return root["result"] ?: throw McpException("response has neither result nor error")
    }

}
