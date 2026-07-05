package com.aos.agent.service

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test

class AgentForegroundServiceTest {
    @Test
    fun channelId_isStableForRuntimeNotifications() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("aos_agent_runtime", AgentForegroundService.CHANNEL_ID)
        assertEquals("com.aos.agent", context.packageName)
    }
}
