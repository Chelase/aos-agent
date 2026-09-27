package com.aos.agent.core.llm

import kotlinx.coroutines.flow.Flow

/**
 * 模型接入点。形态参考 OpenMinis `LLMProvider` / RikkaHub `ai` 模块：
 * 流式分块 + 工具定义参数位 + baseURL/headers 归一化，MVP 只实现 OpenAI 兼容一种。
 */
interface LlmProvider {
    val id: String

    fun stream(request: LlmRequest): Flow<LlmStreamChunk>
}
