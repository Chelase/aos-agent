package com.aos.agent.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * AOSAgent 主题（v5.0 双主题）：日间浅色（默认）+ 夜间深色，[darkTheme] 切换。
 * 色值来源见 `.agent-rules/docs/原型/design.md` §2。
 *
 * 主题状态存 ThemeStore（DataStore，唯一真相），切换由设置页触发并重建 Activity，
 * Composable 内不持有主题状态（design.md §9）。
 */

private val AOSLightColorScheme = lightColorScheme(
    primary = AOSAccentPrimaryLight,
    onPrimary = AOSOnAccentLight,
    primaryContainer = AOSAccentDimLight,
    onPrimaryContainer = AOSAccentPrimaryLight,
    secondary = AOSAccentSecondaryLight,
    onSecondary = AOSOnAccentLight,
    background = AOSBgPrimaryLight,
    onBackground = AOSTextPrimaryLight,
    surface = AOSBgSecondaryLight,
    onSurface = AOSTextPrimaryLight,
    surfaceVariant = AOSBgTertiaryLight,
    onSurfaceVariant = AOSTextSecondaryLight,
    surfaceContainer = AOSBgTertiaryLight,
    surfaceContainerHigh = AOSBgElevatedLight,
    surfaceContainerHighest = AOSBgElevatedLight,
    outline = AOSBorderMediumLight,
    outlineVariant = AOSBorderSubtleLight,
    error = AOSStatusErrorLight,
    onError = AOSOnAccentLight,
)

private val AOSDarkColorScheme = darkColorScheme(
    primary = AOSAccentPrimaryDark,
    onPrimary = AOSOnAccentDark,
    primaryContainer = AOSAccentDimDark,
    onPrimaryContainer = AOSAccentPrimaryDark,
    secondary = AOSAccentSecondaryDark,
    onSecondary = AOSOnAccentDark,
    background = AOSBgPrimaryDark,
    onBackground = AOSTextPrimaryDark,
    surface = AOSBgSecondaryDark,
    onSurface = AOSTextPrimaryDark,
    surfaceVariant = AOSBgTertiaryDark,
    onSurfaceVariant = AOSTextSecondaryDark,
    surfaceContainer = AOSBgTertiaryDark,
    surfaceContainerHigh = AOSBgElevatedDark,
    surfaceContainerHighest = AOSBgElevatedDark,
    outline = AOSBorderMediumDark,
    outlineVariant = AOSBorderSubtleDark,
    error = AOSStatusErrorDark,
    onError = AOSTextPrimaryDark,
)

/** Material3 色板覆盖不到的扩展 token（语义状态、层级文本、品牌边框），两主题各一份。 */
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

private val AOSLightExtendedColors = AOSExtendedColors(
    statusSuccess = AOSStatusSuccessLight,
    statusWarning = AOSStatusWarningLight,
    statusError = AOSStatusErrorLight,
    statusInfo = AOSStatusInfoLight,
    successSurface = AOSSuccessSurfaceLight,
    warningSurface = AOSWarningSurfaceLight,
    errorSurface = AOSErrorSurfaceLight,
    infoSurface = AOSInfoSurfaceLight,
    textTertiary = AOSTextTertiaryLight,
    borderAccent = AOSBorderAccentLight,
    accentDim = AOSAccentDimLight,
)

private val AOSDarkExtendedColors = AOSExtendedColors(
    statusSuccess = AOSStatusSuccessDark,
    statusWarning = AOSStatusWarningDark,
    statusError = AOSStatusErrorDark,
    statusInfo = AOSStatusInfoDark,
    successSurface = AOSSuccessSurfaceDark,
    warningSurface = AOSWarningSurfaceDark,
    errorSurface = AOSErrorSurfaceDark,
    infoSurface = AOSInfoSurfaceDark,
    textTertiary = AOSTextTertiaryDark,
    borderAccent = AOSBorderAccentDark,
    accentDim = AOSAccentDimDark,
)

private val LocalAOSExtendedColors = staticCompositionLocalOf { AOSDarkExtendedColors }

/** 在 [AOSAgentTheme] 作用域内读取扩展色 token。 */
val AOSTheme: AOSExtendedColors
    @Composable
    @ReadOnlyComposable
    get() = LocalAOSExtendedColors.current

@Composable
fun AOSAgentTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    val extendedColors = if (darkTheme) AOSDarkExtendedColors else AOSLightExtendedColors
    CompositionLocalProvider(LocalAOSExtendedColors provides extendedColors) {
        MaterialTheme(
            colorScheme = if (darkTheme) AOSDarkColorScheme else AOSLightColorScheme,
            typography = AOSTypography,
            content = content,
        )
    }
}
