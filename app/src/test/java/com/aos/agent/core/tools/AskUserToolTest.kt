package com.aos.agent.core.tools

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AskUserToolTest {

    private fun args(question: String = "去哪充电？", options: String = bindingOptions()) = buildJsonObject {
        put("question", question)
        put("options", TestJson.parse(options))
    }

    private fun bindingOptions(): String =
        """[{"label":"家附近","description":"3 公里内"},{"label":"高速沿线"}]"""

    @Test
    fun selectedOptionReturnsFullText() {
        val tool = AskUserTool { _, options, _ ->
            AskUserAnswer(selectedIndex = 0, text = "${options[0].label}：${options[0].description}")
        }

        val result = runTool(tool, args())

        assertTrue(result is ToolResult.Ok)
        val fields = (result as ToolResult.Ok).fields
        assertEquals("answered", fields.string("status"))
        assertEquals(0, fields.int("selected"))
        assertTrue(fields.string("text")!!.contains("家附近"))
    }

    @Test
    fun dismissedReturnsStatusNotError() {
        val tool = AskUserTool { _, _, _ -> AskUserAnswer(selectedIndex = null, text = "", dismissed = true) }

        val result = runTool(tool, args())

        assertTrue(result is ToolResult.Ok)
        assertEquals("dismissed", ((result as ToolResult.Ok).fields.string("status")))
    }

    @Test
    fun missingQuestionIsError() {
        val tool = AskUserTool { _, _, _ -> error("不应被调用") }

        assertTrue(runTool(tool, buildJsonObject { put("options", TestJson.parse(bindingOptions())) }) is ToolResult.Error)
    }

    @Test
    fun emptyOptionsIsError() {
        val tool = AskUserTool { _, _, _ -> error("不应被调用") }

        assertTrue(runTool(tool, buildJsonObject {
            put("question", "问什么")
            put("options", TestJson.parse("[]"))
        }) is ToolResult.Error)
    }

    @Test
    fun moreThanFourOptionsAreTrimmed() {
        var seen = 0
        val tool = AskUserTool { _, options, _ ->
            seen = options.size
            AskUserAnswer(selectedIndex = 0, text = options.first().label)
        }
        val many = (1..6).joinToString(prefix = "[", postfix = "]") { """{"label":"选项$it"}""" }

        runTool(tool, args(options = many))

        assertEquals("最多 4 个选项", 4, seen)
    }

    @Test
    fun noChannelIsErrorNotCrash() {
        val result = runTool(AskUserTool(null), args())

        assertTrue(result is ToolResult.Error)
    }
}

private object TestJson {
    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
    fun parse(raw: String) = json.parseToJsonElement(raw)
}

private fun runTool(tool: AskUserTool, args: JsonObject): ToolResult =
    kotlinx.coroutines.runBlocking { tool.execute(args) }

private fun JsonObject.string(key: String): String? =
    (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.let {
        if (it.isString) it.content else null
    }

private fun JsonObject.int(key: String): Int? =
    (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toIntOrNull()
