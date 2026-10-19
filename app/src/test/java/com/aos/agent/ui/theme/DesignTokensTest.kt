package com.aos.agent.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 守护设计 token 与 `.agent-rules/docs/原型/design.md`（v6 原型双主题）一致。
 * 改动色值前必须先改设计规范，否则本测试失败。
 */
class DesignTokensTest {

    @Test
    fun lightBackgroundTokens_matchDesignSpec() {
        // design.md §2.1：日间蓝白浅色（默认主脸）
        assertEquals(Color(0xFFF4F6FA), AOSBgPrimaryLight)
        assertEquals(Color(0xFFFFFFFF), AOSBgSecondaryLight)
        assertEquals(Color(0xFFE9EEF6), AOSBgTertiaryLight)
        assertEquals(Color(0xFFE1E8F2), AOSBgElevatedLight)
    }

    @Test
    fun darkBackgroundTokens_matchDesignSpec() {
        // design.md §2.1：夜间深空蓝黑
        assertEquals(Color(0xFF0B0F17), AOSBgPrimaryDark)
        assertEquals(Color(0xFF131A26), AOSBgSecondaryDark)
        assertEquals(Color(0xFF1A2331), AOSBgTertiaryDark)
        assertEquals(Color(0xFF222D40), AOSBgElevatedDark)
    }

    @Test
    fun lightAccentAndStatusTokens_matchDesignSpec() {
        assertEquals(Color(0xFF0B57D0), AOSAccentPrimaryLight)
        assertEquals(Color(0xFF0B57D0), AOSAccentSecondaryLight)
        assertEquals(Color(0xFF146C2E), AOSStatusSuccessLight)
        assertEquals(Color(0xFF7A4A00), AOSStatusWarningLight)
        assertEquals(Color(0xFFBA1A1A), AOSStatusErrorLight)
        assertEquals(Color(0xFF43474E), AOSStatusInfoLight)
    }

    @Test
    fun darkAccentAndStatusTokens_matchDesignSpec() {
        assertEquals(Color(0xFFA8C7FA), AOSAccentPrimaryDark)
        assertEquals(Color(0xFFA8C7FA), AOSAccentSecondaryDark)
        assertEquals(Color(0xFF7BD88F), AOSStatusSuccessDark)
        assertEquals(Color(0xFFF0B429), AOSStatusWarningDark)
        assertEquals(Color(0xFFFFB4AB), AOSStatusErrorDark)
        assertEquals(Color(0xFFB4BCC9), AOSStatusInfoDark)
    }

    @Test
    fun lightTextTokens_matchDesignSpec() {
        assertEquals(Color(0xFF191C22), AOSTextPrimaryLight)
        assertEquals(Color(0xFF43474E), AOSTextSecondaryLight)
        assertEquals(Color(0xFF6B7280), AOSTextTertiaryLight)
    }

    @Test
    fun darkTextTokens_matchDesignSpec() {
        assertEquals(Color(0xFFE3E7EE), AOSTextPrimaryDark)
        assertEquals(Color(0xFFB4BCC9), AOSTextSecondaryDark)
        assertEquals(Color(0xFF8A94A6), AOSTextTertiaryDark)
    }

    @Test
    fun onAccentTexts_matchDesignSpec() {
        // design.md §2.5：日间蓝底白字、夜间蓝底深蓝墨字
        assertEquals(Color(0xFFFFFFFF), AOSOnAccentLight)
        assertEquals(Color(0xFF06275F), AOSOnAccentDark)
    }

    @Test
    fun touchTarget_meetsDrivingSafetyMinimum() {
        // design.md §1.2：驾驶场景交互区域 ≥ 56dp
        assertTrue(AOSSizing.touchTarget.value >= 72f)
    }

    @Test
    fun cardCorner_matchesSpec() {
        // design.md §4.4：卡片圆角 12dp
        assertEquals(20f, AOSSizing.cardCorner.value, 0.001f)
    }

    @Test
    fun spacing_followsEightPointGrid() {
        // design.md §4.1：基准网格 8dp（xs 为 4dp 半格，用于图标与文本贴合）
        listOf(AOSSpacing.sm, AOSSpacing.md, AOSSpacing.lg, AOSSpacing.xl).forEach { spacing ->
            assertEquals(0f, spacing.value % 8f, 0.001f)
        }
        assertEquals(4f, AOSSpacing.xs.value, 0.001f)
    }

    @Test
    fun typography_matchesTypeScale() {
        assertEquals(56f, AOSTypography.displayLarge.fontSize.value, 0.001f)
        assertEquals(36f, AOSTypography.headlineMedium.fontSize.value, 0.001f)
        assertEquals(24f, AOSTypography.titleMedium.fontSize.value, 0.001f)
        assertEquals(22f, AOSTypography.bodyMedium.fontSize.value, 0.001f)
        assertEquals(18f, AOSTypography.labelSmall.fontSize.value, 0.001f)
        assertEquals(20f, AOSDataText.standard.fontSize.value, 0.001f)
        assertEquals(44f, AOSDataText.large.fontSize.value, 0.001f)
    }

    @Test
    fun legendStyle_matchesSignatureTypeScale() {
        // design.md §3.2：Legend 13sp/500 + 0.1em 字距，区块图例签名样式
        // letterSpacing 为 em 单位时 TextUnit.value 即 em 倍数
        assertEquals(22f, aosLegendStyle.fontSize.value, 0.001f)
        assertTrue(aosLegendStyle.letterSpacing.type == androidx.compose.ui.unit.TextUnitType.Em)
        assertEquals(0f, aosLegendStyle.letterSpacing.value, 0.001f)
    }
}
