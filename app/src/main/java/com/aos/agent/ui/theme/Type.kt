package com.aos.agent.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * 字体 token，对应 `.agent-rules/docs/原型/design.md` §3.2。
 *
 * 字号与字重严格按设计规范。字族暂用系统字体：design.md 指定的 Outfit / JetBrains Mono
 * 均无中文字形，而本应用默认语言为中文，直接套用会导致中文回退到系统字体、
 * 中英文混排字重不一致。待打包带 CJK 覆盖的字体文件后，只需改本文件两个常量。
 */
private val AOSUiFontFamily = FontFamily.SansSerif
private val AOSDataFontFamily = FontFamily.Monospace

val AOSTypography = Typography(
    // Display — 页面主标题
    displayLarge = TextStyle(
        fontFamily = AOSUiFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
    ),
    // Headline — 区块标题
    headlineMedium = TextStyle(
        fontFamily = AOSUiFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
    ),
    // Title — 卡片标题
    titleMedium = TextStyle(
        fontFamily = AOSUiFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
    ),
    // Body — 正文
    bodyMedium = TextStyle(
        fontFamily = AOSUiFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    ),
    // Caption — 辅助说明
    labelSmall = TextStyle(
        fontFamily = AOSUiFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
)

/** 等宽数据样式，Material3 无对应槽位，单独暴露。 */
object AOSDataText {
    /** Data — 系统数值 */
    val standard = TextStyle(
        fontFamily = AOSDataFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    )

    /** Data Large — 关键指标 */
    val large = TextStyle(
        fontFamily = AOSDataFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 20.sp,
        lineHeight = 26.sp,
    )
}
