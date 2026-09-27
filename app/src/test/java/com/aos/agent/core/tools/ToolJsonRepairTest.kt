package com.aos.agent.core.tools

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolJsonRepairTest {

    private fun keys(obj: JsonObject?) = obj?.keys?.toSortedSet()

    @Test
    fun plainObjectParses() {
        assertEquals(setOf("fields"), keys(ToolJsonRepair.parseArguments("""{"fields":["SPEED"]}""")))
    }

    @Test
    fun markdownFenceIsStripped() {
        val raw = "```json\n{\"command\":\"getprop x\"}\n```"

        assertNotNull(ToolJsonRepair.parseArguments(raw))
    }

    @Test
    fun trailingCommaIsRemoved() {
        assertNotNull(ToolJsonRepair.parseArguments("""{"command":"getprop",}"""))
    }

    @Test
    fun missingClosingBraceIsBalanced() {
        val repaired = ToolJsonRepair.parseArguments("""{"fields":["SPEED","EV_BATTERY_LEVEL"""")

        assertNotNull(repaired)
        assertEquals("SPEED", (repaired?.get("fields") as? kotlinx.serialization.json.JsonArray)?.let { it[0] }
            .let { (it as JsonPrimitive).content })
    }

    /** 模型常在 JSON 后面追加解释，截到最后一个右花括号即可。 */
    @Test
    fun trailingProseAfterObjectIsCut() {
        val repaired = ToolJsonRepair.parseArguments("""{"command":"id"} 这是查询身份的命令""")

        assertEquals("id", (repaired?.get("command") as JsonPrimitive).content)
    }

    @Test
    fun emptyAndGarbageReturnNull() {
        assertNull(ToolJsonRepair.parseArguments(""))
        assertNull(ToolJsonRepair.parseArguments("   "))
        assertNull(ToolJsonRepair.parseArguments("我不知道该调哪个工具"))
    }

    @Test
    fun emptyObjectIsAccepted() {
        assertEquals(emptySet<String>(), keys(ToolJsonRepair.parseArguments("{}")))
    }
}
