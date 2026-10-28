package com.aos.agent.core.tools

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 硬约束：**允许操作车机系统内部，禁止操控车辆硬件**。
 *
 * 边界两侧都要测：车机内部（查隐藏模块、启用隐藏功能）必须放行，
 * 车辆硬件动作必须拦下。这些判定是最后一道闸——模型会换参数重试，
 * 所以必须靠代码拦住，不能只写在系统提示里。
 */
class VehicleHardwareTest {

    // ---- 必须拦下：车辆硬件动作 ----

    @Test
    fun hardwareEventInjectionIsBlocked() {
        listOf(
            "input keyevent 26",
            "sendevent /dev/input/event0 1 1 1",
            "input touchscreen swipe 100 100 200 200",
        ).forEach { command ->
            assertNotNull("应拦下：$command", VehicleHardware.commandReason(command.split(" ")))
        }
    }

    /** `cmd car_service set-power off` 是动硬件；`cmd car_service list` 只是读。 */
    @Test
    fun vehicleServiceWriteActionIsBlockedButReadIsAllowed() {
        listOf(
            "cmd car_service set-power off",
            "cmd vehicle set-hvac on",
            "cmd vhal set-window-position 50",
        ).forEach { command ->
            assertNotNull("应拦下：$command", VehicleHardware.commandReason(command.split(" ")))
        }
        assertNull(VehicleHardware.commandReason("cmd car_service list".split(" ")))
    }

    /** `service call` 是通用 binder 写原语，能绕过权限层直调服务写接口。 */
    @Test
    fun serviceCallIsBlockedButServiceListIsAllowed() {
        assertNotNull(VehicleHardware.commandReason("service call car_service 1 i32 1".split(" ")))
        assertNull(VehicleHardware.commandReason("service list".split(" ")))
    }

    /** 包装器是最危险的：一条 `sh -c` 就能藏住任意硬件动作。 */
    @Test
    fun commandWrappersAreBlockedSoTheyCannotHideHardwareActions() {
        listOf(
            "sh -c cmd\\ car_service\\ set-power\\ off",
            "bash -c ls",
            "python -c import\\ os",
            "toybox sh",
            "timeout 5 getprop ro.build.version.release",
            "env FOO=1 getprop ro.build.version.release",
            "busybox cat /proc/version",
        ).forEach { command ->
            assertNotNull("应拦下：$command", VehicleHardware.commandReason(command.split(" ")))
        }
    }

    @Test
    fun privilegeEscalationAndRawDeviceWriteAreBlocked() {
        listOf(
            "su -c ls",
            "sudo ls",
            "dd if=/dev/zero of=/dev/block/sda bs=1M",
            "mount /dev/block/sda /mnt",
            "insmod /system/lib/hw.ko",
            "reboot",
        ).forEach { command ->
            assertNotNull("应拦下：$command", VehicleHardware.commandReason(command.split(" ")))
        }
    }

    /** `find` 只读遍历要放行（找隐藏模块要用），带 -exec/-delete 才拦。 */
    @Test
    fun findIsAllowedUnlessItCanExecuteOrDelete() {
        assertNull(VehicleHardware.commandReason("find /system -name *.so".split(" ")))
        assertNull(VehicleHardware.commandReason("find /system -name *.so -type f".split(" ")))
        assertNotNull(VehicleHardware.commandReason("find /system -exec ls {} ;".split(" ")))
        assertNotNull(VehicleHardware.commandReason("find /system -delete".split(" ")))
    }

    @Test
    fun pathPrefixDoesNotBypassTheBlock() {
        assertNotNull(
            VehicleHardware.commandReason(listOf("/system/bin/input", "keyevent", "26")),
        )
    }

    // ---- 必须放行：车机系统内部 ----

    /**
     * 核心诉求：能调出车辆隐藏模块、功能、数据。
     * 例如被隐藏的行车记录仪——要能查到、能打开。
     */
    @Test
    fun hiddenFeaturesInsideTheHeadUnitRemainReachable() {
        listOf(
            // 查隐藏模块/包/功能
            "pm list packages -u",
            "pm list packages -d",
            "cmd package list packages",
            "dumpsys package com.oem.dashcam",
            "dumpsys car_service",
            "dumpsys activity service com.oem.dashcam/.RecorderService",
            "service list",
            "cmd overlay list",
            // 调出隐藏功能
            "cmd package enable com.oem.dashcam",
            "pm enable com.oem.dashcam",
            "settings put global dashcam_enabled 1",
            "setprop persist.oem.dashcam 1",
            "am start -n com.oem.dashcam/.MainActivity",
            // 只读诊断
            "getprop ro.build.version.release",
            "uptime",
            "date",
            "settings get global device_name",
            "wm size",
            "ls /system/priv-app",
        ).forEach { command ->
            assertNull("不应拦下：$command", VehicleHardware.commandReason(command.split(" ")))
        }
    }

    // ---- 远端工具 ----

    @Test
    fun remoteHardwareWriteToolIsBlocked() {
        assertNotNull(
            VehicleHardware.toolReason("car", "set_climate_temperature", "设置空调温度"),
        )
        assertNotNull(
            VehicleHardware.toolReason("remote", "open_door", "打开车门"),
        )
    }

    @Test
    fun remoteReadToolIsAllowed() {
        assertNull(
            VehicleHardware.toolReason("car", "get_battery_level", "读取电量"),
        )
        assertNull(
            VehicleHardware.toolReason("remote", "list_files", "列出文件"),
        )
    }

    /** 工具名可能是 action_1 这类无意义名，硬件词只在描述里——中文也要拦得住。 */
    @Test
    fun hardwareWordInDescriptionIsEnoughToBlock() {
        assertNotNull(
            VehicleHardware.toolReason("remote", "action_1", "打开车门"),
        )
        assertNotNull(
            VehicleHardware.toolReason("remote", "action_2", "设置空调温度"),
        )
    }

    /** 车辆域 server：写动作工具一律拦——远端不可知，`set_temperature` 与 `enable_dashcam` 只差一个词。 */
    @Test
    fun vehicleServerRejectsWriteToolsButAllowsReadTools() {
        assertNotNull(VehicleHardware.toolReason("car", "toggle_windows", "开关车窗"))
        assertNotNull(VehicleHardware.toolReason("car", "enable_dashcam", "启用行车记录仪"))
        assertNull(VehicleHardware.toolReason("car", "query_speed", "读取车速"))
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
            override val inputSchema = buildJsonObject { put("type", "object") }
            override fun hardBlockReason(args: kotlinx.serialization.json.JsonObject): String? =
                VehicleHardware.toolReason("car", "set_temp", "设置空调温度")

            override suspend fun execute(args: kotlinx.serialization.json.JsonObject): ToolResult =
                ToolResult.Ok(buildJsonObject { put("applied", true) })
        }
        val system = ToolSystem(confirmer = ToolConfirmer { _, _ -> true })
        system.register(tool)

        val result = system.execute("mcp_car_set_temp", buildJsonObject { })

        assertTrue(result is ToolResult.Rejected)
        assertEquals(
            RejectionReason.VehicleHardwareForbidden,
            (result as ToolResult.Rejected).reason,
        )
    }
}
