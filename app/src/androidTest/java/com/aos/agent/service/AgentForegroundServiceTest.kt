package com.aos.agent.service

import android.app.NotificationManager
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import com.aos.agent.receiver.BootReceiver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class AgentForegroundServiceTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun channelId_isStableForRuntimeNotifications() {
        assertEquals("aos_agent_runtime", AgentForegroundService.CHANNEL_ID)
        assertEquals("com.aos.agent", context.packageName)
    }

    @Test
    fun repeatedStart_doesNotCrash() {
        // 幂等验证：重复下发启动命令不得崩溃（车机上开机广播可能多次到达）
        repeat(3) {
            ContextCompat.startForegroundService(
                context,
                BootReceiver.createServiceIntent(context),
            )
        }
    }

    @Test
    fun notificationChannel_isRegisteredAfterServiceStart() {
        ContextCompat.startForegroundService(context, BootReceiver.createServiceIntent(context))
        Thread.sleep(CHANNEL_WAIT_MS)

        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = manager.getNotificationChannel(AgentForegroundService.CHANNEL_ID)

        assertNotNull("通知渠道应在服务 onCreate 时建立", channel)
        assertEquals(NotificationManager.IMPORTANCE_LOW, channel.importance)
    }

    private companion object {
        const val CHANNEL_WAIT_MS = 1500L
    }
}
