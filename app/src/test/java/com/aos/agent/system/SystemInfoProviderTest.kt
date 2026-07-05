package com.aos.agent.system

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SystemInfoProviderTest {

    @Test
    fun collect_usesFallbackWhenReaderReturnsBlankValues() {
        val provider = SystemInfoProvider(
            object : SystemInfoReader {
                override fun androidVersion(): String = ""
                override fun sdkInt(): Int = 34
                override fun manufacturer(): String = ""
                override fun brand(): String = ""
                override fun model(): String = ""
                override fun device(): String = ""
                override fun isAutomotive(): Boolean = false
            }
        )

        val info = provider.collect()

        assertEquals("Unavailable", info.androidVersion)
        assertEquals("Unavailable", info.manufacturer)
        assertEquals("Unavailable", info.brand)
        assertEquals("Unavailable", info.model)
        assertEquals("Unavailable", info.device)
        assertFalse(info.isAutomotive)
    }
}
