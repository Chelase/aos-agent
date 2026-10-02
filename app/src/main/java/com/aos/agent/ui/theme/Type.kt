package com.aos.agent.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * 字体 token，对应 `.agent-rules/docs/原型/design.md` §3.2（v2.0）。
 *
 * 字号与字重严格按设计规范。字族暂用系统字体：design.md 指定的 Outfit / JetBrains Mono
 * 均无中文字形，而本应用默认语言为中文，直接套用会导致中文回退到系统字体、
 * 中英文混排字重不一致。待打包带 CJK 覆盖的字体文件后，只需改本文件两个常量。
 *
 * Legend 签名样式（design.md §3.2）：区块图例 13sp/500 + 0.1em 字距，像仪表盘上的刻度图例；
 * 由 [aosLegendStyle] 统一供给，AOSSectionHeader 消费。中文字符加字距同样成立。
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
        letterSpacing = (-0.01f).em, // -0.01em，随字号缩放
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

/** Legend — 区块图例签名样式：13sp/500 + 0.1em 字距。 */
val aosLegendStyle = TextStyle(
    fontFamily = AOSUiFontFamily,
    fontWeight = FontWeight.Medium,
    fontSize = 13.sp,
    lineHeight = 18.sp,
    letterSpacing = 0.1f.em,
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

    /** Terminal — 终端字符网格（字号参与 PTY 行列计算，改值需重新点开终端） */
    val terminal = TextStyle(
        fontFamily = AOSDataFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    )
}
