package com.aos.agent.core.tools

import com.aos.agent.system.vehicle.FakeVehicleReader
import com.aos.agent.system.vehicle.UnavailableVehicleReader
import com.aos.agent.system.vehicle.VehiclePropertyAllowlist
import com.aos.agent.system.vehicle.VehicleReading
import com.aos.agent.system.vehicle.VehicleReader
import java.io.File
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleBasicToolTest {

    private val allowlist = VehiclePropertyAllowlist.parse(
        File("src/main/assets/vehicle/vehicle_properties.json").readText(),
    )

    private fun fieldsOf(result: ToolResult): JsonObject = (result as ToolResult.Ok).fields

    private fun statusOf(fields: JsonObject, name: String): String? =
        ((fields[name] as? JsonObject)?.get("status") as? JsonPrimitive)?.content

    private fun valueOf(fields: JsonObject, name: String): String? =
        ((fields[name] as? JsonObject)?.get("value"))?.let { (it as? JsonPrimitive)?.content }

    @Test
    fun descriptionListsUnitsSoTheModelNeedNotGuess() {
        val tool = VehicleBasicTool(allowlist, UnavailableVehicleReader())

        assertTrue(tool.description.contains("SPEED[m/s]"))
        assertTrue(tool.description.contains("不要把缺失当成 0"))
    }

    @Test
    fun omittedFieldsArgumentReturnsEverythingReadable() = runTest {
        val tool = VehicleBasicTool(allowlist, FakeVehicleReader(emptyMap()))

        val fields = fieldsOf(tool.execute(buildJsonObject { }))

        assertEquals(allowlist.readable.size, fields.size)
        assertFalse("特权字段不该出现", fields.containsKey("TYRE_PRESSURE"))
    }

    @Test
    fun valuesComeBackWithStatusAndUnit() = runTest {
        val reader = FakeVehicleReader(
            mapOf(
                "SPEED" to VehicleReading.Number(12.5),
                "PERCENT_REMAINING_CHARGE" to VehicleReading.Number(68.0),
            ),
        )

        val fields = fieldsOf(
            VehicleBasicTool(allowlist, reader).execute(
                buildJsonObject {
                    putJson("fields", "SPEED", "PERCENT_REMAINING_CHARGE")
                },
            ),
        )

        assertEquals("ok", statusOf(fields, "SPEED"))
        assertEquals("12.5", valueOf(fields, "SPEED"))
        assertEquals("m/s", ((fields["SPEED"] as JsonObject)["unit"] as JsonPrimitive).content)
        assertEquals("68.0", valueOf(fields, "PERCENT_REMAINING_CHARGE"))
    }

    /** 缺权限/无 VHAL 必须报成结构化状态，而不是一段 "Unavailable" 文本。 */
    @Test
    fun missingReadingsStayStructured() = runTest {
        val tool = VehicleBasicTool(allowlist, UnavailableVehicleReader(DegradeStatus.PermissionDenied, "未授予 CAR_SPEED"))

        val fields = fieldsOf(tool.execute(buildJsonObject { putJson("fields", "SPEED") }))

        assertEquals("permission_denied", statusOf(fields, "SPEED"))
        assertEquals("未授予 CAR_SPEED", ((fields["SPEED"] as JsonObject)["detail"] as JsonPrimitive).content)
    }

    /** 父计划 Step 3 的核心验收：allowlist 少一条，字段就结构化降级，代码零改动。 */
    @Test
    fun droppingAnAllowlistEntryDegradesThatFieldOnly() = runTest {
        val twoFields = """
            {"properties":[
              {"name":"PERCENT_REMAINING_CHARGE","category":"ENERGY_MANAGEMENT","permission":"p",
               "protection":"dangerous","access":"read","unit":"percent","description":"剩余电量"}
            ]}
        """.trimIndent()
        val tool = VehicleBasicTool(
            VehiclePropertyAllowlist.parse(twoFields),
            FakeVehicleReader(mapOf("PERCENT_REMAINING_CHARGE" to VehicleReading.Number(68.0))),
        )

        val fields = fieldsOf(tool.execute(buildJsonObject { putJson("fields", "SPEED", "PERCENT_REMAINING_CHARGE") }))

        assertEquals("allowlist 里删掉的字段只能报 unsupported", "unsupported", statusOf(fields, "SPEED"))
        assertEquals("ok", statusOf(fields, "PERCENT_REMAINING_CHARGE"))
    }

    private fun kotlinx.serialization.json.JsonObjectBuilder.putJson(key: String, vararg values: String) {
        put(
            key,
            JsonArray(values.map { JsonPrimitive(it) }),
        )
    }
}
