package com.aos.agent.system

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 驾驶态分类与诊断归一的纯逻辑测试。
 *
 * [DriveRestrictionClassifier] 和 [DriveUxDiagnostics.fromFailure] 都不触碰 Car API：
 * 分类的边界（null = 未知、0 = 基线）和异常类名的保留，都在这里锁死，
 * 避免以后有人"顺手"把原因吞掉、把静默 UNKNOWN 带回来。
 */
class DriveUxDiagnosticsTest {

    @Test
    fun classifyNullIsUnknownNotUnrestricted() {
        assertEquals(DriveRestriction.UNKNOWN, DriveRestrictionClassifier.classify(null))
    }

    @Test
    fun classifyBaselineIsUnrestricted() {
        assertEquals(
            DriveRestriction.UNRESTRICTED,
            DriveRestrictionClassifier.classify(DriveRestrictionClassifier.UX_RESTRICTIONS_BASELINE),
        )
    }

    @Test
    fun classifyAnyRestrictionBitIsRestricted() {
        // UX_RESTRICTIONS_NO_VIDEO = 1, UX_RESTRICTIONS_LIMIT_STRING_LENGTH = 3 等，任何非 0 都是受限
        assertEquals(DriveRestriction.RESTRICTED, DriveRestrictionClassifier.classify(1))
        assertEquals(DriveRestriction.RESTRICTED, DriveRestrictionClassifier.classify(255))
        assertEquals(DriveRestriction.RESTRICTED, DriveRestrictionClassifier.classify(-1))
    }

    @Test
    fun failureKeepsExceptionNameForLogcatLookup() {
        val diagnostic = DriveUxDiagnostics.fromFailure(
            DriveUxDiagnostics.FailureStage.REGISTER,
            SecurityException("permission denied"),
        )

        assertEquals(DriveUxDiagnostics.FailureStage.REGISTER, diagnostic.stage)
        assertTrue("异常类名要留下来，真车只凭它在 logcat 里定位", diagnostic.reason.contains("SecurityException"))
    }

    @Test
    fun nullReturnIsDescribedAsNullNotException() {
        val diagnostic = DriveUxDiagnostics.fromFailure(
            DriveUxDiagnostics.FailureStage.GET_MANAGER,
            null,
        )

        assertEquals(DriveUxDiagnostics.FailureStage.GET_MANAGER, diagnostic.stage)
        assertEquals("返回 null", diagnostic.reason)
    }

    @Test
    fun allStagesAreDistinguishable() {
        val stages = DriveUxDiagnostics.FailureStage.entries
        assertEquals("三段诊断缺一不可，否则又回到粗粒度吞异常", 3, stages.size)
    }
}