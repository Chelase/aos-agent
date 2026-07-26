package com.aos.agent.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * AOSAgent 主题。车机夜间驾驶场景强制深色，不跟随系统浅色模式。
 * 色值来源见 `.agent-rules/docs/原型/design.md` §2。
 */
private val AOSColorScheme = darkColorScheme(
    primary = AOSAccentPrimary,
    onPrimary = AOSBgPrimary,
    primaryContainer = AOSAccentDim,
    onPrimaryContainer = AOSAccentPrimary,
    secondary = AOSAccentSecondary,
    onSecondary = AOSBgPrimary,
    background = AOSBgPrimary,
    onBackground = AOSTextPrimary,
    surface = AOSBgSecondary,
    onSurface = AOSTextPrimary,
    surfaceVariant = AOSBgTertiary,
    onSurfaceVariant = AOSTextSecondary,
    surfaceContainerHighest = AOSBgElevated,
    outline = AOSBorderMedium,
    outlineVariant = AOSBorderSubtle,
    error = AOSStatusError,
    onError = AOSTextPrimary,
)

/** Material3 色板覆盖不到的扩展 token（语义状态、层级文本、品牌边框）。 */
@Immutable
data class AOSExtendedColors(
    val statusSuccess: Color,
    val statusWarning: Color,
    val statusError: Color,
    val statusInfo: Color,
    val successSurface: Color,
    val warningSurface: Color,
    val errorSurface: Color,
    val infoSurface: Color,
    val textTertiary: Color,
    val borderAccent: Color,
    val accentDim: Color,
)

private val AOSExtendedColorValues = AOSExtendedColors(
    statusSuccess = AOSStatusSuccess,
    statusWarning = AOSStatusWarning,
    statusError = AOSStatusError,
    statusInfo = AOSStatusInfo,
    successSurface = AOSSuccessSurface,
    warningSurface = AOSWarningSurface,
    errorSurface = AOSErrorSurface,
    infoSurface = AOSInfoSurface,
    textTertiary = AOSTextTertiary,
    borderAccent = AOSBorderAccent,
    accentDim = AOSAccentDim,
)

private val LocalAOSExtendedColors = staticCompositionLocalOf { AOSExtendedColorValues }

/** 在 [AOSAgentTheme] 作用域内读取扩展色 token。 */
val AOSTheme: AOSExtendedColors
    @Composable
    @ReadOnlyComposable
    get() = LocalAOSExtendedColors.current

@Composable
fun AOSAgentTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = AOSColorScheme,
        typography = AOSTypography,
        content = content,
    )
}
