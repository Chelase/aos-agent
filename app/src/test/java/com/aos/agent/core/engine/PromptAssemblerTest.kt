package com.aos.agent.core.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptAssemblerTest {

    @Test
    fun sectionsAreOrderedByOrderThenName() {
        val assembler = PromptAssembler(
            listOf(
                PromptSection("b", 20) { "B" },
                PromptSection("a", 10) { "A" },
                PromptSection("c", 10) { "C" },
            ),
        )

        assertEquals("A\n\nC\n\nB", assembler.assemble(PromptContext()))
    }

    @Test(expected = IllegalArgumentException::class)
    fun duplicateNamesAreRejected() {
        PromptAssembler(
            listOf(
                PromptSection("same", 1) { "x" },
                PromptSection("same", 2) { "y" },
            ),
        )
    }

    @Test
    fun emptySectionsAreOmitted() {
        val assembler = PromptAssembler(
            listOf(
                PromptSection("keep", 1) { "ok" },
                PromptSection("skip", 2) { "  " },
            ),
        )

        assertEquals("ok", assembler.assemble(PromptContext()))
    }

    @Test
    fun sameAssemblerYieldsDifferentTextForDifferentContext() {
        val assembler = PromptAssembler(
            listOf(PromptSection("env", 1) { ctx -> "pkg=${ctx["package_name"]}" }),
        )

        val a = assembler.assemble(PromptContext(mapOf("package_name" to "com.a")))
        val b = assembler.assemble(PromptContext(mapOf("package_name" to "com.b")))

        assertEquals("pkg=com.a", a)
        assertEquals("pkg=com.b", b)
        assertTrue(a != b)
    }

    @Test
    fun defaultCapabilitiesListsEveryRegisteredToolName() {        val toolsBlock = """
            |- shell_exec：执行只读诊断
            |- system_info：读取系统信息
            |- vehicle_basic：读取车辆基础属性
        """.trimMargin()
        val prompt = PromptAssembler(PromptSections.defaults()).assemble(
            PromptContext(
                mapOf(
                    "package_name" to "com.aos.agent",
                    "version_name" to "1.0",
                    "version_code" to "1",
                    "android_api" to "36",
                    "automotive" to "是",
                    "has_car_api" to "是",
                    "now" to "2026-10-28 12:00:00 +0800",
                    "screen" to "1920x1080",
                    "tools" to toolsBlock,
                    "skills" to "电量查询：读动力电池",
                    "cli_path" to "/data/user/0/com.aos.agent/files/bin/aos-llm",
                    "cli_present" to "是",
                    "cli_commands" to "status, list, set, add, use, rm, sync",
                ),
            ),
        )

        assertTrue(prompt.contains("shell_exec"))
        assertTrue(prompt.contains("system_info"))
        assertTrue(prompt.contains("vehicle_basic"))
        assertTrue(prompt.contains("aos-llm"))
        assertTrue(prompt.contains("优先调用 shell_exec"))
        assertFalse(prompt.contains("当前能力："))
    }

    /** 硬约束必须出现在提示里：模型要提前知道边界，而不是撞墙后才学。 */
    @Test
    fun defaultRulesStateTheVehicleControlBan() {
        val prompt = PromptAssembler(PromptSections.defaults()).assemble(PromptContext())

        assertTrue(prompt.contains("禁止操控车辆"))
    }

    /** C6：稳定节首帧渲染后逐字复用，动态节每轮重算。 */
    @Test
    fun stableSectionIsRenderedOnceAndReused() {
        var renders = 0
        val assembler = PromptAssembler(
            listOf(PromptSection("stable", 1, stable = true) { renders++; "constant" }),
        )

        assembler.assemble(PromptContext(mapOf("x" to "1")))
        assembler.assemble(PromptContext(mapOf("x" to "2")))

        assertEquals("稳定节只应渲染一次", 1, renders)
    }

    @Test
    fun dynamicSectionIsRenderedEveryTurn() {
        var renders = 0
        val assembler = PromptAssembler(
            listOf(PromptSection("dynamic", 1) { renders++; "value" }),
        )

        assembler.assemble(PromptContext())
        assembler.assemble(PromptContext())

        assertEquals("动态节每轮都应重算", 2, renders)
    }

    /** 稳定节快照不能把过期的环境信息固化下来——它只能用于纯常量文本。 */
    @Test
    fun snapshotKeepsFirstRenderedTextVerbatim() {
        val assembler = PromptAssembler(
            listOf(PromptSection("stable", 1, stable = true) { ctx -> "pkg=${ctx["package_name"]}" }),
        )

        val first = assembler.assemble(PromptContext(mapOf("package_name" to "com.a")))
        val second = assembler.assemble(PromptContext(mapOf("package_name" to "com.b")))

        assertEquals("pkg=com.a", first)
        assertEquals("稳定节应逐字复用", first, second)
    }

    @Test
    fun resetSnapshotsAllowsStableSectionToChange() {
        var value = "v1"
        val assembler = PromptAssembler(
            listOf(PromptSection("stable", 1, stable = true) { value }),
        )

        assertEquals("v1", assembler.assemble(PromptContext()))
        value = "v2"
        assertEquals("v1", assembler.assemble(PromptContext()))

        assembler.resetSnapshots()
        assertEquals("v2", assembler.assemble(PromptContext()))
    }

    @Test
    fun defaultPersonaAndRulesAreStableEnvironmentIsNot() {
        val defaults = PromptSections.defaults()

        assertTrue(defaults.single { it.name == "persona" }.stable)
        assertTrue(defaults.single { it.name == "rules" }.stable)
        assertFalse(defaults.single { it.name == "environment" }.stable)
        assertFalse(defaults.single { it.name == "capabilities" }.stable)
        assertFalse(defaults.single { it.name == "cli" }.stable)
    }
}
