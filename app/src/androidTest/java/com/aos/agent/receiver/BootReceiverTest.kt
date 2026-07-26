package com.aos.agent.receiver

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.aos.agent.service.ServiceStartReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BootReceiverTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun createServiceIntent_targetsForegroundService() {
        val intent = BootReceiver.createServiceIntent(context)

        assertEquals("com.aos.agent.service.AgentForegroundService", intent.component?.className)
        assertNull(intent.getStringExtra(ServiceStartReason.EXTRA_START_REASON))
    }

    @Test
    fun createServiceIntent_carriesStartReasonWhenActionKnown() {
        val intent = BootReceiver.createServiceIntent(context, Intent.ACTION_BOOT_COMPLETED)

        assertEquals(
            Intent.ACTION_BOOT_COMPLETED,
            intent.getStringExtra(ServiceStartReason.EXTRA_START_REASON),
        )
        assertEquals(ServiceStartReason.BOOT, ServiceStartReason.from(intent))
    }

    @Test
    fun startReason_distinguishesEachBootEntry() {
        val quickBoot = BootReceiver.createServiceIntent(
            context,
            ServiceStartReason.ACTION_QUICKBOOT_POWERON,
        )
        val lockedBoot = BootReceiver.createServiceIntent(
            context,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
        )

        assertEquals(ServiceStartReason.QUICK_BOOT, ServiceStartReason.from(quickBoot))
        assertEquals(ServiceStartReason.LOCKED_BOOT, ServiceStartReason.from(lockedBoot))
    }

    @Test
    fun startReason_treatsNullIntentAsSystemRestart() {
        // START_STICKY 重启时 Intent 为 null，必须与用户主动启动区分
        assertEquals(ServiceStartReason.SYSTEM_RESTART, ServiceStartReason.from(null))
        assertEquals(
            ServiceStartReason.MANUAL,
            ServiceStartReason.from(BootReceiver.createServiceIntent(context)),
        )
    }
}
