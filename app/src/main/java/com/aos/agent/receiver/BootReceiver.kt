package com.aos.agent.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.aos.agent.service.AgentForegroundService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return

        val serviceIntent = createServiceIntent(context)
        try {
            ContextCompat.startForegroundService(context, serviceIntent)
            Log.i(TAG, "BootReceiver delegated to AgentForegroundService")
        } catch (exception: Exception) {
            Log.w(TAG, "Unable to start foreground service from boot", exception)
        }
    }

    companion object {
        private const val TAG = "BootReceiver"

        fun createServiceIntent(context: Context): Intent {
            return Intent(context, AgentForegroundService::class.java)
        }
    }
}
