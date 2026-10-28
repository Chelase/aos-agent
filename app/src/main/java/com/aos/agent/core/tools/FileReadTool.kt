package com.aos.agent.core.tools

import java.io.File
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * 只读分页读取 `shell_exec` 落盘的完整输出（`full_output` 路径）。
 *
 * `shell_exec` 超长输出只给模型看头尾， mitigation 不是让模型再调一次
 * `shell_exec`（那会重新执行命令），而是用本工具按 `offset/limit` 翻页，
 * 可选 `grep` 先过滤。只允许读工具输出目录内的文件，目录外直接拒绝，
 * 不经过确认闸（本身就是只读）。
 */
class FileReadTool(
    private val allowedDirs: List<File>,
) : Tool {
    override val name: String = "file_read"
    override val version: Int = 1
    override val category: String = "filesystem"
    override val permission: ToolPermission = ToolPermission.Auto

    override val description: String =
        "分页读取 shell 落盘全文：按 path/offset/limit 读 full_output 文件，可选 grep 过滤行。" +
            "只允许读取工具输出目录内的文件，目录外会被拒绝。"

    override val inputSchema: JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            put("path", buildJsonObject {
                put("type", "string")
                put("description", "shell_exec 返回的 full_output 绝对路径")
            })
            put("offset", buildJsonObject {
                put("type", "integer")
                put("description", "从第几行开始（0 起），默认 0")
            })
            put("limit", buildJsonObject {
                put("type", "integer")
                put("description", "最多返回多少行，默认 200，上限 500")
            })
            put("grep", buildJsonObject {
                put("type", "string")
                put("description", "按子串过滤行，区分大小写")
            })
        })
        putJsonArray("required") { add("path") }
    }

    override suspend fun execute(args: JsonObject): ToolResult {
        val rawPath = args.string("path")?.trim().orEmpty()
        if (rawPath.isEmpty()) return ToolResult.Error("缺少 path 参数")
        val offset = args.int("offset") ?: DEFAULT_OFFSET
        if (offset < 0) return ToolResult.Error("offset 不能为负数")
        val limit = args.int("limit") ?: DEFAULT_LIMIT
        if (limit <= 0) return ToolResult.Error("limit 必须为正数")
        val capped = limit.coerceAtMost(MAX_LIMIT)
        val grep = args.string("grep")

        val target = resolve(rawPath) ?: return ToolResult.Rejected(
            RejectionReason.Forbidden,
            "只允许读取工具输出目录内的文件",
        )
        if (!target.isFile) return ToolResult.Error("文件不存在：$rawPath")

        val lines = runCatching { target.readLines() }.getOrNull()
            ?: return ToolResult.Error("文件读取失败：$rawPath")
        val filtered = if (grep.isNullOrEmpty()) lines else lines.filter { it.contains(grep) }
        val total = filtered.size
        val slice = if (offset >= total) emptyList() else filtered.drop(offset).take(capped)
        val text = slice.joinToString("\n")
        val (display, charTruncated) = ProcessCommandRunner.truncateHeadTail(text)
        val hasMore = offset + slice.size < total

        return buildJsonObject {
            put("path", target.absolutePath)
            put("total_lines", total)
            put("offset", offset)
            put("returned_lines", slice.size)
            put("has_more", hasMore)
            put("truncated", charTruncated)
            put("stdout", display.trim())
            if (hasMore || charTruncated) {
                put("notice", nextHint(target.absolutePath, offset + slice.size, hasMore, charTruncated))
            }
        }.let { ToolResult.Ok(it) }
    }

    private fun resolve(rawPath: String): File? {
        val base = allowedDirs.mapNotNull { runCatching { it.canonicalFile }.getOrNull() }
        val target = runCatching {
            val file = File(rawPath).let {
                if (it.isAbsolute) it else File(base.firstOrNull() ?: File("."), rawPath)
            }
            file.canonicalFile
        }.getOrNull() ?: return null
        val inside = base.any { root ->
            target.path == root.path || target.path.startsWith(root.path + File.separator)
        }
        return if (inside) target else null
    }

    private fun nextHint(path: String, nextOffset: Int, hasMore: Boolean, charTruncated: Boolean): String =
        buildString {
            if (hasMore) append("还有剩余行，可用 file_read 继续读 path=$path offset=$nextOffset。")
            if (charTruncated) append("单页过长已截断，可改用 grep 缩小范围或调小 limit 后重试。")
        }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it !is kotlinx.serialization.json.JsonNull }?.content

    private fun JsonObject.int(key: String): Int? =
        this[key]?.jsonPrimitive?.intOrNull
            ?: this[key]?.jsonPrimitive?.contentOrNull?.toIntOrNull()

    private companion object {
        const val DEFAULT_OFFSET = 0
        const val DEFAULT_LIMIT = 200
        const val MAX_LIMIT = 500
    }
}
