package com.aos.agent.i18n

import android.app.LocaleManager
import android.content.Context
import android.os.LocaleList

/** 应用支持的语言。中文是默认语言，与车机系统语言无关。 */
enum class AppLanguage(val tag: String) {
    CHINESE("zh-CN"),
    ENGLISH("en"),
    ;

    fun next(): AppLanguage = if (this == CHINESE) ENGLISH else CHINESE

    companion object {
        val DEFAULT = CHINESE

        /** 解析语言标签，只按主语言子标签匹配，忽略地区差异（zh-Hans-CN → zh）。 */
        fun fromTag(tag: String?): AppLanguage? {
            val primary = tag?.substringBefore('-')?.lowercase()?.takeIf { it.isNotBlank() }
                ?: return null
            return entries.firstOrNull { it.tag.substringBefore('-') == primary }
        }
    }
}

/**
 * 语言持久化读写。抽成接口以便纯 Kotlin 测试，
 * 与 [com.aos.agent.system.SystemInfoReader] 保持同一注入风格。
 */
interface LocaleStore {
    /** 返回当前已设置的语言标签；未设置返回 null。 */
    fun currentTag(): String?

    fun apply(tag: String)
}

/**
 * 语言控制器。负责「首启兜底中文」与「切换语言」两件事。
 *
 * 语言状态由框架的 `LocaleManager` 持久化，不自建 SharedPreferences，
 * 避免应用状态与系统 per-app locale 记录出现两份真相。
 */
class AppLocaleController(
    private val store: LocaleStore,
) {
    /** 读取当前语言；未设置或无法识别时回落到默认语言。 */
    fun current(): AppLanguage = AppLanguage.fromTag(store.currentTag()) ?: AppLanguage.DEFAULT

    /**
     * 首次启动时若未设置过语言，写入默认中文。
     * 已设置过则保留用户选择，不覆盖。
     */
    fun ensureDefault(): AppLanguage {
        val existing = AppLanguage.fromTag(store.currentTag())
        if (existing != null) {
            return existing
        }
        store.apply(AppLanguage.DEFAULT.tag)
        return AppLanguage.DEFAULT
    }

    fun switchTo(language: AppLanguage) {
        store.apply(language.tag)
    }

    /** 在中英文之间切换，返回切换后的语言。 */
    fun toggle(): AppLanguage {
        val target = current().next()
        store.apply(target.tag)
        return target
    }
}

/** 基于框架 `LocaleManager` 的实现（API 33+，本项目 minSdk 34）。 */
class SystemLocaleStore(
    context: Context,
) : LocaleStore {

    private val localeManager: LocaleManager? =
        context.getSystemService(LocaleManager::class.java)

    override fun currentTag(): String? {
        val locales = localeManager?.applicationLocales ?: return null
        return if (locales.isEmpty) null else locales.get(0)?.toLanguageTag()
    }

    override fun apply(tag: String) {
        localeManager?.applicationLocales = LocaleList.forLanguageTags(tag)
    }
}
