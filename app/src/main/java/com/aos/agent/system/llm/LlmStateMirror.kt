package com.aos.agent.system.llm

import android.content.Context
import android.util.Log
import com.aos.agent.core.llm.LlmConfig
import com.aos.agent.core.llm.LlmProfile
import com.aos.agent.data.store.LlmConfigStore
import com.aos.agent.data.store.LlmProfileStore
import java.io.File

/**
 * 终端 CLI（aos-llm）的读取通道：把存储里的配置状态渲染成可 source 的
 * `files/llm/state.sh`。
 *
 * 只写掩码后的 Key（[LlmConfig.maskedKey]），明文凭据永不落这份文件——
 * 它的生命周期在应用私有目录，但消费方是 shell 脚本，按最坏情况对待。
 * 原子写（tmp → rename）保证脚本 source 到的永远是完整文件。
 */
class LlmStateMirror(private val context: Context) {

    suspend fun refresh() {
        val active = LlmConfigStore(context).current()
        val store = LlmProfileStore(context)
        write(
            dir = File(context.filesDir, STATE_DIR_NAME),
            active = active,
            activeProfile = store.activeProfile(),
            profiles = store.profiles(),
        )
    }

    companion object {
        const val STATE_DIR_NAME = "llm"
        const val STATE_FILE_NAME = "state.sh"
        private const val TAG = "AOSAgent.LlmCli"

        fun write(dir: File, active: LlmConfig?, activeProfile: String?, profiles: List<LlmProfile>) {
            val sb = StringBuilder()
            sb.append("# 由 AOS-Agent 自动生成，请勿手改；aos-llm 脚本读取本文件。\n")
            sb.append("AOS_LLM_URL=").append(quote(active?.baseUrl.orEmpty())).append('\n')
            sb.append("AOS_LLM_MODEL=").append(quote(active?.model.orEmpty())).append('\n')
            sb.append("AOS_LLM_KEY=").append(quote(active?.let { masked(it.apiKey) } ?: "-")).append('\n')
            sb.append("AOS_LLM_ACTIVE_PROFILE=").append(quote(activeProfile ?: "-")).append('\n')
            sb.append("AOS_LLM_PROFILES=").append(quote(profiles.joinToString(" ") { it.name })).append('\n')
            profiles.forEach { profile ->
                val prefix = "AOS_LLM_PROFILE_${profile.name}_"
                sb.append(prefix).append("URL=").append(quote(profile.baseUrl)).append('\n')
                sb.append(prefix).append("MODEL=").append(quote(profile.model)).append('\n')
                sb.append(prefix).append("KEY=").append(quote(masked(profile.apiKey))).append('\n')
            }
            runCatching {
                dir.mkdirs()
                val tmp = File(dir, "$STATE_FILE_NAME.tmp")
                tmp.writeText(sb.toString())
                if (!tmp.renameTo(File(dir, STATE_FILE_NAME))) {
                    // rename 目标已存在时个别文件系统可能拒绝，退化为覆盖写
                    File(dir, STATE_FILE_NAME).writeText(sb.toString())
                    tmp.delete()
                }
            }.onFailure { Log.w(TAG, "state.sh 写入失败", it) }
        }

        /** 单引号包裹并转义内容单引号，产出永远是安全的 shell 字面量。 */
        fun quote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

        /** 与 [LlmConfig.maskedKey] 同语义：只暴露长度，不暴露内容。 */
        private fun masked(key: String): String =
            if (key.isBlank()) "empty" else "set(len=${key.length})"
    }
}
