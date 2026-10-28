package com.aos.agent.core.tools

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/** 用户对结构化提问的回答。 */
data class AskUserAnswer(
    /** 选中的选项序号（从 0 起）；用户自由输入时为 null。 */
    val selectedIndex: Int?,
    /** 回灌给模型的全文：选中项的 label + description，或用户自由输入。 */
    val text: String,
    /** 用户超时未作答。 */
    val dismissed: Boolean = false,
)

/** 结构化提问通道：弹窗（非驾驶）或语音（驾驶）向用户要一个选择。 */
fun interface AskUserChannel {
    suspend fun ask(question: String, options: List<AskUserOption>, allowFreeText: Boolean): AskUserAnswer
}

data class AskUserOption(
    val label: String,
    val description: String = "",
)

/**
 * 结构化问句工具。
 *
 * 模型拿不准时调它问用户，而不是瞎猜——猜错在车上比多问一句更贵。
 * 它本身就是确认通道，所以 `permission = Auto`，不经过 `ToolSystem` 的 Ask 闸。
 * 驾驶态不弹窗：由通道走语音播报 + 语音/按键回答。
 */
class AskUserTool(
    private val channel: AskUserChannel?,
) : Tool {
    override val name: String = "ask_user"
    override val version: Int = 1
    override val category: String = "interaction"
    override val permission: ToolPermission = ToolPermission.Auto

    override val description: String =
        "拿不准时向用户提问，最多给 4 个选项。用户回答后返回选中的全文；" +
            "超时未作答返回 dismissed，此时自行决定继续或放弃，不要反复追问。"

    override val inputSchema: JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            put("question", buildJsonObject {
                put("type", "string")
                put("description", "问用户的原话，播报与弹窗都用它")
            })
            put("options", buildJsonObject {
                put("type", "array")
                put("description", "1-4 个选项，每个 {label, description}")
                put("minItems", 1)
                put("maxItems", 4)
                put("items", buildJsonObject {
                    put("type", "object")
                    put("properties", buildJsonObject {
                        put("label", buildJsonObject { put("type", "string") })
                        put("description", buildJsonObject { put("type", "string") })
                    })
                    putJsonArray("required") { add(JsonPrimitive("label")) }
                })
            })
            put("allow_free_text", buildJsonObject {
                put("type", "boolean")
                put("description", "是否允许用户自由输入而非只选选项")
            })
        })
        putJsonArray("required") {
            add(JsonPrimitive("question"))
            add(JsonPrimitive("options"))
        }
    }

    override suspend fun execute(args: JsonObject): ToolResult {
        val channel = channel ?: return ToolResult.Error("当前没有接入提问通道")
        val question = (args["question"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
        if (question.isEmpty()) return ToolResult.Error("缺少 question 参数")
        val options = parseOptions(args["options"])
        if (options.isEmpty()) return ToolResult.Error("options 至少要给 1 个")
        val allowFreeText = (args["allow_free_text"] as? JsonPrimitive)?.contentOrNull?.toBooleanStrictOrNull()
            ?: (args["allow_free_text"] as? JsonPrimitive)?.content == "true"
        val answer = channel.ask(question, options, allowFreeText)
        return ToolResult.Ok(
            buildJsonObject {
                put("status", if (answer.dismissed) "dismissed" else "answered")
                if (answer.selectedIndex != null) put("selected", answer.selectedIndex)
                put("text", answer.text)
            },
        )
    }

    private fun parseOptions(raw: kotlinx.serialization.json.JsonElement?): List<AskUserOption> {
        val array = raw as? JsonArray ?: return emptyList()
        return array.take(MAX_OPTIONS).mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val label = (obj["label"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
            if (label.isEmpty()) return@mapNotNull null
            AskUserOption(label, (obj["description"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty())
        }
    }

    private companion object {
        const val MAX_OPTIONS = 4
    }
}
