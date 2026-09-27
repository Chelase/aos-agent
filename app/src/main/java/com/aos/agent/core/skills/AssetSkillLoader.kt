package com.aos.agent.core.skills

import android.content.Context
import java.io.IOException

/**
 * 从 `assets/skills/<id>/skill.json` 枚举定义文件。
 *
 * skill 层里唯一碰 `Context` 的地方：解析与命中都在 [SkillRegistry] 的纯函数里，
 * 这样新增一份 skill 只需要往 assets 放文件，不需要改任何 Kotlin。
 */
class AssetSkillLoader(
    private val context: Context,
) {
    fun load(): List<Pair<String, String>> {
        val ids = runCatching { context.assets.list(SKILLS_DIR)?.toList().orEmpty() }.getOrDefault(emptyList())
        return ids.sorted().mapNotNull { id ->
            val path = "$SKILLS_DIR/$id/$FILE_NAME"
            try {
                context.assets.open(path).bufferedReader().use { path to it.readText() }
            } catch (missing: IOException) {
                // 目录里可能有非 skill 的子目录，读不到就跳过，不算失败。
                null
            }
        }
    }

    private companion object {
        const val SKILLS_DIR = "skills"
        const val FILE_NAME = "skill.json"
    }
}
