package com.aos.agent.data.store

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aos.agent.core.llm.LlmConfig
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LlmConfigStoreTest {

    private val store by lazy { LlmConfigStore(InstrumentationRegistry.getInstrumentation().targetContext) }

    @Before
    fun clearFirst() = runBlocking { store.clear() }

    @Test
    fun returnsNullWhenNothingSaved() = runBlocking {
        assertNull(store.current())
    }

    @Test
    fun roundTripsConfigIncludingExtraHeaders() = runBlocking {
        val config = LlmConfig(
            baseUrl = "https://relay.example/v1",
            model = "some-model",
            apiKey = "sk-secret-value",
            extraHeaders = mapOf("X-Trace" to "car"),
        )
        store.save(config)

        assertEquals(config, store.current())
    }

    @Test
    fun emptyHeadersRoundTrip() = runBlocking {
        val config = LlmConfig(baseUrl = "https://a/v1", model = "m", apiKey = "")
        store.save(config)

        assertEquals(emptyMap<String, String>(), store.current()?.extraHeaders)
    }

    /** 凭据不得出现在配置对象的字符串形态里，否则日志会把它带出去。 */
    @Test
    fun configStringFormMasksTheKey() {
        val config = LlmConfig(baseUrl = "https://a/v1", model = "m", apiKey = "sk-very-secret")

        val described = config.describe()

        assertFalse("掩码失效：$described", described.contains("sk-very-secret"))
        assertEquals("LlmConfig(baseUrl=https://a/v1, model=m, apiKey=set(len=14))", described)
    }
}
