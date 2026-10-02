package com.aos.agent.system

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build

/**
 * 当前网络承载类型。用枚举而非字符串，便于 UI 侧映射到本地化文案。
 * [UNAVAILABLE] 表示无法读取（服务缺失或权限不足），与「确实没有网络」的 [NONE] 区分。
 */
enum class NetworkTransport {
    WIFI,
    CELLULAR,
    ETHERNET,
    OTHER,
    NONE,
    UNAVAILABLE,
}

interface SystemInfoReader {
    fun androidVersion(): String
    fun sdkInt(): Int
    fun manufacturer(): String
    fun brand(): String
    fun model(): String
    fun device(): String
    fun isAutomotive(): Boolean
    fun networkTransport(): NetworkTransport
    fun battery(): BatterySnapshot
}

/**
 * 电池瞬时状态。字段为 null 表示本次读取不可用（无广播、字段缺失），UI 渲染「不可用」，
 * 与「确实在放电」的 false 区分。
 */
data class BatterySnapshot(
    val levelPercent: Int?,
    val charging: Boolean?,
) {
    companion object {
        /**
         * 纯函数：从 ACTION_BATTERY_CHANGED 的关键字段解析，便于无 Android 环境单测。
         * status 用 [BatteryManager] 常量（编译期内联，单测可用）。
         */
        fun from(status: Int?, level: Int?, scale: Int?): BatterySnapshot {
            val percent = if (level != null && scale != null && scale > 0) {
                (level * 100 / scale).coerceIn(0, 100)
            } else {
                null
            }
            val charging = when (status) {
                BatteryManager.BATTERY_STATUS_CHARGING,
                BatteryManager.BATTERY_STATUS_FULL,
                -> true
                BatteryManager.BATTERY_STATUS_DISCHARGING,
                BatteryManager.BATTERY_STATUS_NOT_CHARGING,
                -> false
                else -> null
            }
            return BatterySnapshot(percent, charging)
        }
    }
}

/**
 * 一次性采集的系统信息快照。
 *
 * [networkTransport] 与 [battery] 是动态值，本快照只反映采集瞬间的状态；
 * 系统面板页负责定时重采（Batch 1 Step 2）。[summary] 只包含静态设备信息，
 * 避免在首页展示过期网络状态。
 */
data class SystemInfo(
    val androidVersion: String,
    val sdkInt: Int,
    val manufacturer: String,
    val brand: String,
    val model: String,
    val device: String,
    val isAutomotive: Boolean,
    val networkTransport: NetworkTransport,
    val battery: BatterySnapshot = BatterySnapshot(null, null),
) {
    val summary: String = buildString {
        append("Android ")
        append(androidVersion)
        append(" (SDK ")
        append(sdkInt)
        append(") · ")
        append(manufacturer)
        append(" / ")
        append(model)
        append(" · Automotive=")
        append(isAutomotive)
    }
}

class AndroidSystemInfoReader(
    private val context: Context,
) : SystemInfoReader {
    override fun androidVersion(): String = Build.VERSION.RELEASE.orUnavailable()
    override fun sdkInt(): Int = Build.VERSION.SDK_INT
    override fun manufacturer(): String = Build.MANUFACTURER.orUnavailable()
    override fun brand(): String = Build.BRAND.orUnavailable()
    override fun model(): String = Build.MODEL.orUnavailable()
    override fun device(): String = Build.DEVICE.orUnavailable()
    override fun isAutomotive(): Boolean = context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)

    override fun networkTransport(): NetworkTransport {
        val manager = context.getSystemService(ConnectivityManager::class.java)
            ?: return NetworkTransport.UNAVAILABLE
        val network = manager.activeNetwork ?: return NetworkTransport.NONE
        val capabilities = manager.getNetworkCapabilities(network)
            ?: return NetworkTransport.UNAVAILABLE

        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkTransport.WIFI
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkTransport.CELLULAR
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkTransport.ETHERNET
            else -> NetworkTransport.OTHER
        }
    }

    override fun battery(): BatterySnapshot {
        // 粘性广播无需注册常驻 receiver，读取瞬间状态即可（无权限要求）
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?: return BatterySnapshot(null, null)
        return BatterySnapshot.from(
            status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1).takeIf { it != -1 },
            level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1).takeIf { it != -1 },
            scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1).takeIf { it != -1 },
        )
    }

    private fun String?.orUnavailable(): String = takeIf { !it.isNullOrBlank() } ?: "Unavailable"
}

class SystemInfoProvider(
    private val reader: SystemInfoReader,
) {
    fun collect(): SystemInfo {
        return SystemInfo(
            androidVersion = safeValue(reader.androidVersion()),
            sdkInt = reader.sdkInt(),
            manufacturer = safeValue(reader.manufacturer()),
            brand = safeValue(reader.brand()),
            model = safeValue(reader.model()),
            device = safeValue(reader.device()),
            isAutomotive = reader.isAutomotive(),
            networkTransport = reader.networkTransport(),
            battery = reader.battery(),
        )
    }

    private fun safeValue(value: String?): String {
        return value?.takeIf { it.isNotBlank() } ?: "Unavailable"
    }
}
