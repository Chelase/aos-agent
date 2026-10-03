package com.aos.agent.core.update

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * CI 产出的 `update.json`。字段与 `.github/workflows/release.yml` 一一对应，改一边要改另一边。
 *
 * 手工解析而不是 `@Serializable`：本工程没有引序列化插件，
 * LLM/MCP/Skill 也都是 `parseToJsonElement` 这一套，不为一个数据类破例。
 */
data class UpdateManifest(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val sha256: String,
    val notes: String = "",
    val publishedAt: String = "",
)

enum class UpdateFailure { NETWORK, BAD_MANIFEST, UNTRUSTED_SOURCE }

/** 检查结论：要么"没新版"，要么"有新版且下载地址可信"，要么带原因地失败。 */
sealed interface UpdateOutcome {
    data object UpToDate : UpdateOutcome
    data class Available(val manifest: UpdateManifest) : UpdateOutcome
    data class Failed(val failure: UpdateFailure) : UpdateOutcome
}

/**
 * 更新判定的纯函数层。
 *
 * 两条硬规矩：
 * 1. 只认官方发布主机与 HTTPS——更新通道不能变成任意 URL 的侧载入口；
 * 2. 只往更高 versionCode 升，清单写错或被人塞一个更低版本时不动。
 */
object UpdateDecision {

    /** 精确主机名，不做后缀匹配：`evil.github.com` 也算官方子域这种结论不能默认成立。 */
    private val ALLOWED_HOSTS = setOf(
        "github.com",
        "raw.githubusercontent.com",
        "objects.githubusercontent.com",
        "release-assets.githubusercontent.com",
    )

    fun isTrustedUrl(raw: String): Boolean = runCatching {
        val uri = java.net.URI(raw.trim())
        uri.scheme?.lowercase() == "https" && uri.host?.lowercase() in ALLOWED_HOSTS
    }.getOrDefault(false)

    /** 校验和必须是 64 位十六进制，否则等于没校验。 */
    fun hasUsableChecksum(manifest: UpdateManifest): Boolean =
        manifest.sha256.trim().length == 64 && manifest.sha256.trim().all { it in "0123456789abcdefABCDEF" }

    fun decide(localVersionCode: Int, manifest: UpdateManifest): UpdateOutcome = when {
        manifest.versionCode <= 0 -> UpdateOutcome.Failed(UpdateFailure.BAD_MANIFEST)
        !isTrustedUrl(manifest.apkUrl) -> UpdateOutcome.Failed(UpdateFailure.UNTRUSTED_SOURCE)
        !hasUsableChecksum(manifest) -> UpdateOutcome.Failed(UpdateFailure.UNTRUSTED_SOURCE)
        manifest.versionCode <= localVersionCode -> UpdateOutcome.UpToDate
        else -> UpdateOutcome.Available(manifest)
    }

    fun parse(json: String): UpdateManifest? = runCatching {
        val root = Json.parseToJsonElement(json) as? JsonObject ?: return null
        val code = root["versionCode"]?.jsonPrimitive?.intOrNull ?: return null
        val name = root["versionName"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: return null
        val url = root["apkUrl"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: return null
        val sha = root["sha256"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: return null
        UpdateManifest(
            versionCode = code,
            versionName = name,
            apkUrl = url,
            sha256 = sha,
            notes = root["notes"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            publishedAt = root["publishedAt"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        )
    }.getOrNull()

}
