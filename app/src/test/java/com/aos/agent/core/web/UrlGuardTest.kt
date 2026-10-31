package com.aos.agent.core.web

import java.net.InetAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * URL 守卫测试。
 *
 * 拒绝类用例比通过类用例重要：守卫放行了不该放行的地址，
 * 后果是车机把内网/自身暴露出去，而这种问题在车上无法及时发现。
 */
class UrlGuardTest {

    @Test
    fun rejectsNonHttpsSchemes() {
        listOf(
            "http://example.com",
            "ftp://example.com",
            "file:///etc/passwd",
            "content://media/external/file/1",
            "javascript:alert(1)",
        ).forEach { url ->
            val verdict = UrlGuard.check(url)
            assertFalse("应拒绝 $url，实际 ${verdict.reason}", verdict.allowed)
        }
    }

    @Test
    fun rejectsLoopbackAndPrivateAddresses() {
        listOf(
            "https://127.0.0.1/",
            "https://127.1.2.3/",
            "https://10.0.0.1/",
            "https://10.255.255.255/",
            "https://172.16.0.1/",
            "https://172.31.255.255/",
            "https://192.168.1.1/",
            "https://169.254.1.1/",
            // 云元数据地址：能拿到实例凭据
            "https://169.254.169.254/latest/meta-data/",
            "https://0.0.0.0/",
        ).forEach { url ->
            val verdict = UrlGuard.check(url)
            assertFalse("应拒绝 $url", verdict.allowed)
        }
    }

    @Test
    fun rejectsIpLiteralsEvenWhenPublic() {
        // 公网 IP 直连也要拒：DNS rebinding 的第一步就是绕过域名
        listOf(
            "https://8.8.8.8/",
            "https://1.1.1.1/",
            "https://[::1]/",
            "https://[2606:4700:4700::1111]/",
        ).forEach { url ->
            val verdict = UrlGuard.check(url)
            assertTrue("公网 IP 字面量应被拒：$url", verdict.decision == UrlGuard.Decision.IP_LITERAL)
        }
    }

    @Test
    fun rejectsInternalOnlyHostnames() {
        listOf(
            "https://localhost/",
            "https://localhost:8080/",
            "https://api.localhost/",
            "https://printer.local/",
            "https://metadata.internal/",
            "https://gateway.lan/",
        ).forEach { url ->
            val verdict = UrlGuard.check(url)
            assertFalse("应拒绝 $url", verdict.allowed)
        }
    }

    @Test
    fun rejectsMalformedUrls() {
        listOf("", "   ", "not a url", "https://", "://example.com").forEach { url ->
            val verdict = UrlGuard.check(url)
            assertFalse("应拒绝 '$url'", verdict.allowed)
        }
    }

    @Test
    fun rejectsOverlongUrl() {
        val long = "https://example.com/" + "a".repeat(3000)
        assertFalse(UrlGuard.check(long).allowed)
    }

    @Test
    fun allowsPublicHttpsDomains() {
        listOf(
            "https://example.com",
            "https://www.example.com/path?a=1",
            "https://zh.wikipedia.org/wiki/Android",
            "https://html.duckduckgo.com/html/?q=test",
        ).forEach { url ->
            val verdict = UrlGuard.check(url)
            assertTrue("应放行 $url，实际 ${verdict.reason}", verdict.allowed)
        }
    }

    @Test
    fun dnsResolvingToPrivateAddressIsRejected() {
        // 域名看着正常，解析到内网——这正是静态检查拦不住的那类
        val resolver: (String) -> List<InetAddress> = { listOf(InetAddress.getByName("10.1.2.3")) }
        val verdict = UrlGuard.checkWithDns("https://sneaky.example.com/", resolver)
        assertFalse("解析到内网应被拒", verdict.allowed)
    }

    @Test
    fun dnsResolvingToPublicAddressIsAllowed() {
        val resolver: (String) -> List<InetAddress> = { listOf(InetAddress.getByName("93.184.216.34")) }
        val verdict = UrlGuard.checkWithDns("https://example.com/", resolver)
        assertTrue("公网解析应放行", verdict.allowed)
    }

    @Test
    fun dnsFailureIsNotAllowed() {
        // 解析不出来时无法证明是公网地址，不能放行
        val resolver: (String) -> List<InetAddress> = { emptyList() }
        val verdict = UrlGuard.checkWithDns("https://nonexistent.example.com/", resolver)
        assertFalse("解析失败不应放行", verdict.allowed)
    }

    @Test
    fun identifiesPublicAddressRanges() {
        assertFalse(UrlGuard.isPublicAddress(InetAddress.getByName("127.0.0.1")))
        assertFalse(UrlGuard.isPublicAddress(InetAddress.getByName("10.0.0.1")))
        assertFalse(UrlGuard.isPublicAddress(InetAddress.getByName("192.168.0.1")))
        assertFalse(UrlGuard.isPublicAddress(InetAddress.getByName("169.254.169.254")))
        assertFalse(UrlGuard.isPublicAddress(InetAddress.getByName("::1")))
        assertTrue(UrlGuard.isPublicAddress(InetAddress.getByName("8.8.8.8")))
        assertTrue(UrlGuard.isPublicAddress(InetAddress.getByName("93.184.216.34")))
    }

    @Test
    fun cgnatRangeIsTreatedAsPrivate() {
        // 100.64/10 是运营商级 NAT，不该当成公网
        assertFalse(UrlGuard.isPublicAddress(InetAddress.getByName("100.64.0.1")))
    }

    @Test
    fun normalizesInternationalHostnames() {
        assertEquals("xn--fsqu00a.com", UrlGuard.normalizeHost("例子.com"))
    }
}
