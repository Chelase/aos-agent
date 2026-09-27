package com.aos.agent.system.vehicle

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VehiclePropertyAllowlistTest {

    private fun shippedAsset(): String =
        File("src/main/assets/vehicle/vehicle_properties.json").readText()

    @Test
    fun shippedAllowlistParses() {
        val allowlist = VehiclePropertyAllowlist.parse(shippedAsset())

        assertTrue("可用字段不该为空", allowlist.readable.isNotEmpty())
        assertTrue(allowlist.names.contains("SPEED"))
        assertEquals("m/s", allowlist.spec("SPEED")?.unit)
        assertEquals("dangerous", allowlist.spec("SPEED")?.protection)
    }

    /** 写属性与特权只读属性都不该进可用集，且原因要留下来可观测。 */
    @Test
    fun writeAndPrivilegedEntriesAreSkippedWithReason() {
        val allowlist = VehiclePropertyAllowlist.parse(shippedAsset())

        assertNull(allowlist.spec("HVAC_TEMPERATURE_SET"))
        assertNull(allowlist.spec("TYRE_PRESSURE"))
        assertTrue(allowlist.skipped.any { it.name == "HVAC_TEMPERATURE_SET" && it.reason.contains("写属性") })
        assertTrue(allowlist.skipped.any { it.name == "TYRE_PRESSURE" && it.reason.contains("签名") })
    }

    @Test
    fun privilegedReadBecomesAvailableOnlyWhenSystemSigned() {
        val allowlist = VehiclePropertyAllowlist.parse(shippedAsset(), allowPrivilegedRead = true)

        assertTrue(allowlist.names.contains("TYRE_PRESSURE"))
        assertNull("写属性即使开了特权读取也不注册", allowlist.spec("HVAC_TEMPERATURE_SET"))
    }

    @Test
    fun malformedEntriesAreSkippedNotFatal() {
        val raw = """
            {"properties":[
              {"name":"GOOD","category":"VEHICLE_INFO","permission":"p","protection":"normal","access":"read","description":"d"},
              {"name":"NO_CATEGORY","permission":"p","protection":"normal","access":"read","description":"d"},
              {"name":"BAD_ACCESS","category":"c","permission":"p","protection":"normal","access":"toggle","description":"d"},
              {"permission":"p","category":"c","protection":"normal","access":"read","description":"d"}
            ]}
        """.trimIndent()

        val allowlist = VehiclePropertyAllowlist.parse(raw)

        assertEquals(listOf("GOOD"), allowlist.readable.map { it.name })
        assertEquals(3, allowlist.skipped.size)
    }

    @Test
    fun nonObjectRootDegradesToEmpty() {
        assertEquals(emptyList<VehiclePropertySpec>(), VehiclePropertyAllowlist.parse("[1,2]").readable)
    }
}
