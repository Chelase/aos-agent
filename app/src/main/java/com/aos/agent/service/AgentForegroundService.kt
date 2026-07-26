package com.aos.agent.service

import android.app.ForegroundServiceStartNotAllowedException
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.aos.agent.MainActivity
import com.aos.agent.R

/**
 * Agent 常驻运行载体。本 Step 只保证「可靠起来、起不来不崩、能看清谁拉起的」，
 * 电源状态处理与状态 checkpoint 属 Batch 1 Step 5。
 */
class AgentForegroundService : Service() {

    private var startCount = 0
    private var foregroundActive = false

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
        Log.i(TAG, "Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startCount++
        val reason = ServiceStartReason.from(intent)
        Log.i(TAG, "onStartCommand reason=${reason.label} startId=$startId count=$startCount")

        if (reason == ServiceStartReason.SYSTEM_RESTART) {
            // 进程曾被系统杀死。真车上频繁出现说明保活策略不足，需要立项处理。
            Log.w(TAG, "Service restarted by system after process death")
        }

        // 幂等：已在前台则不重复调用 startForeground，避免刷新通知造成闪烁
        if (foregroundActive) {
            Log.d(TAG, "Already in foreground, skip startForeground")
            return START_STICKY
        }

        return if (enterForeground()) {
            START_STICKY
        } else {
            // 无法进入前台就不留半启动状态，直接退出，等下次入口重试
            stopSelf()
            START_NOT_STICKY
        }
    }

    override fun onDestroy() {
        foregroundActive = false
        Log.i(TAG, "Service destroyed after $startCount start command(s)")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null

    /** 进入前台，成功返回 true。失败只记录不抛出，由调用方决定退出。 */
    private fun enterForeground(): Boolean {
        return try {
            startForeground(NOTIFICATION_ID, createNotification())
            foregroundActive = true
            Log.i(TAG, "Entered foreground")
            true
        } catch (exception: ForegroundServiceStartNotAllowedException) {
            // Android 15+ 从 BOOT_COMPLETED 启动 specialUse 前台服务可能被系统拒绝
            Log.w(TAG, "Foreground start not allowed by system", exception)
            false
        } catch (exception: Exception) {
            Log.e(TAG, "Unexpected failure entering foreground", exception)
            false
        }
    }

    private fun createNotification(): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            REQUEST_OPEN_APP,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.service_running))
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    /** 创建通知渠道。重复调用是安全的：同 ID 重建只更新可变属性。 */
    private fun ensureNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: run {
            Log.w(TAG, "NotificationManager unavailable, channel not created")
            return
        }
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.notification_channel_description)
            enableVibration(false)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "aos_agent_runtime"
        private const val TAG = "AOSAgent.Service"
        private const val NOTIFICATION_ID = 1001
        private const val REQUEST_OPEN_APP = 0
    }
}
