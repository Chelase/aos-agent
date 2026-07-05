package com.aos.agent.receiver

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test

class BootReceiverTest {
    @Test
    fun createServiceIntent_targetsForegroundService() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent = BootReceiver.createServiceIntent(context)

        assertEquals("com.aos.agent.service.AgentForegroundService", intent.component?.className)
    }
}
