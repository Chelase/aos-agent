package com.aos.agent.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 色彩 token，对应 `.agent-rules/docs/原型/design.md` §2（v5.0 双主题）。
 * 全部 token 成对（*Light 日间 / *Dark 夜间），由 Theme.kt 按 darkTheme 组装下发。
 * 修改前先改设计规范，再同步此处与 DesignTokensTest。
 *
 * 注意：组件与页面禁止直引这些常量，必须经 MaterialTheme / AOSTheme 取色（design.md §9.4）。
 */

// —— 日间（浅色，默认主脸）——
val AOSBgPrimaryLight = Color(0xFFF5F8FC)
val AOSBgSecondaryLight = Color(0xFFFFFFFF)
val AOSBgTertiaryLight = Color(0xFFEDF2F8)
val AOSBgElevatedLight = Color(0xFFE2E9F2)
val AOSAccentPrimaryLight = Color(0xFF2E6FD8)
val AOSAccentSecondaryLight = Color(0xFF1E55B0)
val AOSAccentDimLight = Color(0x262E6FD8)
val AOSStatusSuccessLight = Color(0xFF1E7A34)
val AOSStatusWarningLight = Color(0xFFB45309)
val AOSStatusErrorLight = Color(0xFFC23425)
val AOSStatusInfoLight = Color(0xFF4A6584)
val AOSTextPrimaryLight = Color(0xFF10151C)
val AOSTextSecondaryLight = Color(0xFF45505C)
val AOSTextTertiaryLight = Color(0xFF5E6B7A)
val AOSOnAccentLight = Color(0xFFFFFFFF)
val AOSBorderSubtleLight = Color(0x1410151C)
val AOSBorderMediumLight = Color(0x2910151C)
val AOSBorderAccentLight = Color(0x662E6FD8)
val AOSSuccessSurfaceLight = Color(0x261E7A34)
val AOSWarningSurfaceLight = Color(0x26B45309)
val AOSErrorSurfaceLight = Color(0x26C23425)
val AOSInfoSurfaceLight = Color(0x264A6584)

// —— 夜间（深色）——
val AOSBgPrimaryDark = Color(0xFF0E1116)
val AOSBgSecondaryDark = Color(0xFF161B22)
val AOSBgTertiaryDark = Color(0xFF1D242D)
val AOSBgElevatedDark = Color(0xFF242D38)
val AOSAccentPrimaryDark = Color(0xFF5B9BFF)
val AOSAccentSecondaryDark = Color(0xFF3E7BD6)
val AOSAccentDimDark = Color(0x265B9BFF)
val AOSStatusSuccessDark = Color(0xFF7CC47F)
val AOSStatusWarningDark = Color(0xFFE8A33D)
val AOSStatusErrorDark = Color(0xFFEA6E5E)
val AOSStatusInfoDark = Color(0xFF8A9BB0)
val AOSTextPrimaryDark = Color(0xFFF2F5F9)
val AOSTextSecondaryDark = Color(0xFFA8B3C1)
val AOSTextTertiaryDark = Color(0xFF7C8899)
val AOSOnAccentDark = Color(0xFF0A1428)
val AOSBorderSubtleDark = Color(0x14F2F5F9)
val AOSBorderMediumDark = Color(0x29F2F5F9)
val AOSBorderAccentDark = Color(0x665B9BFF)
val AOSSuccessSurfaceDark = Color(0x267CC47F)
val AOSWarningSurfaceDark = Color(0x26E8A33D)
val AOSErrorSurfaceDark = Color(0x26EA6E5E)
val AOSInfoSurfaceDark = Color(0x268A9BB0)
