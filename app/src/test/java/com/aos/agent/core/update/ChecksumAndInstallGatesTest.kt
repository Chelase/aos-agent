package com.aos.agent.core.update

import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 校验与安装门控的纯逻辑测试：装错包比不装危险，所以拒绝路径要一条条钉住。 */
class ChecksumAndInstallGatesTest {

    private val abcSha256 = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"

    @Test
    fun sha256MatchesKnownVector() {
        assertEquals(abcSha256, Checksum.sha256Of(ByteArrayInputStream("abc".toByteArray())))
    }

    @Test
    fun acceptsCorrectChecksumAndRejectsWrongOne() {
        assertTrue(Checksum.matches(ByteArrayInputStream("abc".toByteArray()), abcSha256))
        assertTrue(Checksum.matches(ByteArrayInputStream("abc".toByteArray()), abcSha256.uppercase()))
        assertFalse(Checksum.matches(ByteArrayInputStream("abd".toByteArray()), abcSha256))
    }

    @Test
    fun refusesUnusableExpectedChecksums() {
        val stream = ByteArrayInputStream("abc".toByteArray())
        assertFalse(Checksum.matches(stream, ""))
        assertFalse(Checksum.matches(stream, "deadbeef"))
        assertFalse(Checksum.matches(stream, "z".repeat(64)))
        assertFalse(Checksum.isExpectedFormat(" " + abcSha256.dropLast(1)))
        assertTrue(Checksum.isExpectedFormat("  $abcSha256  "))
    }

    @Test
    fun streamErrorDegradesToNoMatch() {
        val broken = object : java.io.InputStream() {
            override fun read(): Int = throw java.io.IOException("disk gone")
        }
        assertFalse(Checksum.matches(broken, abcSha256))
    }

    @Test
    fun permissionGateComesBeforeInstallerGate() {
        // 没授权时先说授权，别让用户去翻一个根本不存在的安装器
        assertEquals(InstallGate.PERMISSION_NEEDED, InstallGates.decide(false, false))
        assertEquals(InstallGate.PERMISSION_NEEDED, InstallGates.decide(false, true))
        assertEquals(InstallGate.NO_INSTALLER, InstallGates.decide(true, false))
        assertEquals(InstallGate.READY, InstallGates.decide(true, true))
    }

    @Test
    fun untrustedUrlsAreRefusedBeforeDownload() {
        assertTrue(InstallGates.refuseUntrusted("http://github.com/a/b.apk"))
        assertTrue(InstallGates.refuseUntrusted("https://evil.github.com/a.apk"))
        assertTrue(InstallGates.refuseUntrusted("ftp://github.com/a.apk"))
        assertFalse(InstallGates.refuseUntrusted("https://github.com/Chelase/aos-agent/releases/download/v1/a.apk"))
    }
}
