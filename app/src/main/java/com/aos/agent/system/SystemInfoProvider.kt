package com.aos.agent.system

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

interface SystemInfoReader {
    fun androidVersion(): String
    fun sdkInt(): Int
    fun manufacturer(): String
    fun brand(): String
    fun model(): String
    fun device(): String
    fun isAutomotive(): Boolean
}

data class SystemInfo(
    val androidVersion: String,
    val sdkInt: Int,
    val manufacturer: String,
    val brand: String,
    val model: String,
    val device: String,
    val isAutomotive: Boolean,
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
        )
    }

    private fun safeValue(value: String?): String {
        return value?.takeIf { it.isNotBlank() } ?: "Unavailable"
    }
}
