package com.aos.agent.system

import android.content.Context
import android.os.Build

/**
 * 运行环境能力探测。
 *
 * 目标是"任意车机可运行"，所以任何依赖 API 版本或系统特性的功能都必须先问这里，
 * 缺失时结构化降级，而不是假定存在（见 mechanisms/architecture-overview.md 关键约束 6）。
 * 工具层、UI 与后续 MCP 来源共用这一份判断，不许各写各的 `SDK_INT` 比较。
 */
class Capabilities private constructor(
    val hasCarApi: Boolean,
    val hasPerAppLocale: Boolean,
    val hasAppFunctions: Boolean,
) {
    companion object {
        private const val AUTOMOTIVE_FEATURE = "android.hardware.type.automotive"
        private const val CAR_CLASS = "android.car.Car"

        fun detect(context: Context): Capabilities = Capabilities(
            hasCarApi = context.packageManager.hasSystemFeature(AUTOMOTIVE_FEATURE) &&
                runCatching { Class.forName(CAR_CLASS) }.isSuccess,
            hasPerAppLocale = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU,
            hasAppFunctions = Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA,
        )
    }
}
