package com.aos.agent.runtime

import android.content.Context
import android.os.Build
import com.aos.agent.core.engine.PromptContext
import com.aos.agent.core.tools.Tool
import com.aos.agent.system.Capabilities
import com.aos.agent.system.update.AppVersionReader
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 每轮提示组装用的环境快照。只采集可公开字段，不含密钥。
 */
object RuntimeEnvironment {

    private const val AUTOMOTIVE_FEATURE = "android.hardware.type.automotive"

    fun snapshot(
        context: Context,
        tools: List<Tool>,
        skillSummary: String,
    ): PromptContext {
        val app = context.applicationContext
        val version = AppVersionReader.read(app)
        val metrics = app.resources.displayMetrics
        val cliPath = File(app.filesDir, "bin/aos-llm").absolutePath
        val cliPresent = File(cliPath).exists()
        val automotive = app.packageManager.hasSystemFeature(AUTOMOTIVE_FEATURE)
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(Date())
        val toolLines = tools.joinToString("\n") { tool ->
            "- ${tool.name}：${firstSentence(tool.description)}"
        }
        return PromptContext(
            mapOf(
                "package_name" to app.packageName,
                "version_name" to version.versionName,
                "version_code" to version.versionCode.toString(),
                "android_api" to Build.VERSION.SDK_INT.toString(),
                "automotive" to if (automotive) "是" else "否",
                "has_car_api" to if (Capabilities.detect(app).hasCarApi) "是" else "否",
                "now" to time,
                "screen" to "${metrics.widthPixels}x${metrics.heightPixels}",
                "tools" to toolLines,
                "skills" to skillSummary,
                "cli_path" to cliPath,
                "cli_present" to if (cliPresent) "是" else "否",
                "cli_commands" to "status, list, set, add, use, rm, sync",
            ),
        )
    }

    internal fun firstSentence(text: String): String {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return trimmed
        val end = trimmed.indexOfFirst { it == '。' || it == '.' || it == '\n' }
        return if (end in 1 until trimmed.length) trimmed.substring(0, end + 1).trim() else trimmed
    }
}
