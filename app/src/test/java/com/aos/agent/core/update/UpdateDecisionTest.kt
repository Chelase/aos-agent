package com.aos.agent.core.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 更新判定纯函数测试。
 *
 * 这里管的是"能不能装到一个来路不明的包"，所以拒绝类用例比通过类用例更重要。
 */
class UpdateDecisionTest {

    private val sha = "a".repeat(64)

    private fun manifest(
        code: Int = 99,
        url: String = "https://github.com/Chelase/aos-agent/releases/download/v1.2.3/aos-agent-1.2.3.apk",
        sha256: String = sha,
    ) = UpdateManifest(versionCode = code, versionName = "1.2.3", apkUrl = url, sha256 = sha256)

    @Test
    fun parsesACompleteManifest() {
        val parsed = UpdateDecision.parse(
            """{"versionCode":10203,"versionName":"1.2.3","apkUrl":"https://github.com/x/y.apk","sha256":"$sha","notes":"n"}""",
        )
        assertEquals(10203, parsed?.versionCode)
        assertEquals("1.2.3", parsed?.versionName)
        assertEquals("n", parsed?.notes)
    }

    @Test
    fun toleratesUnknownFieldsButNotMissingOnes() {
        val extra = UpdateDecision.parse(
            """{"versionCode":1,"versionName":"1","apkUrl":"https://github.com/a/b","sha256":"$sha","future":"x"}""",
        )
        assertEquals(1, extra?.versionCode)
        assertNull(UpdateDecision.parse("""{"versionCode":1}"""))
        assertNull(UpdateDecision.parse("not json"))
        assertNull(UpdateDecision.parse(""))
        assertNull(UpdateDecision.parse("[]"))
        // versionCode 写成字符串也不能蒙对
        assertNull(UpdateDecision.parse("""{"versionCode":"abc","versionName":"1","apkUrl":"u","sha256":"$sha"}"""))
    }

    @Test
    fun higherRemoteVersionOffersUpdate() {
        val outcome = UpdateDecision.decide(localVersionCode = 1, manifest = manifest(code = 10203))
        assertTrue(outcome is UpdateOutcome.Available)
        assertEquals("1.2.3", (outcome as UpdateOutcome.Available).manifest.versionName)
    }

    @Test
    fun sameOrLowerRemoteVersionSaysUpToDate() {
        assertEquals(UpdateOutcome.UpToDate, UpdateDecision.decide(10203, manifest(code = 10203)))
        assertEquals(UpdateOutcome.UpToDate, UpdateDecision.decide(10204, manifest(code = 10203)))
    }

    @Test
    fun nonHttpsSourceIsRefused() {
        val outcome = UpdateDecision.decide(
            1,
            manifest(url = "http://github.com/Chelase/aos-agent/releases/download/v1/a.apk"),
        )
        assertEquals(UpdateOutcome.Failed(UpdateFailure.UNTRUSTED_SOURCE), outcome)
    }

    @Test
    fun lookalikeHostsAreRefused() {
        // 后缀伪装与前缀伪装都不算官方主机
        assertFalse(UpdateDecision.isTrustedUrl("https://evil-github.com/a.apk"))
        assertFalse(UpdateDecision.isTrustedUrl("https://github.com.evil.io/a.apk"))
        assertFalse(UpdateDecision.isTrustedUrl("https://user@github.com.evil.io/a.apk"))
        assertTrue(UpdateDecision.isTrustedUrl("https://raw.githubusercontent.com/a/b/c"))
        assertTrue(UpdateDecision.isTrustedUrl("https://release-assets.githubusercontent.com/x"))
    }

    @Test
    fun unusableChecksumIsRefused() {
        val short = UpdateDecision.decide(1, manifest(sha256 = "deadbeef"))
        assertEquals(UpdateOutcome.Failed(UpdateFailure.UNTRUSTED_SOURCE), short)
        val nonHex = UpdateDecision.decide(1, manifest(sha256 = "g".repeat(64)))
        assertEquals(UpdateOutcome.Failed(UpdateFailure.UNTRUSTED_SOURCE), nonHex)
    }

    @Test
    fun manifestWithoutUsableVersionCodeFailsAsBadManifest() {
        assertEquals(
            UpdateOutcome.Failed(UpdateFailure.BAD_MANIFEST),
            UpdateDecision.decide(1, manifest(code = 0)),
        )
        assertEquals(
            UpdateOutcome.Failed(UpdateFailure.BAD_MANIFEST),
            UpdateDecision.decide(1, manifest(code = -5)),
        )
    }
}
