package com.aos.agent.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 守护设计 token 与 `.agent-rules/docs/原型/design.md`（v5.0 双主题）一致。
 * 改动色值前必须先改设计规范，否则本测试失败。
 */
class DesignTokensTest {

    @Test
    fun lightBackgroundTokens_matchDesignSpec() {
        // design.md §2.1：日间蓝白浅色（默认主脸）
        assertEquals(Color(0xFFF5F8FC), AOSBgPrimaryLight)
        assertEquals(Color(0xFFFFFFFF), AOSBgSecondaryLight)
        assertEquals(Color(0xFFEDF2F8), AOSBgTertiaryLight)
        assertEquals(Color(0xFFE2E9F2), AOSBgElevatedLight)
    }

    @Test
    fun darkBackgroundTokens_matchDesignSpec() {
        // design.md §2.1：夜间深空蓝黑
        assertEquals(Color(0xFF0E1116), AOSBgPrimaryDark)
        assertEquals(Color(0xFF161B22), AOSBgSecondaryDark)
        assertEquals(Color(0xFF1D242D), AOSBgTertiaryDark)
        assertEquals(Color(0xFF242D38), AOSBgElevatedDark)
    }

    @Test
    fun lightAccentAndStatusTokens_matchDesignSpec() {
        assertEquals(Color(0xFF2E6FD8), AOSAccentPrimaryLight)
        assertEquals(Color(0xFF1E55B0), AOSAccentSecondaryLight)
        assertEquals(Color(0xFF1E7A34), AOSStatusSuccessLight)
        assertEquals(Color(0xFFB45309), AOSStatusWarningLight)
        assertEquals(Color(0xFFC23425), AOSStatusErrorLight)
        assertEquals(Color(0xFF4A6584), AOSStatusInfoLight)
    }

    @Test
    fun darkAccentAndStatusTokens_matchDesignSpec() {
        assertEquals(Color(0xFF5B9BFF), AOSAccentPrimaryDark)
        assertEquals(Color(0xFF3E7BD6), AOSAccentSecondaryDark)
        assertEquals(Color(0xFF7CC47F), AOSStatusSuccessDark)
        assertEquals(Color(0xFFE8A33D), AOSStatusWarningDark)
        assertEquals(Color(0xFFEA6E5E), AOSStatusErrorDark)
        assertEquals(Color(0xFF8A9BB0), AOSStatusInfoDark)
    }

    @Test
    fun lightTextTokens_matchDesignSpec() {
        assertEquals(Color(0xFF10151C), AOSTextPrimaryLight)
        assertEquals(Color(0xFF45505C), AOSTextSecondaryLight)
        assertEquals(Color(0xFF5E6B7A), AOSTextTertiaryLight)
    }

    @Test
    fun darkTextTokens_matchDesignSpec() {
        assertEquals(Color(0xFFF2F5F9), AOSTextPrimaryDark)
        assertEquals(Color(0xFFA8B3C1), AOSTextSecondaryDark)
        assertEquals(Color(0xFF7C8899), AOSTextTertiaryDark)
    }

    @Test
    fun onAccentTexts_matchDesignSpec() {
        // design.md §2.5：日间蓝底白字、夜间蓝底深蓝墨字
        assertEquals(Color(0xFFFFFFFF), AOSOnAccentLight)
        assertEquals(Color(0xFF0A1428), AOSOnAccentDark)
    }

    @Test
    fun touchTarget_meetsDrivingSafetyMinimum() {
        // design.md §1.2：驾驶场景交互区域 ≥ 56dp
        assertTrue(AOSSizing.touchTarget.value >= 56f)
    }

    @Test
    fun cardCorner_matchesSpec() {
        // design.md §4.4：卡片圆角 12dp
        assertEquals(12f, AOSSizing.cardCorner.value, 0.001f)
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
        assertEquals(32f, AOSTypography.displayLarge.fontSize.value, 0.001f)
        assertEquals(24f, AOSTypography.headlineMedium.fontSize.value, 0.001f)
        assertEquals(18f, AOSTypography.titleMedium.fontSize.value, 0.001f)
        assertEquals(15f, AOSTypography.bodyMedium.fontSize.value, 0.001f)
        assertEquals(13f, AOSTypography.labelSmall.fontSize.value, 0.001f)
        assertEquals(14f, AOSDataText.standard.fontSize.value, 0.001f)
        assertEquals(20f, AOSDataText.large.fontSize.value, 0.001f)
    }

    @Test
    fun legendStyle_matchesSignatureTypeScale() {
        // design.md §3.2：Legend 13sp/500 + 0.1em 字距，区块图例签名样式
        // letterSpacing 为 em 单位时 TextUnit.value 即 em 倍数
        assertEquals(13f, aosLegendStyle.fontSize.value, 0.001f)
        assertTrue(aosLegendStyle.letterSpacing.type == androidx.compose.ui.unit.TextUnitType.Em)
        assertEquals(0.1f, aosLegendStyle.letterSpacing.value, 0.001f)
    }
}
