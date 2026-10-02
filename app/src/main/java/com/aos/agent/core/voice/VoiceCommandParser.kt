package com.aos.agent.core.voice

/**
 * 指令词表：中英成对，由资源注入（文案不进解析逻辑）。
 * 固定指令按整句全等匹配，技能指令按前缀匹配。
 */
data class VoiceCommandVocabulary(
    val newSession: Set<String> = emptySet(),
    val openSettings: Set<String> = emptySet(),
    val closeSettings: Set<String> = emptySet(),
    val goHome: Set<String> = emptySet(),
    val useMcp: Set<String> = emptySet(),
    val useSkillPrefixes: Set<String> = emptySet(),
) {
    val normalizedNewSession = newSession.map(VoiceCommandParser::normalize).toSet()
    val normalizedOpenSettings = openSettings.map(VoiceCommandParser::normalize).toSet()
    val normalizedCloseSettings = closeSettings.map(VoiceCommandParser::normalize).toSet()
    val normalizedGoHome = goHome.map(VoiceCommandParser::normalize).toSet()
    val normalizedUseMcp = useMcp.map(VoiceCommandParser::normalize).toSet()
}

/**
 * 识别文本 → [VoiceCommand]，纯函数、无 Android 依赖。
 *
 * 匹配规则（宁可漏判也不误判，避免把普通提问当命令）：
 * 1. 固定指令要**整句**命中，"帮我新开会话"不算命令；
 * 2. 技能指令按前缀命中，前缀后必须带技能名，光说"使用技能"不算；
 * 3. 归一化 = 转小写 + 只留字母数字，标点/空格/大小写差异不影响命中；
 * 4. 技能名保留用户原话（只剥句末标点），以便与技能注册表里的名字对得上。
 */
object VoiceCommandParser {

    private val TRAILING_PUNCTUATION = charArrayOf('.', '。', '！', '!', '?', '？', '，', ',', '、')

    fun normalize(text: String): String = buildString(text.length) {
        text.lowercase().forEach { ch -> if (ch.isLetterOrDigit()) append(ch) }
    }

    fun parse(text: String, vocab: VoiceCommandVocabulary): VoiceCommand? {
        val key = normalize(text)
        if (key.isEmpty()) return null

        val command = when (key) {
            in vocab.normalizedNewSession -> VoiceCommand.NewSession
            in vocab.normalizedOpenSettings -> VoiceCommand.OpenSettings
            in vocab.normalizedCloseSettings -> VoiceCommand.CloseSettings
            in vocab.normalizedGoHome -> VoiceCommand.GoHome
            in vocab.normalizedUseMcp -> VoiceCommand.UseMcp
            else -> null
        }
        if (command != null) return command

        val raw = text.trim()
        val prefix = vocab.useSkillPrefixes.firstOrNull { raw.startsWith(it, ignoreCase = true) }
            ?: return null
        val skill = raw.substring(prefix.length).trim().trimEnd(*TRAILING_PUNCTUATION).trim()
        return skill.takeIf { it.isNotEmpty() }?.let { VoiceCommand.UseSkill(it) }
    }
}
