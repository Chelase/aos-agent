package com.aos.agent.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/** 原型指定的 Android 系统无衬线，完整 CJK 回退；所有字号支持系统字体缩放。 */
private val uiFont = FontFamily.SansSerif
private val dataFont = FontFamily.Monospace

val AOSTypography = Typography(
    displayLarge = TextStyle(fontFamily = uiFont, fontWeight = FontWeight.Bold, fontSize = 56.sp, lineHeight = 64.sp, letterSpacing = (-0.02f).em),
    headlineLarge = TextStyle(fontFamily = uiFont, fontWeight = FontWeight.Bold, fontSize = 44.sp, lineHeight = 52.sp),
    headlineMedium = TextStyle(fontFamily = uiFont, fontWeight = FontWeight.Bold, fontSize = 36.sp, lineHeight = 44.sp),
    headlineSmall = TextStyle(fontFamily = uiFont, fontWeight = FontWeight.Medium, fontSize = 28.sp, lineHeight = 36.sp),
    titleLarge = TextStyle(fontFamily = uiFont, fontWeight = FontWeight.Medium, fontSize = 26.sp, lineHeight = 34.sp),
    titleMedium = TextStyle(fontFamily = uiFont, fontWeight = FontWeight.Medium, fontSize = 24.sp, lineHeight = 32.sp),
    titleSmall = TextStyle(fontFamily = uiFont, fontWeight = FontWeight.Medium, fontSize = 20.sp, lineHeight = 28.sp),
    bodyLarge = TextStyle(fontFamily = uiFont, fontWeight = FontWeight.Normal, fontSize = 24.sp, lineHeight = 36.sp),
    bodyMedium = TextStyle(fontFamily = uiFont, fontWeight = FontWeight.Normal, fontSize = 22.sp, lineHeight = 32.sp),
    bodySmall = TextStyle(fontFamily = uiFont, fontWeight = FontWeight.Normal, fontSize = 18.sp, lineHeight = 26.sp),
    labelLarge = TextStyle(fontFamily = uiFont, fontWeight = FontWeight.Medium, fontSize = 20.sp, lineHeight = 28.sp),
    labelMedium = TextStyle(fontFamily = uiFont, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 22.sp),
    labelSmall = TextStyle(fontFamily = uiFont, fontWeight = FontWeight.Normal, fontSize = 18.sp, lineHeight = 26.sp),
)

val aosLegendStyle = TextStyle(
    fontFamily = uiFont, fontWeight = FontWeight.Medium,
    fontSize = 22.sp, lineHeight = 30.sp, letterSpacing = 0.em,
)

object AOSDataText {
    val standard = TextStyle(fontFamily = dataFont, fontWeight = FontWeight.Normal, fontSize = 20.sp, lineHeight = 28.sp)
    val large = TextStyle(fontFamily = uiFont, fontWeight = FontWeight.Bold, fontSize = 44.sp, lineHeight = 56.sp)
    // 等宽格大小参与 PTY 尺寸计算，保留既有终端协议。
    val terminal = TextStyle(fontFamily = dataFont, fontWeight = FontWeight.Normal, fontSize = 22.sp, lineHeight = 32.sp)
}
