package com.aos.agent.system.update

import android.content.Context

/** 自身版本信息；读不到时给占位值而不是抛，界面必须还能画出来。 */
data class AppVersionInfo(val versionName: String, val versionCode: Int)

object AppVersionReader {

    fun read(context: Context): AppVersionInfo = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        AppVersionInfo(
            versionName = info.versionName?.takeIf { it.isNotBlank() } ?: "?",
            versionCode = info.longVersionCode.toInt(),
        )
    }.getOrElse { AppVersionInfo("?", 0) }
}
