package com.aos.agent.core.skills

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SkillRegistryTest {

    private val vehicleSkill = """
        {
          "id": "vehicle_status",
          "name": "车辆状态查询",
          "description": "查询只读车况",
          "triggers": ["车辆状态", "电量", "续航"],
          "tools": ["vehicle_basic", "control_window"],
          "prompt": "给出数值与单位",
          "permission": "query"
        }
    """.trimIndent()

    private val systemSkill = """
        {
          "id": "system_diagnostics",
          "name": "系统诊断",
          "description": "查看系统环境",
          "triggers": ["系统信息", "网络"],
          "tools": ["system_info"],
          "prompt": "一到两句概括",
          "permission": "query",
          "tags": ["system"],
          "input_modes": ["voice"]
        }
    """.trimIndent()

    private fun registry(vararg sources: Pair<String, String>) = SkillRegistry.parseAll(sources.toList())

    private val two = listOf("vehicle_status" to vehicleSkill, "system_diagnostics" to systemSkill)

    @Test
    fun parsesBothSkillsAndCapabilityFields() {
        val loaded = registry(*two.toTypedArray())

        assertEquals(listOf("system_diagnostics", "vehicle_status"), loaded.skills.map { it.id })
        assertEquals(0, loaded.skipped.size)
        val vehicle = loaded.byId("vehicle_status")!!
        assertEquals(SkillPermission.Query, vehicle.permission)
        assertEquals("给出数值与单位", vehicle.prompt)
    }

    @Test
    fun matchesOnDeclaredTriggers() {
        val loaded = registry(*two.toTypedArray())

        assertEquals("vehicle_status", loaded.match("现在电量还剩多少")?.id)
        assertEquals("system_diagnostics", loaded.match("看下系统信息")?.id)
        assertNull("未命中要安静回退", loaded.match("今天心情不好"))
        assertNull(loaded.match("   "))
    }

    /** 命中两个时按触发词数量取胜，同分按 id 字典序，保证可测稳定。 */
    @Test
    fun highestScoreWinsAndTiesBreakByStableOrder() {
        val loaded = registry(*two.toTypedArray())

        assertEquals("vehicle_status", loaded.match("车辆状态 电量 续航 怎么样")?.id)

        val overlapping = registry(
            "b" to """{"id":"b_skill","name":"b","description":"d","triggers":["共同"],"tools":[],"permission":"query"}""",
            "a" to """{"id":"a_skill","name":"a","description":"d","triggers":["共同"],"tools":[],"permission":"query"}""",
        )
        assertEquals("a_skill", overlapping.match("共同 出现")?.id)
    }

    /** skill 里写了控制类工具名：过滤掉，绝不带进引擎。 */
    @Test
    fun controlToolsDeclaredByMistakeAreFilteredOut() {
        val skill = registry(*two.toTypedArray()).byId("vehicle_status")!!

        val effective = skill.effectiveToolNames(available = setOf("vehicle_basic", "system_info"))

        assertEquals(setOf("vehicle_basic"), effective)
        assertTrue("control_window 不该出现", "control_window" !in effective)
    }

    /** 舒适/运动/高风险档位在解析阶段就不注册，效果等同未命中。 */
    @Test
    fun nonQueryPermissionsAreNotRegistered() {
        val loaded = registry(
            "comfort" to """{"id":"ac","name":"空调","description":"d","triggers":["空调"],"tools":["vehicle_basic"],"permission":"comfort"}""",
        )

        assertNull(loaded.match("把空调打开"))
        assertEquals(1, loaded.skipped.size)
        assertTrue(loaded.skipped.single().reason.contains("只允许 query"))
    }

    @Test
    fun brokenDefinitionsAreSkippedWithoutKillingTheRegistry() {
        val loaded = registry(
            "bad_json" to "{not json",
            "missing_name" to """{"id":"x","description":"d","triggers":[],"tools":[],"permission":"query"}""",
            "bad_permission" to """{"id":"y","name":"y","description":"d","triggers":[],"tools":[],"permission":"teleport"}""",
            "good" to systemSkill,
        )

        assertEquals(listOf("system_diagnostics"), loaded.skills.map { it.id })
        assertEquals(listOf("bad_json", "bad_permission", "missing_name"), loaded.skipped.map { it.source }.sorted())
    }

    @Test
    fun capabilitySummaryListsWhatTheCarCanDo() {
        val summary = registry(*two.toTypedArray()).capabilitySummary()

        assertTrue(summary.contains("车辆状态查询"))
        assertTrue(summary.contains("系统诊断"))
    }

    @Test
    fun shippedSkillsParseAndMatchRealQueries() {
        val shipped = java.io.File("src/main/assets/skills")
            .listFiles()
            .orEmpty()
            .flatMap { dir ->
                val file = java.io.File(dir, "skill.json")
                if (file.exists()) listOf(file.path to file.readText()) else emptyList()
            }
        val loaded = SkillRegistry.parseAll(shipped)

        assertEquals(2, loaded.skills.size)
        assertNotNull("内置 skill 应能被真实问法命中", loaded.match("车辆状态怎么样"))
        assertNotNull(loaded.match("帮我看下系统信息"))
        assertEquals("query", loaded.skills.map { it.permission.wire }.distinct().single())
    }
}
