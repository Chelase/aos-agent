package com.aos.agent.system

import android.os.BatteryManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class SystemInfoProviderTest {

    /** 可配置的测试替身，默认返回空值以触发 fallback 分支。 */
    private class FakeReader(
        private val version: String = "",
        private val sdk: Int = 34,
        private val vendor: String = "",
        private val brandName: String = "",
        private val modelName: String = "",
        private val deviceName: String = "",
        private val automotive: Boolean = false,
        private val transport: NetworkTransport = NetworkTransport.UNAVAILABLE,
        private val batterySnapshot: BatterySnapshot = BatterySnapshot(null, null),
    ) : SystemInfoReader {
        override fun androidVersion(): String = version
        override fun sdkInt(): Int = sdk
        override fun manufacturer(): String = vendor
        override fun brand(): String = brandName
        override fun model(): String = modelName
        override fun device(): String = deviceName
        override fun isAutomotive(): Boolean = automotive
        override fun networkTransport(): NetworkTransport = transport
        override fun battery(): BatterySnapshot = batterySnapshot
    }

    @Test
    fun collect_usesFallbackWhenReaderReturnsBlankValues() {
        val info = SystemInfoProvider(FakeReader()).collect()

        assertEquals("Unavailable", info.androidVersion)
        assertEquals("Unavailable", info.manufacturer)
        assertEquals("Unavailable", info.brand)
        assertEquals("Unavailable", info.model)
        assertEquals("Unavailable", info.device)
        assertFalse(info.isAutomotive)
    }

    @Test
    fun collect_passesThroughNetworkTransport() {
        val info = SystemInfoProvider(FakeReader(transport = NetworkTransport.WIFI)).collect()

        assertEquals(NetworkTransport.WIFI, info.networkTransport)
    }

    @Test
    fun collect_keepsUnavailableDistinctFromDisconnected() {
        // UNAVAILABLE 表示读不到（服务缺失/权限不足），NONE 表示确实没有网络，两者不可混淆
        val unavailable = SystemInfoProvider(
            FakeReader(transport = NetworkTransport.UNAVAILABLE),
        ).collect()
        val disconnected = SystemInfoProvider(
            FakeReader(transport = NetworkTransport.NONE),
        ).collect()

        assertEquals(NetworkTransport.UNAVAILABLE, unavailable.networkTransport)
        assertEquals(NetworkTransport.NONE, disconnected.networkTransport)
    }

    @Test
    fun summary_excludesDynamicNetworkState() {
        // summary 只含静态设备信息，避免首页展示过期网络状态
        val info = SystemInfoProvider(
            FakeReader(
                version = "14",
                vendor = "SAIC-GM-Wuling",
                modelName = "星光",
                automotive = true,
                transport = NetworkTransport.WIFI,
            ),
        ).collect()

        assertEquals(
            "Android 14 (SDK 34) · SAIC-GM-Wuling / 星光 · Automotive=true",
            info.summary,
        )
    }

    @Test
    fun collect_carriesBatterySnapshotThrough() {
        val info = SystemInfoProvider(
            FakeReader(batterySnapshot = BatterySnapshot(66, true)),
        ).collect()

        assertEquals(66, info.battery.levelPercent)
        assertEquals(true, info.battery.charging)
    }

    @Test
    fun collect_batteryDefaultsToUnavailableWithoutReaderData() {
        val info = SystemInfoProvider(FakeReader()).collect()

        assertNull(info.battery.levelPercent)
        assertNull(info.battery.charging)
    }

    @Test
    fun batterySnapshot_chargingStatuses() {
        val charging = BatterySnapshot.from(BatteryManager.BATTERY_STATUS_CHARGING, 40, 100)
        val full = BatterySnapshot.from(BatteryManager.BATTERY_STATUS_FULL, 100, 100)

        assertEquals(40, charging.levelPercent)
        assertEquals(true, charging.charging)
        assertEquals(100, full.levelPercent)
        assertEquals(true, full.charging)
    }

    @Test
    fun batterySnapshot_dischargingStatuses() {
        val discharging = BatterySnapshot.from(BatteryManager.BATTERY_STATUS_DISCHARGING, 80, 100)
        val notCharging = BatterySnapshot.from(BatteryManager.BATTERY_STATUS_NOT_CHARGING, 80, 100)

        assertEquals(false, discharging.charging)
        assertEquals(false, notCharging.charging)
    }

    @Test
    fun batterySnapshot_unknownOrMissingFieldsStayNull() {
        val unknown = BatterySnapshot.from(BatteryManager.BATTERY_STATUS_UNKNOWN, null, null)
        val missing = BatterySnapshot.from(null, null, null)
        val badScale = BatterySnapshot.from(BatteryManager.BATTERY_STATUS_CHARGING, 50, 0)

        assertNull(unknown.charging)
        assertNull(unknown.levelPercent)
        assertNull(missing.levelPercent)
        assertNull(missing.charging)
        assertNull(badScale.levelPercent)
    }

    @Test
    fun batterySnapshot_percentClampedToValidRange() {
        val over = BatterySnapshot.from(BatteryManager.BATTERY_STATUS_DISCHARGING, 120, 100)

        assertEquals(100, over.levelPercent)
    }
}
