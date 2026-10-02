package com.aos.agent.ui.voice

import android.content.Context
import com.aos.agent.R
import com.aos.agent.core.voice.VoiceCommandVocabulary

/**
 * 从资源装配指令词表：文案与解析逻辑分离，中英词同时收（见 strings.xml 注释）。
 */
fun voiceVocabularyFrom(context: Context): VoiceCommandVocabulary = VoiceCommandVocabulary(
    newSession = context.stringArray(R.array.voice_cmd_new_session),
    openSettings = context.stringArray(R.array.voice_cmd_open_settings),
    closeSettings = context.stringArray(R.array.voice_cmd_close_settings),
    goHome = context.stringArray(R.array.voice_cmd_go_home),
    useMcp = context.stringArray(R.array.voice_cmd_use_mcp),
    useSkillPrefixes = context.stringArray(R.array.voice_cmd_use_skill_prefix),
)

private fun Context.stringArray(resId: Int): Set<String> =
    resources.getStringArray(resId).map { it.trim() }.filter { it.isNotEmpty() }.toSet()
