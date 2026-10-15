package com.aos.agent.system.vehicle

import com.aos.agent.core.tools.DegradeStatus
import kotlin.math.roundToInt

/**
 * VHAL 动力电池读数。[levelPercent] 为 null 表示没读出来，[unavailableStatus] 与 [detail]
 * 说明为什么，界面才敢说"不可用"而不是演一个数字。
 *
 * 为什么要自己算：AOSP 把 `EV_BATTERY_LEVEL` 定义成**剩余能量（Wh）**而不是百分比
 * （模拟器实测 150000.0），而这类型程车机的 `dumpsys battery` 是空的（level 0）。
 * 直接拿去显示会把一辆电满的车演成 `150000%` 或 `0%`。
 * 口径来自 allowlist 的 `battery_level_unit`：换车型改数据，不改这里的分支。
 */
data class VehicleBatteryReading(
    val levelPercent: Int?,
    val unavailableStatus: DegradeStatus? = null,
    val detail: String? = null,
) {
    companion object {
        /** 能量口径：SOC = 剩余能量 / 可用容量。 */
        fun percentFromEnergy(levelWh: Double, capacityWh: Double): Int =
            (levelWh / capacityWh * 100.0).roundToInt().coerceIn(0, 100)

        /** 百分比口径：厂商直接给 0-100。越界说明口径不对，不夹紧——夹紧会把配置错误演成合理读数。 */
        fun percentFromRawLevel(level: Double): Int? =
            level.takeIf { it in 0.0..100.0 }?.roundToInt()?.coerceIn(0, 100)
    }
}

/**
 * 从车辆属性算 SOC。属性名与所需权限都取自 [VehiclePropertyAllowlist]，本类不硬编码厂商差异。
 */
class VehicleBatteryReader(
    private val vehicle: VehicleReader,
    private val allowlist: VehiclePropertyAllowlist,
) {
    suspend fun read(): VehicleBatteryReading {
        val level = read(LEVEL_PROPERTY)
        val levelValue = (level as? VehicleReading.Number)?.value
            ?: return VehicleBatteryReading(null, statusOf(level), detailOf(LEVEL_PROPERTY, level))

        return when (allowlist.batteryLevelUnit) {
            BatteryLevelUnit.Percent -> VehicleBatteryReading.percentFromRawLevel(levelValue)?.let {
                VehicleBatteryReading(it)
            } ?: VehicleBatteryReading(
                null,
                DegradeStatus.Unsupported,
                "$LEVEL_PROPERTY=$levelValue 不在 0-100 内，battery_level_unit 需要核实",
            )

            BatteryLevelUnit.Wh -> firstCapacity()?.let { capacity ->
                VehicleBatteryReading(VehicleBatteryReading.percentFromEnergy(levelValue, capacity))
            } ?: VehicleBatteryReading(
                null,
                DegradeStatus.Unsupported,
                "$LEVEL_PROPERTY=$levelValue Wh 但容量属性都读不到，无法换算百分比",
            )
        }
    }

    /** 分母按"实测可读优先"排：模拟器量到 `EV_CURRENT_BATTERY_CAPACITY`，`INFO_*` 只当兜底。 */
    private suspend fun firstCapacity(): Double? =
        CAPACITY_PROPERTIES.firstNotNullOfOrNull { name ->
            (read(name) as? VehicleReading.Number)?.value?.takeIf { it > 0.0 }
        }

    private suspend fun read(name: String): VehicleReading =
        allowlist.spec(name)
            ?.let { vehicle.read(it) }
            ?: VehicleReading.Missing(DegradeStatus.Unsupported, "$name 不在 allowlist 中")

    private fun statusOf(reading: VehicleReading): DegradeStatus = when (reading) {
        is VehicleReading.Missing -> reading.status
        else -> DegradeStatus.Unsupported
    }

    private fun detailOf(name: String, reading: VehicleReading): String = when (reading) {
        is VehicleReading.Missing -> reading.detail ?: "$name 读不到"
        is VehicleReading.Text -> "$name 返回文本，不是数值"
        is VehicleReading.Number -> "$name 数值异常"
    }

    private companion object {
        const val LEVEL_PROPERTY = "EV_BATTERY_LEVEL"
        val CAPACITY_PROPERTIES = listOf("EV_CURRENT_BATTERY_CAPACITY", "INFO_EV_BATTERY_CAPACITY")
    }
}
