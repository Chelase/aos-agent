package com.aos.agent.core.tools

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 硬约束：允许操作底层，禁止操控车辆。
 *
 * 这些判定是最后一道闸——模型会换参数重试，所以必须靠代码拦住，
 * 不能只写在系统提示里。
 */
class VehicleActuationTest {

    @Test
    fun vehicleControlChannelsAreBlocked() {
        listOf(
            "service call car_service 1 i32 1",
            "cmd car_service set-power off",
            "input keyevent 26",
            "sendevent /dev/input/event0 1 1 1",
            "am start -n com.oem.carcontrol/.MainActivity",
            "am broadcast -a com.oem.carcontrol.OPEN",
        ).forEach { command ->
            assertNotNull(
                "应拦下：$command",
                VehicleActuation.commandReason(command.split(" ")),
            )
        }
    }

    /** 包装器是最危险的：一条 `sh -c` 就能藏住任意禁命令。 */
    @Test
    fun commandWrappersAreBlockedSoTheyCannotHideBannedCommands() {
        listOf(
            "sh -c cmd\\ car_service\\ set-power\\ off",
            "bash -c ls",
            "python -c import\\ os",
            "toybox sh",
            "timeout 5 getprop ro.build.version.release",
            "env FOO=1 getprop ro.build.version.release",
            "busybox cat /proc/version",
        ).forEach { command ->
            assertNotNull(
                "应拦下：$command",
                VehicleActuation.commandReason(command.split(" ")),
            )
        }
    }

    /** 只读诊断必须照常可用——硬闸不能把底层能力一起掐死。 */
    @Test
    fun readOnlyDiagnosticsStillAllowed() {
        listOf(
            "getprop ro.build.version.release",
            "dumpsys car_service",
            "uptime",
            "date",
            "settings get global device_name",
            "wm size",
        ).forEach { command ->
            assertNull(
                "不应拦下：$command",
                VehicleActuation.commandReason(command.split(" ")),
            )
        }
    }

    @Test
    fun pathPrefixDoesNotBypassTheBlock() {
        assertNotNull(
            VehicleActuation.commandReason(listOf("/system/bin/service", "call", "car_service")),
        )
    }

    @Test
    fun remoteVehicleWriteToolIsBlocked() {
        assertNotNull(
            VehicleActuation.toolReason("car", "set_climate_temperature", "设置空调温度"),
        )
        assertNotNull(
            VehicleActuation.toolReason("remote", "open_door", "打开车门"),
        )
    }

    @Test
    fun remoteVehicleReadToolIsAllowed() {
        assertNull(
            VehicleActuation.toolReason("car", "get_battery_level", "读取电量"),
        )
        assertNull(
            VehicleActuation.toolReason("remote", "list_files", "列出文件"),
        )
    }

    /** 车辆域的 server：除读工具外一律拦，避免漏掉没带车辆名词的写工具。 */
    @Test
    fun vehicleServerRejectsNonReadTools() {
        assertNotNull(VehicleActuation.toolReason("car", "toggle_windows", "开关车窗"))
        assertNull(VehicleActuation.toolReason("car", "query_speed", "读取车速"))
    }

    /** MCP 配置成 auto 时也拦得住——确认流程不参与。 */
    @Test
    fun hardBlockBeatsAutoPermission() = kotlinx.coroutines.test.runTest {
        val tool = object : Tool {
            override val name = "mcp_car_set_temp"
            override val version = 1
            override val category = "mcp:car"
            override val description = "设置空调温度"
            override val permission = ToolPermission.Auto
            override val inputSchema = kotlinx.serialization.json.buildJsonObject {
                put("type", "object")
            }
            override fun hardBlockReason(args: kotlinx.serialization.json.JsonObject): String? =
                VehicleActuation.toolReason("car", "set_temp", "设置空调温度")

            override suspend fun execute(args: kotlinx.serialization.json.JsonObject): ToolResult =
                ToolResult.Ok(kotlinx.serialization.json.buildJsonObject { put("applied", true) })
        }
        val system = ToolSystem(confirmer = ToolConfirmer { _, _ -> true })
        system.register(tool)

        val result = system.execute("mcp_car_set_temp", kotlinx.serialization.json.buildJsonObject { })

        assertTrue(result is ToolResult.Rejected)
        assertEquals(RejectionReason.VehicleControlForbidden, (result as ToolResult.Rejected).reason)
    }
}
