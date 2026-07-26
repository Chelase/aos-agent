package com.aos.agent.system

import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
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
}

/**
 * 一次性采集的系统信息快照。
 *
 * [networkTransport] 是动态值，本快照只反映采集瞬间的状态，不会自动刷新；
 * [summary] 因此只包含静态设备信息，避免在首页展示过期网络状态。
 * 定时刷新机制属 Batch 1 Step 2（系统感知面板）。
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
        )
    }

    private fun safeValue(value: String?): String {
        return value?.takeIf { it.isNotBlank() } ?: "Unavailable"
    }
}
