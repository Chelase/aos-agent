package com.aos.agent.system.vehicle

import com.aos.agent.core.tools.DegradeStatus
import com.aos.agent.system.BatterySnapshot
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleBatteryTest {

    private fun allowlist(unit: String? = null, vararg names: String): VehiclePropertyAllowlist =
        VehiclePropertyAllowlist.parse(
            buildString {
                append("{")
                if (unit != null) append("\"battery_level_unit\":\"").append(unit).append("\",")
                append("\"properties\":[")
                names.forEachIndexed { index, name ->
                    if (index > 0) append(",")
                    append(
                        """{"name":"$name","category":"ENERGY_MANAGEMENT",
                           "permission":"android.car.permission.CAR_ENERGY",
                           "protection":"dangerous","access":"read","unit":"Wh","description":"测试"}"""
                            .replace("\n", " "),
                    )
                }
                append("]}")
            },
        )

    private fun readerWith(
        unit: String?,
        vararg values: Pair<String, VehicleReading>,
    ): VehicleBatteryReader = VehicleBatteryReader(
        FakeVehicleReader(values.toMap()),
        allowlist(unit, LEVEL, CURRENT_CAPACITY, INFO_CAPACITY),
    )

    private fun readerWith(vararg values: Pair<String, VehicleReading>): VehicleBatteryReader =
        readerWith(unit = null, values = values)

    @Test
    fun energyLevelIsDividedByCapacity() {
        val reading = runBlocking {
            readerWith(
                LEVEL to VehicleReading.Number(8000.0),
                CURRENT_CAPACITY to VehicleReading.Number(20000.0),
            ).read()
        }

        assertEquals(40, reading.levelPercent)
        assertNull(reading.unavailableStatus)
    }

    @Test
    fun capacityFallsBackToInfoPropertyWhenCurrentIsUnreadable() {
        val reading = runBlocking {
            readerWith(
                LEVEL to VehicleReading.Number(150000.0),
                CURRENT_CAPACITY to VehicleReading.Missing(DegradeStatus.NoVhal, "本机无该属性"),
                INFO_CAPACITY to VehicleReading.Number(300000.0),
            ).read()
        }

        assertEquals(50, reading.levelPercent)
    }

    @Test
    fun missingCapacityIsNotShownAsZeroPercent() {
        val reading = runBlocking {
            readerWith(LEVEL to VehicleReading.Number(8000.0)).read()
        }

        assertNull(reading.levelPercent)
        assertEquals(DegradeStatus.Unsupported, reading.unavailableStatus)
        assertTrue(reading.detail!!.contains("容量"))
    }

    @Test
    fun permissionDeniedOnLevelIsCarriedThrough() {
        val reading = runBlocking {
            readerWith(
                LEVEL to VehicleReading.Missing(
                    DegradeStatus.PermissionDenied,
                    "未授予 android.car.permission.CAR_ENERGY",
                ),
            ).read()
        }

        assertNull(reading.levelPercent)
        assertEquals(DegradeStatus.PermissionDenied, reading.unavailableStatus)
        assertEquals("未授予 android.car.permission.CAR_ENERGY", reading.detail)
    }

    @Test
    fun percentUnitReadsLevelDirectlyWithoutCapacity() {
        val reading = runBlocking {
            readerWith("percent", LEVEL to VehicleReading.Number(75.4)).read()
        }

        assertEquals(75, reading.levelPercent)
    }

    @Test
    fun percentUnitRejectsEnergyRangeValue() {
        // 配成 percent 但厂商给的还是 Wh：越界就是不认识，不能夹紧成 100% 糊弄过去
        val reading = runBlocking {
            readerWith("percent", LEVEL to VehicleReading.Number(150000.0)).read()
        }

        assertNull(reading.levelPercent)
        assertEquals(DegradeStatus.Unsupported, reading.unavailableStatus)
        assertTrue(reading.detail!!.contains("battery_level_unit"))
    }

    @Test
    fun unknownPropertyNameDegradesInsteadOfGuessing() {
        val reading = runBlocking {
            VehicleBatteryReader(FakeVehicleReader(emptyMap()), allowlist(LEVEL)).read()
        }

        assertNull(reading.levelPercent)
        assertTrue(reading.detail!!.contains("不在 allowlist"))
    }

    @Test
    fun allowlistDefaultsToEnergyUnitAndParsesPercentKey() {
        assertEquals(BatteryLevelUnit.Wh, allowlist().batteryLevelUnit)
        assertEquals(BatteryLevelUnit.Percent, allowlist(unit = "percent").batteryLevelUnit)
        // 写错键值不能变成"未知口径"，按 AOSP 定义当 Wh 处理
        assertEquals(BatteryLevelUnit.Wh, allowlist(unit = "litre").batteryLevelUnit)
    }

    @Test
    fun energyRatioOutsideRangeIsClamped() {
        assertEquals(100, VehicleBatteryReading.percentFromEnergy(300000.0, 200000.0))
        assertEquals(0, VehicleBatteryReading.percentFromEnergy(0.0, 200000.0))
    }

    @Test
    fun vehicleLevelOverridesEmptyBroadcast() {
        // 五菱星光实测：广播恒 0，VHAL 才是真值
        val merged = BatterySnapshot(0, null).withVehicleLevel(40)

        assertEquals(40, merged.levelPercent)
    }

    @Test
    fun broadcastValueSurvivesWhenVehicleLevelUnreadable() {
        val merged = BatterySnapshot(66, true).withVehicleLevel(null)

        assertEquals(66, merged.levelPercent)
        assertEquals(true, merged.charging)
    }

    private companion object {
        const val LEVEL = "EV_BATTERY_LEVEL"
        const val CURRENT_CAPACITY = "EV_CURRENT_BATTERY_CAPACITY"
        const val INFO_CAPACITY = "INFO_EV_BATTERY_CAPACITY"
    }
}
