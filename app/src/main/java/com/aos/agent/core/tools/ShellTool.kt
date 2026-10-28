package com.aos.agent.core.tools

import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
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
 * 原始输出在此完整读入（带安全上限）；面向模型的头尾截断由 [ShellTool] 负责。
 */
class ProcessCommandRunner(
    private val maxRawChars: Int = DEFAULT_MAX_RAW_CHARS,
) : CommandRunner {
    override suspend fun run(argv: List<String>): CommandOutput = withContext(Dispatchers.IO) {
        val process = ProcessBuilder(argv)
            .redirectErrorStream(false)
            .directory(File(System.getProperty("java.io.tmpdir") ?: "/data/local/tmp"))
            .start()
        val stdout = process.inputStream.bufferedReader().use { it.readText() }.take(maxRawChars)
        val stderr = process.errorStream.bufferedReader().use { it.readText() }.take(maxRawChars)
        val exitCode = process.waitFor()
        CommandOutput(exitCode, stdout, stderr)
    }

    companion object {
        /** 读入安全上限，防止极端 dumpsys 撑爆内存；展示截断另走 [truncateHeadTail]。 */
        const val DEFAULT_MAX_RAW_CHARS = 512_000
        const val DEFAULT_KEEP_EACH_SIDE = 4_000

        fun truncateHeadTail(text: String, keepEachSide: Int = DEFAULT_KEEP_EACH_SIDE): Pair<String, Boolean> {
            if (text.length <= keepEachSide * 2) return text to false
            val omitted = text.length - keepEachSide * 2
            val clipped = text.take(keepEachSide) + "\n…省略 ${omitted} 字符…\n" + text.takeLast(keepEachSide)
            return clipped to true
        }
    }
}

/**
 * shell 工具：白名单内 auto、名单外 ask（无确认器即拒）、黑名单 forbid。
 *
 * 命令按空白切分成 argv 传给 [ProcessBuilder]，不拼 shell 字符串——
 * 一旦经过 shell，`;` `|` `$()` 就成了注入面，白名单也就形同虚设。
 * 管道过滤走 [grep]/[head] 参数，不走 shell。
 */
class ShellTool(
    private val runner: CommandRunner,
    private val autoCommands: Set<String>,
    private val askCommands: Set<String> = emptySet(),
    private val blockedCommands: Set<String> = emptySet(),
    private val outputDir: File? = null,
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
        append("可用可选参数 grep（按子串过滤 stdout 行）与 head（过滤后保留前 N 行）。")
        append("超长输出会头尾截断；完整结果路径在 full_output，可用 total_lines / truncated 判断是否需二次读取。")
        append("truncated 为 true 时，truncation_notice 会给出完整输出的落盘路径，可再次读取该文件取回全部内容。")
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
            put(
                "grep",
                buildJsonObject {
                    put("type", "string")
                    put("description", "按子串过滤 stdout 行，区分大小写")
                },
            )
            put(
                "head",
                buildJsonObject {
                    put("type", "integer")
                    put("description", "过滤后只保留前 N 行")
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

    /** 车辆操控禁令：命中则无论命令在哪个白名单里都不执行。 */
    override fun hardBlockReason(args: JsonObject): String? {
        val raw = args.string("command") ?: return null
        return VehicleActuation.commandReason(tokenize(raw))
    }

    override suspend fun execute(args: JsonObject): ToolResult {
        val raw = args.string("command")
        if (raw.isNullOrBlank()) return ToolResult.Error("缺少 command 参数")
        if (raw.any { it in SHELL_METACHARS }) {
            return ToolResult.Rejected(
                RejectionReason.InvalidInput,
                "命令含 shell 元字符，本工具只接受单条命令",
            )
        }
        val argv = tokenize(raw)
        if (argv.isEmpty()) return ToolResult.Error("command 解析后为空")

        val output = runner.run(argv)
        val grep = args.string("grep")
        val head = args.int("head")
        val filtered = filterStdout(output.stdout, grep, head)
        val totalLines = if (filtered.isEmpty()) 0 else filtered.lineSequence().count()
        val fullPath = writeFullOutput(filtered)
        val (display, truncated) = ProcessCommandRunner.truncateHeadTail(filtered)

        return buildJsonObject {
            put("argv", argv.joinToString(" "))
            put("exit_code", output.exitCode)
            put("stdout", display.trim())
            put("total_lines", totalLines)
            put("truncated", truncated)
            fullPath?.let { put("full_output", it) }
            if (truncated) {
                put("truncation_notice", truncationNotice(filtered.length, fullPath))
            }
            if (output.stderr.isNotBlank()) put("stderr", output.stderr.trim())
        }.let { ToolResult.Ok(it) }
    }

    /**
     * 截断后模型手里只有片段，必须明确告诉它去哪取全文——否则它会以为命令没输出那么多。
     * 落盘路径缺失（未配 outputDir）时如实说明拿不到全文，不编路径。
     */
    private fun truncationNotice(originalChars: Int, fullPath: String?): String = if (fullPath == null) {
        "输出过长已截断（原始 $originalChars 字符），当前环境未配置落盘目录，无法读取完整内容；" +
            "可改用 grep 过滤或 head 缩小范围后重试。"
    } else {
        "输出过长已截断（原始 $originalChars 字符），完整内容已保存到 $fullPath，可再次读取该文件取回全部内容。"
    }

    private fun filterStdout(stdout: String, grep: String?, head: Int?): String {
        var lines = stdout.lineSequence()
        if (!grep.isNullOrEmpty()) {
            lines = lines.filter { it.contains(grep) }
        }
        if (head != null && head >= 0) {
            lines = lines.take(head)
        }
        return lines.joinToString("\n")
    }

    private fun writeFullOutput(text: String): String? {
        val dir = outputDir ?: return null
        return runCatching {
            dir.mkdirs()
            val file = File(dir, "${UUID.randomUUID()}.txt")
            file.writeText(text)
            file.absolutePath
        }.getOrNull()
    }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it !is kotlinx.serialization.json.JsonNull }?.content

    private fun JsonObject.int(key: String): Int? =
        this[key]?.jsonPrimitive?.intOrNull
            ?: this[key]?.jsonPrimitive?.contentOrNull?.toIntOrNull()

    private fun tokenize(command: String): List<String> =
        command.trim().split(WHITESPACE).filter { it.isNotBlank() }

    private companion object {
        val WHITESPACE = Regex("\\s+")
        val SHELL_METACHARS = charArrayOf(';', '|', '&', '$', '<', '>', '`')
    }
}
