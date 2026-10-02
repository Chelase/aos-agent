package com.aos.agent.data.store

import androidx.test.platform.app.InstrumentationRegistry
import com.aos.agent.core.llm.LlmConfig
import kotlinx.coroutines.runBlocking
import org.junit.Test

/**
 * 配置种子：通过 am instrument 的 -e 参数直接写 DataStore，
 * 供宿主侧（adb）把模型服务配置注入车机，绕开车机输入法的标点转换问题。
 *
 * 用法：
 * ```
 * adb shell am instrument --user 10 \
 *   -e class com.aos.agent.data.store.ConfigSeedInstrumentedTest \
 *   -e seed_base_url https://host/v1 \
 *   -e seed_model <model> \
 *   -e seed_api_key <key> \
 *   -w com.aos.agent.test/com.aos.agent.data.store.ConfigSeedInstrumentedTest
 * ```
 *
 * 写入后需重启应用进程（force-stop 再启动）让 DataStore 重新加载。
 * 只走应用自己的 [LlmConfigStore]，key 只进应用私有存储。
 */
class ConfigSeedInstrumentedTest {

    @Test
    fun seedLlmConfig() {
        val args = InstrumentationRegistry.getArguments()
        val baseUrl = requireNotNull(args.getString("seed_base_url")) { "seed_base_url is required" }
        val model = requireNotNull(args.getString("seed_model")) { "seed_model is required" }
        val apiKey = requireNotNull(args.getString("seed_api_key")) { "seed_api_key is required" }

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        runBlocking {
            LlmConfigStore(context).save(LlmConfig(baseUrl = baseUrl, model = model, apiKey = apiKey))
        }
    }
}
