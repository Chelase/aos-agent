package com.aos.agent.ui.voice

import androidx.annotation.StringRes

/** 语音状态机的四个可见阶段；每个阶段都要在界面上有灯与文案。 */
enum class VoicePhase { IDLE, LISTENING, THINKING, SPEAKING }

/**
 * 语音界面状态：Compose 的唯一真相源。
 *
 * 文案一律用资源 id（中英成对），状态机里不出现字面量。
 */
data class VoiceUiState(
    val phase: VoicePhase = VoicePhase.IDLE,
    /** 流式部分识别结果，直接上屏。 */
    val partial: String = "",
    /** 能力 + 权限都具备才允许点；false 时按钮渲染禁用态并给出 [blockedReasonRes]。 */
    val usable: Boolean = false,
    /** 只差麦克风授权：按钮点击应直接发起授权请求，而不是只提示不可用。 */
    val needsPermission: Boolean = false,
    @StringRes val blockedReasonRes: Int? = null,
    @StringRes val noticeRes: Int? = null,
    /** 回执里要带的动态内容，例如命中的技能名。 */
    val noticeArg: String? = null,
    val continuous: Boolean = false,
    /** 最近一次已提交的识别文本，供界面显示"我听到了什么"。 */
    val lastHeard: String = "",
) {
    val busy: Boolean get() = phase == VoicePhase.LISTENING || phase == VoicePhase.THINKING
}
