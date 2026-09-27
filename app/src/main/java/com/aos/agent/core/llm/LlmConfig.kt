package com.aos.agent.core.llm

/**
 * 模型服务配置。
 *
 * [toString] 与 [describe] 都只输出掩码后的 key：日志里出现凭据值是本项目的硬禁止项。
 */
data class LlmConfig(
    val baseUrl: String,
    val model: String,
    val apiKey: String,
    val extraHeaders: Map<String, String> = emptyMap(),
) {
    val isUsable: Boolean get() = baseUrl.isNotBlank() && model.isNotBlank()

    /** 允许填 `https://host/v1` 或已经带 `/chat/completions` 的全路径。 */
    val chatCompletionsUrl: String
        get() {
            val trimmed = baseUrl.trim().trimEnd('/')
            return if (trimmed.endsWith(CHAT_COMPLETIONS_SUFFIX)) {
                trimmed
            } else {
                trimmed + CHAT_COMPLETIONS_SUFFIX
            }
        }

    fun maskedKey(): String =
        if (apiKey.isBlank()) "empty" else "set(len=${apiKey.length})"

    fun describe(): String = "LlmConfig(baseUrl=$baseUrl, model=$model, apiKey=${maskedKey()})"

    override fun toString(): String = describe()

    private companion object {
        const val CHAT_COMPLETIONS_SUFFIX = "/chat/completions"
    }
}

/** 配置的读取口。Android 侧由 DataStore 实现，测试用假实现。 */
interface LlmConfigSource {
    suspend fun current(): LlmConfig?
}
