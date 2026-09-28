package com.aos.agent.system.vehicle

import android.content.pm.PackageManager
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aos.agent.system.Capabilities
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 属性级可读性实测：把 allowlist 里每个只读字段在真机上跑一遍，
 * 结果打进 logcat（TAG=VehicleProbe）供人读，断言只保证"不崩、都有结构化结论"。
 *
 * 这一份测试是 step3 子计划 §1 要求的实测证据来源——权限分级可以查文档，
 * "这台设备到底给不给读某个属性"只能实测。
 *
 * 授权走带外：`androidx.test` 的 GrantPermissionRule 在 AAOS 上给不上 car 权限，
 * 所以跑两遍——不授权一遍（应得 permission_denied），
 * `adb shell pm grant` 之后再 `am instrument` 一遍（应得真实数值）。
 */
@RunWith(AndroidJUnit4::class)
class VehiclePropertyProbeTest {

    @Test
    fun probeEveryReadableFieldWithoutThrowing() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue("测试环境应为车机", Capabilities.detect(context).hasCarApi)

        val raw = context.assets.open(ALLOWLIST_ASSET).bufferedReader().use { it.readText() }
        val allowlist = VehiclePropertyAllowlist.parse(raw)
        assertTrue("allowlist 不该是空", allowlist.readable.isNotEmpty())

        val results = AndroidVehicleReader(context).use { reader ->
            allowlist.readable.map { spec -> spec.name to runBlocking { reader.read(spec) } }
        }

        results.forEach { (name, reading) ->
            Log.i(TAG, "$name -> $reading")
            assertNotNull(reading)
        }
        // 授权状态由应用自己报，避免靠 dumpsys 猜：撤销是否真生效决定了这条结论能不能写。
        allowlist.readable.map { it.permission }.distinct().sorted().forEach { permission ->
            val granted = context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
            Log.i(TAG, "PERMISSION $permission -> granted=$granted")
        }
        File(context.externalCacheDir ?: context.cacheDir, "vehicle-probe.txt").writeText(
            results.joinToString("\n") { (name, reading) -> "$name\t$reading" },
        )
    }

    private companion object {
        const val TAG = "VehicleProbe"
        const val ALLOWLIST_ASSET = "vehicle/vehicle_properties.json"
    }
}
