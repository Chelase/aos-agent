package com.aos.agent.core.web

import java.net.IDN
import java.net.InetAddress
import java.net.URI
import java.net.UnknownHostException

/**
 * 外网 URL 守卫：只放行 https 的公网域名。
 *
 * 存在的理由是 SSRF——车机上的 Agent 若能被诱导去请求内网地址，
 * 等于把车机自身（诊断端口、OTA 服务、内网设备）交出去。所以这里
 * **拒绝解析到内网的域名**，而不是只做字符串检查：
 * 域名可以指向 10.x/127.x，靠字符串看不出问题，必须解析后判地址。
 *
 * 判断交给调用方做决定的地方：
 * - 只放行 https（http 明文可被中间人改内容）；
 * - 拒绝 IP 字面量（防 DNS rebinding：先解析再连，中间换地址）；
 * - 拒绝回环/私网/链路本地（含 169.254.169.254 云元数据）/ IPv6 唯一本地地址；
 * - 拒绝 localhost / *.local / *.internal 这类内网专用后缀。
 */
object UrlGuard {

    enum class Decision { ALLOWED, NOT_HTTPS, MALFORMED, NOT_PUBLIC, IP_LITERAL }

    data class Verdict(val decision: Decision, val reason: String) {
        val allowed: Boolean get() = decision == Decision.ALLOWED
    }

    /** 只做静态判断，不做 DNS 解析。用于不信任的主机名展示与快速失败。 */
    fun check(raw: String): Verdict {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return deny(Decision.MALFORMED, "URL 为空")
        if (trimmed.length > MAX_URL_LENGTH) {
            return deny(Decision.MALFORMED, "URL 过长（上限 $MAX_URL_LENGTH 字符）")
        }
        val uri = runCatching { URI(trimmed) }.getOrNull()
            ?: return deny(Decision.MALFORMED, "URL 格式非法")
        if (!uri.scheme.equals("https", ignoreCase = true)) {
            return deny(Decision.NOT_HTTPS, "只允许 https，当前是 ${uri.scheme ?: "无 scheme"}")
        }
        val host = uri.host?.lowercase()?.takeIf { it.isNotBlank() }
            ?: return deny(Decision.MALFORMED, "URL 缺少主机名")
        // 主机名先转 punycode 再判断后缀：中文域名不转的话 ".local" 之类检查会被绕过
        val normalized = normalizeHost(host)
        if (normalized == "localhost" || normalized.endsWith(".localhost")) {
            return deny(Decision.NOT_PUBLIC, "禁止访问 localhost")
        }
        if (normalized.endsWith(".local") || normalized.endsWith(".internal") || normalized.endsWith(".lan")) {
            return deny(Decision.NOT_PUBLIC, "禁止访问内网专用域名：$normalized")
        }
        if (isIpLiteral(normalized)) {
            return deny(Decision.IP_LITERAL, "禁止直接使用 IP 地址：$normalized")
        }
        return Verdict(Decision.ALLOWED, "ok")
    }

    /**
     * 完整判断：静态检查 + DNS 解析后判地址。
     *
     * 解析失败**不放行**：域名解析不出来时无法证明它是公网地址，
     * 让它过等于把判断交给网络层。调用方应把解析失败当网络错误处理。
     *
     * **注意代理环境（车机常态）**：若设备走 HTTP 代理，域名由代理侧解析，
     * 本地 `InetAddress.getAllByName` 会失败——此时本方法会误判为"非公网"而拒绝。
     * 生产用 URL 校验请走 [check]（静态判断），需要 DNS 判定的场景（如 SSRF 防护）
     * 要么用 [check] 配合服务端二次校验，要么在代理环境下跳过 DNS 判定。
     */
    fun checkWithDns(raw: String, resolver: (String) -> List<InetAddress> = { host ->
        runCatching { InetAddress.getAllByName(host).toList() }.getOrElse { emptyList() }
    }): Verdict {
        val static = check(raw)
        if (!static.allowed) return static

        val trimmed = raw.trim()
        val uri = runCatching { URI(trimmed) }.getOrNull() ?: return static
        val host = uri.host?.lowercase()?.takeIf { it.isNotBlank() }?.let(::normalizeHost) ?: return static

        val addresses = resolver(host)
        if (addresses.isEmpty()) {
            return deny(Decision.NOT_PUBLIC, "域名解析失败或无记录，无法确认是公网地址：$host")
        }
        val bad = addresses.firstOrNull { !isPublicAddress(it) }
        if (bad != null) {
            return deny(Decision.NOT_PUBLIC, "目标解析到非公网地址（${bad.hostAddress}），已拒绝")
        }
        return static
    }

    /** 是否是公网可路由地址。 */
    fun isPublicAddress(address: InetAddress): Boolean {
        val bytes = address.address
        if (bytes.size == 4) {
            val b0 = bytes[0].toInt() and 0xFF
            val b1 = bytes[1].toInt() and 0xFF
            return when {
                b0 == 127 -> false // 回环
                b0 == 0 -> false // 本网络
                b0 == 10 -> false // 私网
                b0 == 172 && b1 in 16..31 -> false // 私网
                b0 == 192 && b1 == 168 -> false // 私网
                b0 == 169 && b1 == 254 -> false // 链路本地，含云元数据 169.254.169.254
                b0 == 100 && b1 in 64..127 -> false // CGNAT 100.64/10
                b0 == 192 && b1 == 0 && bytes[2].toInt() == 0 -> false // 保留
                b0 >= 224 -> false // 组播与保留
                else -> true
            }
        }
        if (bytes.size == 16) {
            val first = bytes[0].toInt() and 0xFF
            val second = bytes[1].toInt() and 0xFF
            // fc00::/7 唯一本地地址
            if (first == 0xFC || first == 0xFD) return false
            // fe80::/10 链路本地
            if (first == 0xFE && second and 0xC0 == 0x80) return false
            // ::1 回环
            if (bytes.take(15).all { it == 0.toByte() } && bytes[15].toInt() == 1) return false
            // :: 回环与未指定
            if (bytes.all { it == 0.toByte() }) return false
            // IPv4 映射地址（::ffff:a.b.c.d）按 IPv4 规则再判一次
            if (bytes.take(10).all { it == 0.toByte() } && bytes[10].toInt() == 0xFF && bytes[11].toInt() == 0xFF) {
                return isPublicAddress(
                    InetAddress.getByAddress(bytes.copyOfRange(12, 16))
                )
            }
            return true
        }
        return false
    }

    private fun isIpLiteral(host: String): Boolean {
        if (host.startsWith("[") && host.endsWith("]")) return true // IPv6 字面量
        // IPv4：四段纯数字，或能被解析成 IPv4 的形式
        val parts = host.split('.')
        if (parts.size == 4 && parts.all { it.isNotEmpty() && it.all(Char::isDigit) }) return true
        return runCatching {
            val addr = InetAddress.getByName(host)
            addr is java.net.Inet4Address
        }.getOrDefault(false) && host.all { it.isDigit() || it == '.' }
    }

    /** 主机名转 punycode，避免中文域名绕过后缀判断。 */
    fun normalizeHost(host: String): String = runCatching { IDN.toASCII(host).lowercase() }.getOrDefault(host.lowercase())

    private fun deny(decision: Decision, reason: String) = Verdict(decision, reason)

    private const val MAX_URL_LENGTH = 2048
}
