package com.aos.agent.ui.chat

import com.aos.agent.core.tools.ToolConfirmer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject

data class PendingConfirm(
    val toolName: String,
    val argsSummary: String,
)

/**
 * 界面侧 Ask 确认器：把待确认请求暴露成 StateFlow，由 Compose 弹窗回写。
 * 驾驶态拒绝由 [com.aos.agent.core.tools.ToolSystem] 在调用本确认器之前处理。
 */
class UiToolConfirmer : ToolConfirmer {
    private val _pending = MutableStateFlow<PendingConfirm?>(null)
    val pending: StateFlow<PendingConfirm?> = _pending.asStateFlow()

    @Volatile
    private var deferred: CompletableDeferred<Boolean>? = null

    override suspend fun confirm(toolName: String, args: JsonObject): Boolean {
        val answer = CompletableDeferred<Boolean>().also { deferred = it }
        _pending.value = PendingConfirm(toolName, args.toString().take(200))
        val result = withTimeoutOrNull(CONFIRM_TIMEOUT_MS) { answer.await() } ?: false
        _pending.value = null
        deferred = null
        return result
    }

    fun respond(allow: Boolean) {
        deferred?.complete(allow)
    }

    private companion object {
        const val CONFIRM_TIMEOUT_MS = 15_000L
    }
}
