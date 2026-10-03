package com.aos.agent.service

import android.app.ForegroundServiceStartNotAllowedException
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.aos.agent.MainActivity
import com.aos.agent.R
import com.aos.agent.core.voice.WakeWordEngine
import com.aos.agent.core.voice.WakeWordGate
import com.aos.agent.core.voice.WakeWordPhrases
import com.aos.agent.data.store.VoiceSettingsStore
import com.aos.agent.system.voice.VoskWakeWordEngine
import com.aos.agent.system.voice.WakeModelInstaller
import com.aos.agent.system.voice.WakeModelState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * 唤醒监听的启动入口（界面侧与服务侧唯一的接缝）。
 *
 * 服务已经在跑时 `onStartCommand` 只会重算模型状态，不会重复进前台；
 * 服务没起来时才真正把它拉起来。
 */
object AgentWakeWatcher {
    fun requestRefresh(context: Context) {
        val intent = Intent(context, AgentForegroundService::class.java)
        runCatching { ContextCompat.startForegroundService(context, intent) }
            .onFailure { Log.w("AOSAgent.Service", "唤醒监听拉起失败", it) }
    }
}

/**
 * Agent 常驻运行载体。本 Step 只保证「可靠起来、起不来不崩、能看清谁拉起的」，
 * 电源状态处理与状态 checkpoint 属 Batch 1 Step 5。
 *
 * 语音 Phase B 起，这里还托管离线唤醒（KWS）：常驻听只在"开关开 + 模型就绪 + 已授权"
 * 三条同时成立时开始，任一不成立立刻停并释放模型；界面正在跑语音会话时由 [WakeWordGate] 让路。
 */
class AgentForegroundService : Service() {

    private var startCount = 0
    private var foregroundActive = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val settingsStore by lazy { VoiceSettingsStore(this) }
    private val modelInstaller by lazy { WakeModelInstaller(this, scope) }
    private var wakeWatchJob: Job? = null
    private var wakeEngine: WakeWordEngine? = null
    private var wakePhrases: List<String> = emptyList()

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
            modelInstaller.refresh()
            return START_STICKY
        }

        return if (enterForeground()) {
            watchWakeWord()
            START_STICKY
        } else {
            // 无法进入前台就不留半启动状态，直接退出，等下次入口重试
            stopSelf()
            START_NOT_STICKY
        }
    }

    override fun onDestroy() {
        foregroundActive = false
        stopWakeWord()
        wakeWatchJob?.cancel()
        wakeWatchJob = null
        scope.cancel()
        Log.i(TAG, "Service destroyed after $startCount start command(s)")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null

    // ---- 离线唤醒（KWS）----

    /** 幂等：只装一次监听协程，之后开关/模型/名字/说法/闸门任一变化都会自动重算该不该听。 */
    private fun watchWakeWord() {
        if (wakeWatchJob != null) return
        wakeWatchJob = scope.launch {
            combine(
                settingsStore.settings,
                modelInstaller.state,
                WakeWordGate.paused,
            ) { settings, model, paused ->
                WakePlan(
                    listening = settings.wakeWordEnabled && model is WakeModelState.Ready && !paused,
                    phrases = WakeWordPhrases.build(settings.agentName, settings.wakeVariants),
                )
            }.collect { plan ->
                when {
                    !plan.listening || plan.phrases.isEmpty() -> stopWakeWord()
                    wakePhrases != plan.phrases -> {
                        // 说法变了要重建识别器：受限语法是构造时定死的
                        stopWakeWord()
                        startWakeWord(plan.phrases)
                    }
                }
            }
        }
    }

    private data class WakePlan(val listening: Boolean, val phrases: List<String>)

    private fun startWakeWord(phrases: List<String>) {
        val modelPath = modelInstaller.readyModelPath() ?: return
        val engine = VoskWakeWordEngine(
            context = this,
            modelPath = modelPath,
            phrases = phrases,
            scope = scope,
        )
        wakeEngine = engine
        wakePhrases = phrases
        engine.start(
            onWake = { onWakeWordHeard() },
            onError = { error ->
                Log.w(TAG, "Wake word stopped: $error")
                stopWakeWord()
            },
        )
        refreshNotification()
        Log.i(TAG, "Wake word listening started")
    }

    private fun stopWakeWord() {
        val engine = wakeEngine ?: return
        wakeEngine = null
        wakePhrases = emptyList()
        engine.release()
        refreshNotification()
        Log.i(TAG, "Wake word listening stopped")
    }

    /**
     * 命中分发：进程内事件让活着的界面直接进聆听；Activity 不在栈上时再补一次启动。
     *
     * 后台拉起界面在 Android 10+ 会被系统拒绝，所以这里 `runCatching`——被拒时唤醒仍然
     * 通过事件流生效（只要界面还活着），不能因为打不开就崩掉服务。
     */
    private fun onWakeWordHeard() {
        Log.i(TAG, "Wake word matched")
        WakeWordGate.emitWake()
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_VOICE_WAKE, true)
        }
        runCatching { startActivity(intent) }
            .onFailure { Log.w(TAG, "唤醒后未能拉起界面（系统可能拒绝后台启动）", it) }
    }

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
        // 常驻麦克风必须在通知里说清楚在听什么，不能只写"运行中"
        val contentText = if (wakeEngine != null) {
            getString(R.string.notification_wake_listening, WakeWordPhrases.displayOf(wakePhrases))
        } else {
            getString(R.string.service_running)
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(contentText)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    /** 唤醒监听的起停要立刻反映到通知文案上（同 ID 重建只更新可变字段）。 */
    private fun refreshNotification() {
        if (!foregroundActive) return
        runCatching {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, createNotification())
        }.onFailure { Log.w(TAG, "通知刷新失败", it) }
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
