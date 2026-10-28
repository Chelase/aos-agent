package com.aos.agent.ui.chat

import com.aos.agent.core.tools.AskUserAnswer
import com.aos.agent.core.tools.AskUserChannel
import com.aos.agent.core.tools.AskUserOption
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 待回答的结构化提问：问题 + 1-4 个选项。
 * 界面弹窗渲染，驾驶态走语音播报 + 语音回答。
 */
data class PendingQuestion(
    val question: String,
    val options: List<AskUserOption>,
    val allowFreeText: Boolean,
)

/**
 * 界面侧 ask_user 通道：把待回答暴露成 StateFlow，由 Compose 弹窗 / 语音回写。
 * 与 [UiToolConfirmer] 同一模式：宿主完成回答后调 [respond]，超时返回 dismissed。
 */
class UiAskUserChannel : AskUserChannel {
    private val _pending = MutableStateFlow<PendingQuestion?>(null)
    val pending: StateFlow<PendingQuestion?> = _pending.asStateFlow()

    @Volatile
    private var deferred: CompletableDeferred<AskUserAnswer>? = null

    override suspend fun ask(
        question: String,
        options: List<AskUserOption>,
        allowFreeText: Boolean,
    ): AskUserAnswer {
        val answer = CompletableDeferred<AskUserAnswer>().also { deferred = it }
        _pending.value = PendingQuestion(question, options, allowFreeText)
        val result = withTimeoutOrNull(ASK_TIMEOUT_MS) { answer.await() }
            ?: AskUserAnswer(selectedIndex = null, text = "", dismissed = true)
        _pending.value = null
        deferred = null
        return result
    }

    fun respond(answer: AskUserAnswer) {
        deferred?.complete(answer)
    }

    fun dismiss() {
        deferred?.complete(AskUserAnswer(selectedIndex = null, text = "", dismissed = true))
    }

    private companion object {
        const val ASK_TIMEOUT_MS = 60_000L
    }
}
