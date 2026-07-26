package com.aos.agent.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.aos.agent.service.AgentForegroundService
import com.aos.agent.service.ServiceStartReason

/**
 * 开机自启入口。只做委托，不做任何耗时操作（机制文档关键约束 1）。
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action
        if (!ServiceStartReason.isBootAction(action)) {
            Log.d(TAG, "Ignored broadcast action=$action")
            return
        }

        val serviceIntent = createServiceIntent(context, action)
        try {
            ContextCompat.startForegroundService(context, serviceIntent)
            Log.i(TAG, "Delegated to AgentForegroundService, action=$action")
        } catch (exception: Exception) {
            // Android 15+ 从广播启动前台服务可能被拒。此处不能让异常抛出广播边界，
            // 否则系统记为接收器异常；服务侧另有兜底，见 AgentForegroundService。
            Log.w(TAG, "Unable to start foreground service, action=$action", exception)
        }
    }

    companion object {
        private const val TAG = "AOSAgent.Boot"

        fun createServiceIntent(context: Context, action: String? = null): Intent {
            return Intent(context, AgentForegroundService::class.java).apply {
                if (action != null) {
                    putExtra(ServiceStartReason.EXTRA_START_REASON, action)
                }
            }
        }
    }
}
