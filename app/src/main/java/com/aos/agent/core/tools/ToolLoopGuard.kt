package com.aos.agent.core.tools

import com.aos.agent.core.llm.LlmToolCall

/**
 * 循环防护：步数上限 + 重复调用检测。
 *
 * 模型一旦认定工具"没答对"，会用完全相同的参数一直重试，把整轮预算烧光。
 * 参考 OpenMinis 的 ToolLoopDetector。
 */
class ToolLoopGuard(val maxSteps: Int = DEFAULT_MAX_STEPS) {
    private var lastSignature: String? = null

    fun stepsExhausted(step: Int): Boolean = step > maxSteps

    /** 与上一步完全相同的工具+参数视为打转。 */
    fun isRepeat(call: LlmToolCall): Boolean {
        val signature = "${call.name}::${call.argumentsJson.trim()}"
        val repeat = signature == lastSignature
        lastSignature = signature
        return repeat
    }

    fun reset() {
        lastSignature = null
    }

    companion object {
        const val DEFAULT_MAX_STEPS = 8
    }
}
