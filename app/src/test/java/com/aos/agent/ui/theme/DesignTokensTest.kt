package com.aos.agent.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 守护设计 token 与 `.agent-rules/docs/原型/design.md` 一致。
 * 改动色值前必须先改设计规范，否则本测试失败。
 */
class DesignTokensTest {

    @Test
    fun backgroundTokens_matchDesignSpec() {
        assertEquals(Color(0xFF0B1120), AOSBgPrimary)
        assertEquals(Color(0xFF111827), AOSBgSecondary)
        assertEquals(Color(0xFF1E293B), AOSBgTertiary)
        assertEquals(Color(0xFF273549), AOSBgElevated)
    }

    @Test
    fun accentAndStatusTokens_matchDesignSpec() {
        assertEquals(Color(0xFF00E5FF), AOSAccentPrimary)
        assertEquals(Color(0xFF00BCD4), AOSAccentSecondary)
        assertEquals(Color(0xFF22C55E), AOSStatusSuccess)
        assertEquals(Color(0xFFF59E0B), AOSStatusWarning)
        assertEquals(Color(0xFFEF4444), AOSStatusError)
        assertEquals(Color(0xFF3B82F6), AOSStatusInfo)
    }

    @Test
    fun textTokens_matchDesignSpec() {
        assertEquals(Color(0xFFF1F5F9), AOSTextPrimary)
        assertEquals(Color(0xFF94A3B8), AOSTextSecondary)
        assertEquals(Color(0xFF64748B), AOSTextTertiary)
    }

    @Test
    fun touchTarget_meetsDrivingSafetyMinimum() {
        // design.md §1.2：驾驶场景交互区域 ≥ 56dp
        assertTrue(AOSSizing.touchTarget.value >= 56f)
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
}
