package com.aos.agent.core.llm

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/**
 * 模型服务配置档案（地址簿条目）。
 *
 * 档案只是"存着备用的三元组"；「当前生效」永远只有 [LlmConfigStore] 一份真源，
 * `use` 的语义是把档案复制进当前配置，不是改指针。
 */
data class LlmProfile(
    val name: String,
    val baseUrl: String,
    val model: String,
    val apiKey: String = "",
)

/** CLI 广播能下发的全部写操作。 */
sealed interface LlmCommand {
    data class SetActive(
        val baseUrl: String,
        val model: String,
        val apiKey: String,
        val saveAs: String? = null,
        val keepKey: Boolean = false,
    ) : LlmCommand

    data class AddProfile(
        val name: String,
        val baseUrl: String,
        val model: String,
        val apiKey: String = "",
    ) : LlmCommand
    data class UseProfile(val name: String) : LlmCommand
    data class RemoveProfile(val name: String) : LlmCommand
}

/** CLI 视角的整体状态：地址簿 + 当前生效配置 + 当前配置来自哪个档案。 */
data class LlmCliState(
    val profiles: List<LlmProfile>,
    val active: LlmConfig?,
    val activeProfile: String? = null,
)

sealed interface LlmCliOutcome {
    val message: String

    data class Ok(val newState: LlmCliState, override val message: String) : LlmCliOutcome
    data class Err(override val message: String) : LlmCliOutcome
}

object LlmProfiles {

    /** 档案名同时是 state.sh 里的变量名片段，必须限制字符集。 */
    const val NAME_PATTERN = "[a-z0-9][a-z0-9_-]{0,31}"
    val NAME_REGEX = Regex(NAME_PATTERN)

    fun validName(name: String): Boolean = NAME_REGEX.matches(name)

    fun encode(profiles: List<LlmProfile>): String = buildJsonArray {
        profiles.forEach { profile ->
            add(
                buildJsonObject {
                    put("name", profile.name)
                    put("baseUrl", profile.baseUrl)
                    put("model", profile.model)
                    put("apiKey", profile.apiKey)
                },
            )
        }
    }.toString()

    /** 容错解码：坏 JSON 或坏条目按空处理，绝不抛出到调用方。 */
    fun decode(raw: String?): List<LlmProfile> {
        if (raw.isNullOrBlank()) return emptyList()
        val array = runCatching { Json.parseToJsonElement(raw).jsonArray }.getOrNull() ?: return emptyList()
        return array.mapNotNull { element ->
            val obj = runCatching { element.jsonObject }.getOrNull() ?: return@mapNotNull null
            val name = obj.string("name")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            LlmProfile(
                name = name,
                baseUrl = obj.string("baseUrl").orEmpty(),
                model = obj.string("model").orEmpty(),
                apiKey = obj.string("apiKey").orEmpty(),
            )
        }
    }

    private fun kotlinx.serialization.json.JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.content

    /** 档案名合法、URL 与模型名非空才收；其余给原因。 */
    fun apply(state: LlmCliState, command: LlmCommand): LlmCliOutcome = when (command) {
        is LlmCommand.SetActive -> applySetActive(state, command)
        is LlmCommand.AddProfile -> applyAdd(state, command)
        is LlmCommand.UseProfile -> applyUse(state, command)
        is LlmCommand.RemoveProfile -> applyRemove(state, command)
    }

    private fun applySetActive(state: LlmCliState, command: LlmCommand.SetActive): LlmCliOutcome {
        // --keep 语义：未显式给出的字段（URL/模型/Key）沿用当前生效配置
        val url = command.baseUrl.trim().ifEmpty {
            if (command.keepKey) state.active?.baseUrl.orEmpty() else ""
        }
        val model = command.model.trim().ifEmpty {
            if (command.keepKey) state.active?.model.orEmpty() else ""
        }
        if (url.isEmpty() || model.isEmpty()) {
            return LlmCliOutcome.Err("URL 与模型名都不能为空（当前无生效配置可沿用时必须显式给出）")
        }
        val key = when {
            command.keepKey -> state.active?.apiKey.orEmpty()
            else -> command.apiKey.trim()
        }
        val active = LlmConfig(baseUrl = url, model = model, apiKey = key)
        var profiles = state.profiles
        var savedAs: String? = null
        command.saveAs?.let { name ->
            if (!validName(name)) {
                return LlmCliOutcome.Err("档案名不合法：$name（小写字母/数字开头，仅小写字母数字-_，≤32 字符）")
            }
            profiles = upsert(profiles, LlmProfile(name = name, baseUrl = url, model = model, apiKey = key))
            savedAs = name
        }
        val tail = if (savedAs != null) "，并存为档案 $savedAs" else ""
        return LlmCliOutcome.Ok(
            newState = state.copy(profiles = profiles, active = active, activeProfile = savedAs),
            message = "已写入当前配置：$model @ $url$tail",
        )
    }

    private fun applyAdd(state: LlmCliState, command: LlmCommand.AddProfile): LlmCliOutcome {
        if (!validName(command.name)) {
            return LlmCliOutcome.Err("档案名不合法：${command.name}（小写字母/数字开头，仅小写字母数字-_，≤32 字符）")
        }
        val url = command.baseUrl.trim()
        val model = command.model.trim()
        if (url.isEmpty() || model.isEmpty()) {
            return LlmCliOutcome.Err("URL 与模型名都不能为空")
        }
        val profile = LlmProfile(name = command.name, baseUrl = url, model = model, apiKey = command.apiKey.trim())
        val existed = state.profiles.any { it.name == profile.name }
        val profiles = upsert(state.profiles, profile)
        val verb = if (existed) "已更新" else "已保存"
        return LlmCliOutcome.Ok(
            newState = state.copy(profiles = profiles),
            message = "$verb 档案 ${profile.name}：${profile.model} @ ${profile.baseUrl}（当前生效配置未变）",
        )
    }

    private fun applyUse(state: LlmCliState, command: LlmCommand.UseProfile): LlmCliOutcome {
        val profile = state.profiles.firstOrNull { it.name == command.name }
            ?: return LlmCliOutcome.Err("没有档案 ${command.name}；用 aos-llm list 查看")
        val active = LlmConfig(baseUrl = profile.baseUrl, model = profile.model, apiKey = profile.apiKey)
        return LlmCliOutcome.Ok(
            newState = state.copy(active = active, activeProfile = profile.name),
            message = "已切换到 ${profile.name}：${profile.model} @ ${profile.baseUrl}",
        )
    }

    private fun applyRemove(state: LlmCliState, command: LlmCommand.RemoveProfile): LlmCliOutcome {
        if (state.profiles.none { it.name == command.name }) {
            return LlmCliOutcome.Err("没有档案 ${command.name}；用 aos-llm list 查看")
        }
        val profiles = state.profiles.filterNot { it.name == command.name }
        val note = if (state.activeProfile == command.name) {
            "（该档案是当前生效配置的来源，删除地址簿不影响已生效配置）"
        } else {
            "（不影响当前生效配置）"
        }
        val activeProfile = state.activeProfile?.takeIf { it != command.name }
        return LlmCliOutcome.Ok(
            newState = state.copy(profiles = profiles, activeProfile = activeProfile),
            message = "已删除档案 ${command.name}$note",
        )
    }

    /** 同名替换保持原位置，新增追加到末尾。 */
    private fun upsert(profiles: List<LlmProfile>, profile: LlmProfile): List<LlmProfile> {
        val index = profiles.indexOfFirst { it.name == profile.name }
        return if (index >= 0) {
            profiles.toMutableList().also { it[index] = profile }
        } else {
            profiles + profile
        }
    }
}

/**
 * 广播 action + extras → [LlmCommand] 的纯解析，接收器只做搬运。
 *
 * action 形如 `<applicationId>.llm.<命令>`。只按 `.llm.` 之后的命令段分发，
 * 不比对完整包名：调试包带 `.debug` 后缀时，aos-llm 脚本用 `@PKG@` 拼出来的
 * action 与发布包不同，若按完整字符串匹配会静默失效。
 */
object LlmCliParser {
    private const val COMMAND_SET_ACTIVE = "SET_ACTIVE"
    private const val COMMAND_ADD_PROFILE = "ADD_PROFILE"
    private const val COMMAND_USE_PROFILE = "USE_PROFILE"
    private const val COMMAND_REMOVE_PROFILE = "REMOVE_PROFILE"
    private const val COMMAND_REFRESH = "REFRESH"

    /** 包名与命令之间的分隔，命令段由它定位。 */
    private const val NAMESPACE_MARKER = ".llm."

    const val ACTION_PREFIX = "com.aos.agent.llm."
    const val ACTION_SET_ACTIVE = ACTION_PREFIX + COMMAND_SET_ACTIVE
    const val ACTION_ADD_PROFILE = ACTION_PREFIX + COMMAND_ADD_PROFILE
    const val ACTION_USE_PROFILE = ACTION_PREFIX + COMMAND_USE_PROFILE
    const val ACTION_REMOVE_PROFILE = ACTION_PREFIX + COMMAND_REMOVE_PROFILE
    const val ACTION_REFRESH = ACTION_PREFIX + COMMAND_REFRESH

    const val EXTRA_NAME = "name"
    const val EXTRA_URL = "url"
    const val EXTRA_MODEL = "model"
    const val EXTRA_KEY = "key"

    private fun commandOf(action: String?): String? {
        val index = action?.lastIndexOf(NAMESPACE_MARKER) ?: return null
        if (index < 0) return null
        return action.substring(index + NAMESPACE_MARKER.length)
    }

    fun isRefresh(action: String?): Boolean = commandOf(action) == COMMAND_REFRESH

    fun parse(action: String?, name: String?, url: String?, model: String?, key: String?, keepKey: Boolean): LlmCommand? =
        when (commandOf(action)) {
            COMMAND_SET_ACTIVE -> LlmCommand.SetActive(
                baseUrl = url.orEmpty(),
                model = model.orEmpty(),
                apiKey = key.orEmpty(),
                saveAs = name?.takeIf { it.isNotBlank() },
                keepKey = keepKey,
            )
            COMMAND_ADD_PROFILE -> name?.takeIf { it.isNotBlank() }?.let {
                LlmCommand.AddProfile(baseUrl = url.orEmpty(), model = model.orEmpty(), apiKey = key.orEmpty(), name = it)
            }
            COMMAND_USE_PROFILE -> name?.takeIf { it.isNotBlank() }?.let { LlmCommand.UseProfile(it) }
            COMMAND_REMOVE_PROFILE -> name?.takeIf { it.isNotBlank() }?.let { LlmCommand.RemoveProfile(it) }
            else -> null
        }
}
