package com.aos.agent.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.aos.agent.core.llm.LlmCliParser
import com.aos.agent.core.llm.LlmCliOutcome
import com.aos.agent.core.llm.LlmCliState
import com.aos.agent.core.llm.LlmCommand
import com.aos.agent.core.llm.LlmProfiles
import com.aos.agent.data.store.LlmConfigStore
import com.aos.agent.data.store.LlmProfileStore
import com.aos.agent.system.llm.LlmStateMirror
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 模型服务配置 CLI 的写通道：终端脚本 / adb 用显式组件广播进来。
 *
 * 非 exported、无 intent-filter——只有应用自身 uid（应用内真实终端 fork 的 shell）
 * 和系统特权调用方（adb）能送达，任何第三方 App 都无法借此改配置。
 * 结果回执走 ordered broadcast 的 resultData（掩码消息，绝无明文 Key）。
 */
class LlmConfigReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val appContext = context.applicationContext
        val command = LlmCliParser.parse(
            action = intent.action,
            name = intent.getStringExtra(LlmCliParser.EXTRA_NAME),
            url = intent.getStringExtra(LlmCliParser.EXTRA_URL),
            model = intent.getStringExtra(LlmCliParser.EXTRA_MODEL),
            key = intent.getStringExtra(LlmCliParser.EXTRA_KEY),
            keepKey = intent.getBooleanExtra(EXTRA_KEEP_KEY, false),
        )
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            val reply = runCatching {
                when {
                    intent.action == LlmCliParser.ACTION_REFRESH -> {
                        LlmStateMirror(appContext).refresh()
                        "已重建状态镜像"
                    }
                    command == null -> "无法识别的广播（action 或必填参数缺失）；用法见 aos-llm help"
                    else -> execute(appContext, command)
                }
            }.getOrElse { "执行失败：${it.message ?: it.javaClass.simpleName}" }
            pending.setResultData(reply)
            pending.finish()
        }
    }

    private suspend fun execute(context: Context, command: LlmCommand): String {
        val profileStore = LlmProfileStore(context)
        val llmStore = LlmConfigStore(context)
        val state = LlmCliState(
            profiles = profileStore.profiles(),
            active = llmStore.current(),
            activeProfile = profileStore.activeProfile(),
        )
        val outcome = LlmProfiles.apply(state, command)
        when (outcome) {
            is LlmCliOutcome.Err -> return outcome.message
            is LlmCliOutcome.Ok -> {
                val next = outcome.newState
                if (next.active != state.active) {
                    next.active?.let { llmStore.save(it) }
                }
                if (next.profiles != state.profiles) profileStore.save(next.profiles)
                if (next.activeProfile != state.activeProfile) profileStore.saveActiveProfile(next.activeProfile)
                LlmStateMirror(context).refresh()
                return outcome.message
            }
        }
    }

    private companion object {
        const val EXTRA_KEEP_KEY = "keep_key"
    }
}
