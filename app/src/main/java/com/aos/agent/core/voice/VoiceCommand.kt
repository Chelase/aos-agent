package com.aos.agent.core.voice

/**
 * 本地语音指令：命中即就地执行，不进模型，省时省 token。
 *
 * 只支持整句固定指令（见 [VoiceCommandParser]），避免把普通提问误判成命令。
 */
sealed interface VoiceCommand {
    /** 新开会话：清空当前对话流并中止在跑的回合。 */
    data object NewSession : VoiceCommand

    data object OpenSettings : VoiceCommand

    data object CloseSettings : VoiceCommand

    data object GoHome : VoiceCommand

    /** 列出可用 MCP 工具来源并引导选择。 */
    data object UseMcp : VoiceCommand

    /** 指名使用某个技能，[skill] 为用户原话里的技能名。 */
    data class UseSkill(val skill: String) : VoiceCommand
}
