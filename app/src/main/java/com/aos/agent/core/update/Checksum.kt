package com.aos.agent.core.update

import java.io.InputStream
import java.security.MessageDigest

/**
 * 校验和比对。
 *
 * 装一个来路不明的 APK 比不装危险得多，所以"校验不过就绝不拉起安装器"是硬门：
 * 长度、字符集、逐位比较都要过，缺任何一项都算不通过。
 */
object Checksum {

    fun isExpectedFormat(expectedHex: String): Boolean {
        val trimmed = expectedHex.trim()
        return trimmed.length == 64 && trimmed.all { it in "0123456789abcdefABCDEF" }
    }

    fun sha256Of(input: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE shl 1)
        input.use { stream ->
            while (true) {
                val read = stream.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun matches(input: InputStream, expectedHex: String): Boolean {
        if (!isExpectedFormat(expectedHex)) return false
        val actual = runCatching { sha256Of(input) }.getOrNull() ?: return false
        return actual.equals(expectedHex.trim(), ignoreCase = true)
    }
}
