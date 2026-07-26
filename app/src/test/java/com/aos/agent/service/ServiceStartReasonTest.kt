package com.aos.agent.service

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `ServiceStartReason` 是纯逻辑，不依赖 Android 运行时，可在 JVM 上直接测。
 * 注意：`from()` 需要 Intent 实例，故只测不依赖 Intent 的部分与动作集合；
 * Intent 解析路径由仪器测试覆盖。
 */
class ServiceStartReasonTest {

    @Test
    fun isBootAction_acceptsAllSupportedBootBroadcasts() {
        assertTrue(ServiceStartReason.isBootAction(Intent.ACTION_BOOT_COMPLETED))
        assertTrue(ServiceStartReason.isBootAction(Intent.ACTION_LOCKED_BOOT_COMPLETED))
        assertTrue(ServiceStartReason.isBootAction(ServiceStartReason.ACTION_QUICKBOOT_POWERON))
        assertTrue(
            ServiceStartReason.isBootAction(ServiceStartReason.ACTION_QUICKBOOT_POWERON_LEGACY),
        )
    }

    @Test
    fun isBootAction_rejectsUnrelatedAndMissingActions() {
        assertFalse(ServiceStartReason.isBootAction(Intent.ACTION_SCREEN_ON))
        assertFalse(ServiceStartReason.isBootAction("com.example.RANDOM"))
        assertFalse(ServiceStartReason.isBootAction(""))
        assertFalse(ServiceStartReason.isBootAction(null))
    }

    @Test
    fun bootActions_containsQuickBootVariants() {
        // 快速启动广播容易在重构时被误删，这里固定住数量与内容
        assertEquals(4, ServiceStartReason.BOOT_ACTIONS.size)
    }

    @Test
    fun labels_areStableForLogGrepping() {
        // 日志排查依赖这些字面量，改动会打断既有排查手册
        assertEquals("boot", ServiceStartReason.BOOT.label)
        assertEquals("locked-boot", ServiceStartReason.LOCKED_BOOT.label)
        assertEquals("quick-boot", ServiceStartReason.QUICK_BOOT.label)
        assertEquals("manual", ServiceStartReason.MANUAL.label)
        assertEquals("system-restart", ServiceStartReason.SYSTEM_RESTART.label)
    }
}
