package com.aos.agent.service

import android.content.Intent

/**
 * 服务启动来源。用于在日志中区分「谁把服务拉起来的」，
 * 排查开机自启失败与被系统重启时无需猜测。
 */
enum class ServiceStartReason(val label: String) {
    BOOT("boot"),
    LOCKED_BOOT("locked-boot"),
    QUICK_BOOT("quick-boot"),
    MANUAL("manual"),

    /** `onStartCommand` 收到 null Intent，表示进程被杀后由 START_STICKY 重启。 */
    SYSTEM_RESTART("system-restart"),
    ;

    companion object {
        /** 部分 OEM 快速启动使用的私有广播，不在 SDK 常量中。 */
        const val ACTION_QUICKBOOT_POWERON = "android.intent.action.QUICKBOOT_POWERON"

        /** 同上，HTC 系机型历史遗留写法，部分车机 ROM 沿用。 */
        const val ACTION_QUICKBOOT_POWERON_LEGACY = "com.htc.intent.action.QUICKBOOT_POWERON"

        /** BootReceiver 接受的广播动作全集。 */
        val BOOT_ACTIONS: Set<String> = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            ACTION_QUICKBOOT_POWERON,
            ACTION_QUICKBOOT_POWERON_LEGACY,
        )

        /** 判断广播动作是否属于开机自启入口。 */
        fun isBootAction(action: String?): Boolean = action in BOOT_ACTIONS

        /**
         * 解析启动来源。
         *
         * null Intent 表示进程被系统杀死后依 START_STICKY 重启，
         * 与用户主动启动必须区分——前者说明存在保活问题，需要关注。
         */
        fun from(intent: Intent?): ServiceStartReason {
            if (intent == null) {
                return SYSTEM_RESTART
            }
            return when (intent.getStringExtra(EXTRA_START_REASON)) {
                Intent.ACTION_BOOT_COMPLETED -> BOOT
                Intent.ACTION_LOCKED_BOOT_COMPLETED -> LOCKED_BOOT
                ACTION_QUICKBOOT_POWERON, ACTION_QUICKBOOT_POWERON_LEGACY -> QUICK_BOOT
                else -> MANUAL
            }
        }

        const val EXTRA_START_REASON = "com.aos.agent.extra.START_REASON"
    }
}
