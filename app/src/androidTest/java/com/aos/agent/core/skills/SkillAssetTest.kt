package com.aos.agent.core.skills

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** 证明打包进 APK 的真资产能被枚举、解析并命中，而不只是单测里的字符串。 */
@RunWith(AndroidJUnit4::class)
class SkillAssetTest {

    @Test
    fun shippedSkillsLoadFromApkAssetsAndMatch() {
        val registry = SkillRegistry.parseAll(
            AssetSkillLoader(InstrumentationRegistry.getInstrumentation().targetContext).load(),
        )

        assertEquals(listOf("system_diagnostics", "vehicle_status"), registry.skills.map { it.id })
        assertTrue("内置 skill 不该有被跳过的：${registry.skipped}", registry.skipped.isEmpty())
        assertEquals("vehicle_status", registry.match("车辆状态怎么样")?.id)
        assertEquals("system_diagnostics", registry.match("看下系统信息")?.id)
    }
}
