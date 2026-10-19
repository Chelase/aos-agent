package com.aos.agent.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 色彩 token，对应 `.agent-rules/docs/原型/design.md` §2（v6 原型双主题）。
 * 全部 token 成对（*Light 日间 / *Dark 夜间），由 Theme.kt 按 darkTheme 组装下发。
 * 修改前先改设计规范，再同步此处与 DesignTokensTest。
 *
 * 注意：组件与页面禁止直引这些常量，必须经 MaterialTheme / AOSTheme 取色（design.md §9.4）。
 */

// —— 日间（浅色，默认主脸）——
val AOSBgPrimaryLight = Color(0xFFF4F6FA)
val AOSBgSecondaryLight = Color(0xFFFFFFFF)
val AOSBgTertiaryLight = Color(0xFFE9EEF6)
val AOSBgElevatedLight = Color(0xFFE1E8F2)
val AOSAccentPrimaryLight = Color(0xFF0B57D0)
val AOSAccentSecondaryLight = Color(0xFF0B57D0)
val AOSAccentDimLight = Color(0xFFD3E3FD)
val AOSStatusSuccessLight = Color(0xFF146C2E)
val AOSStatusWarningLight = Color(0xFF7A4A00)
val AOSStatusErrorLight = Color(0xFFBA1A1A)
val AOSStatusInfoLight = Color(0xFF43474E)
val AOSTextPrimaryLight = Color(0xFF191C22)
val AOSTextSecondaryLight = Color(0xFF43474E)
val AOSTextTertiaryLight = Color(0xFF6B7280)
val AOSOnAccentLight = Color(0xFFFFFFFF)
val AOSBorderSubtleLight = Color(0xFFC4C6D0)
val AOSBorderMediumLight = Color(0xFFC4C6D0)
val AOSBorderAccentLight = Color(0xFFC4C6D0)
val AOSSuccessSurfaceLight = Color(0xFFD3F2D9)
val AOSWarningSurfaceLight = Color(0xFFFFDE9E)
val AOSErrorSurfaceLight = Color(0xFFFFDAD6)
val AOSInfoSurfaceLight = Color(0xFFE9EEF6)

// —— 夜间（深色）——
val AOSBgPrimaryDark = Color(0xFF0B0F17)
val AOSBgSecondaryDark = Color(0xFF131A26)
val AOSBgTertiaryDark = Color(0xFF1A2331)
val AOSBgElevatedDark = Color(0xFF222D40)
val AOSAccentPrimaryDark = Color(0xFFA8C7FA)
val AOSAccentSecondaryDark = Color(0xFFA8C7FA)
val AOSAccentDimDark = Color(0xFF29425F)
val AOSStatusSuccessDark = Color(0xFF7BD88F)
val AOSStatusWarningDark = Color(0xFFF0B429)
val AOSStatusErrorDark = Color(0xFFFFB4AB)
val AOSStatusInfoDark = Color(0xFFB4BCC9)
val AOSTextPrimaryDark = Color(0xFFE3E7EE)
val AOSTextSecondaryDark = Color(0xFFB4BCC9)
val AOSTextTertiaryDark = Color(0xFF8A94A6)
val AOSOnAccentDark = Color(0xFF06275F)
val AOSBorderSubtleDark = Color(0xFF3A4454)
val AOSBorderMediumDark = Color(0xFF3A4454)
val AOSBorderAccentDark = Color(0xFF3A4454)
val AOSSuccessSurfaceDark = Color(0xFF0E3B1E)
val AOSWarningSurfaceDark = Color(0xFF4A3200)
val AOSErrorSurfaceDark = Color(0xFF93000A)
val AOSInfoSurfaceDark = Color(0xFF1A2331)

// 导航与终端保持独立的语义表面。
val AOSRailLight = Color(0xFFFFFFFF)
val AOSRailDark = Color(0xFF101722)
val AOSOnContainerLight = Color(0xFF041E49)
val AOSOnContainerDark = Color(0xFFD3E3FD)
val AOSTerminalBackground = Color(0xFF080D14)
val AOSTerminalForeground = Color(0xFFD3E3FD)
