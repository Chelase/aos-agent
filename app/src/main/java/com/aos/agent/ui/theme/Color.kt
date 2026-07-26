package com.aos.agent.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 色彩 token，对应 `.agent-rules/docs/原型/design.md` §2。
 * 修改前先改设计规范，再同步此处与 ColorTokensTest。
 */

// 背景层
val AOSBgPrimary = Color(0xFF0B1120)
val AOSBgSecondary = Color(0xFF111827)
val AOSBgTertiary = Color(0xFF1E293B)
val AOSBgElevated = Color(0xFF273549)

// 品牌强调色
val AOSAccentPrimary = Color(0xFF00E5FF)
val AOSAccentSecondary = Color(0xFF00BCD4)
val AOSAccentDim = Color(0x2600E5FF)

// 语义状态色
val AOSStatusSuccess = Color(0xFF22C55E)
val AOSStatusWarning = Color(0xFFF59E0B)
val AOSStatusError = Color(0xFFEF4444)
val AOSStatusInfo = Color(0xFF3B82F6)

// 文本层级
val AOSTextPrimary = Color(0xFFF1F5F9)
val AOSTextSecondary = Color(0xFF94A3B8)
val AOSTextTertiary = Color(0xFF64748B)

// 边框
val AOSBorderSubtle = Color(0x1A94A3B8)
val AOSBorderMedium = Color(0x3394A3B8)
val AOSBorderAccent = Color(0x4D00E5FF)

/** 状态标签背景，为对应语义色的 15% 透明度。 */
val AOSSuccessSurface = Color(0x2622C55E)
val AOSWarningSurface = Color(0x26F59E0B)
val AOSErrorSurface = Color(0x26EF4444)
val AOSInfoSurface = Color(0x263B82F6)
