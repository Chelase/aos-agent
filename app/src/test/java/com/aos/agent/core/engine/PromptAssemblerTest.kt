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
    fun defaultCapabilitiesListsEveryRegisteredToolName() {
        val toolsBlock = """
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
}
