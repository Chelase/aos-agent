package com.aos.agent.core.tools

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

data class CommandOutput(val exitCode: Int, val stdout: String, val stderr: String)

/** 命令执行口。抽出来是为了让权限判定与输出截断能在 JVM 单测里跑，不依赖真机。 */
fun interface CommandRunner {
    suspend fun run(argv: List<String>): CommandOutput
}

/**
 * 真机实现：直接 exec argv，**不经过 shell**。
 *
 * 量产车机上 app 沙箱 + SELinux 会挡掉大部分命令，这是预期行为，
 * 所以本工具定位是"探针"而不是终端；真正的终端属 Batch 2 Step 1。
 */
class ProcessCommandRunner(
    private val maxOutputChars: Int = DEFAULT_MAX_OUTPUT_CHARS,
) : CommandRunner {
    override suspend fun run(argv: List<String>): CommandOutput = withContext(Dispatchers.IO) {
        val process = ProcessBuilder(argv)
            .redirectErrorStream(false)
            .directory(File(System.getProperty("java.io.tmpdir") ?: "/data/local/tmp"))
            .start()
        val stdout = process.inputStream.bufferedReader().use { it.readText() }
        val stderr = process.errorStream.bufferedReader().use { it.readText() }
        val exitCode = process.waitFor()
        CommandOutput(exitCode, stdout.take(maxOutputChars), stderr.take(maxOutputChars))
    }

    companion object {
        const val DEFAULT_MAX_OUTPUT_CHARS = 8_000
    }
}

/**
 * shell 工具：白名单内 auto、名单外 ask（无确认器即拒）、黑名单 forbid。
 *
 * 命令按空白切分成 argv 传给 [ProcessBuilder]，不拼 shell 字符串——
 * 一旦经过 shell，`;` `|` `$()` 就成了注入面，白名单也就形同虚设。
 */
class ShellTool(
    private val runner: CommandRunner,
    private val autoCommands: Set<String>,
    private val askCommands: Set<String> = emptySet(),
    private val blockedCommands: Set<String> = emptySet(),
) : Tool {
    override val name: String = "shell_exec"
    override val version: Int = 1
    override val category: String = "shell"

    /** 默认按最保守的 Ask 申报；具体命令的分级由 [permissionFor] 决定。 */
    override val permission: ToolPermission = ToolPermission.Ask

    override val description: String = buildString {
        append("执行单条只读诊断命令，参数 command 为整条命令行。免确认命令：")
        append(autoCommands.sorted().joinToString(", ").ifEmpty { "（无）" })
        append("；需确认命令：")
        append(askCommands.sorted().joinToString(", ").ifEmpty { "（无）" })
        append("。不支持管道、重定向与命令串联，含这些字符会被拒绝。")
    }

    override val inputSchema: JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            put(
                "command",
                buildJsonObject {
                    put("type", "string")
                    put("description", "示例：getprop ro.build.version.release")
                },
            )
        })
        putJsonArray("required") { add("command") }
    }

    override fun permissionFor(args: JsonObject): ToolPermission {
        val program = args.string("command")?.let { tokenize(it) }?.firstOrNull() ?: return ToolPermission.Forbid
        return when {
            program in blockedCommands -> ToolPermission.Forbid
            program in autoCommands -> ToolPermission.Auto
            program in askCommands -> ToolPermission.Ask
            else -> ToolPermission.Ask
        }
    }

    override suspend fun execute(args: JsonObject): ToolResult {
        val raw = args.string("command")
        if (raw.isNullOrBlank()) return ToolResult.Error("缺少 command 参数")
        if (raw.any { it in SHELL_METACHARS }) {
            return ToolResult.Rejected("命令含 shell 元字符，本工具只接受单条命令")
        }
        val argv = tokenize(raw)
        if (argv.isEmpty()) return ToolResult.Error("command 解析后为空")

        val output = runner.run(argv)
        return buildJsonObject {
            put("argv", argv.joinToString(" "))
            put("exit_code", output.exitCode)
            put("stdout", output.stdout.trim())
            if (output.stderr.isNotBlank()) put("stderr", output.stderr.trim())
        }.let { ToolResult.Ok(it) }
    }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it !is kotlinx.serialization.json.JsonNull }?.content

    private fun tokenize(command: String): List<String> =
        command.trim().split(WHITESPACE).filter { it.isNotBlank() }

    private companion object {
        val WHITESPACE = Regex("\\s+")
        val SHELL_METACHARS = charArrayOf(';', '|', '&', '$', '<', '>', '`')
    }
}
