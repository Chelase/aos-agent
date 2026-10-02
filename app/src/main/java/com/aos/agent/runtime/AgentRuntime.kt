package com.aos.agent.runtime

import android.content.Context
import com.aos.agent.core.engine.AgentEngine
import com.aos.agent.core.engine.AgentEvent
import com.aos.agent.core.engine.ContextManager
import com.aos.agent.core.llm.LlmConfig
import com.aos.agent.core.llm.OpenAiCompatibleProvider
import com.aos.agent.core.skills.AssetSkillLoader
import com.aos.agent.core.skills.SkillDefinition
import com.aos.agent.core.skills.SkillRegistry
import com.aos.agent.core.tools.ProcessCommandRunner
import com.aos.agent.core.tools.ShellTool
import com.aos.agent.core.tools.SystemInfoTool
import com.aos.agent.core.tools.Tool
import com.aos.agent.core.tools.ToolSystem
import com.aos.agent.core.tools.VehicleBasicTool
import com.aos.agent.core.tools.mcp.McpServerConfig
import com.aos.agent.core.tools.mcp.McpSourceStatus
import com.aos.agent.core.tools.mcp.McpToolSource
import com.aos.agent.core.tools.mcp.StreamableHttpTransport
import com.aos.agent.data.store.LlmConfigStore
import com.aos.agent.data.store.McpServerStore
import com.aos.agent.system.AndroidSystemInfoReader
import com.aos.agent.system.vehicle.AndroidVehicleReader
import com.aos.agent.system.vehicle.VehiclePropertyAllowlist
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import okhttp3.OkHttpClient

/** 运行状态快照：只给描述与掩码，绝不含 apiKey / MCP token 明文。 */
data class RuntimeStatus(
    val llmConfigured: Boolean,
    val llmDescribe: String,
    val skills: List<String>,
    val skippedSkills: List<String>,
    val localTools: List<String>,
    val vehicleReadable: List<String>,
    val vehicleSkipped: List<String>,
    val mcpSources: List<McpSourceStatus>,
    val mcpTools: List<String>,
) {
    val canSend: Boolean get() = llmConfigured
    val blockedReason: String? get() = if (llmConfigured) null else "未配置模型服务"

    companion object {
        val EMPTY = RuntimeStatus(
            llmConfigured = false,
            llmDescribe = "-",
            skills = emptyList(),
            skippedSkills = emptyList(),
            localTools = emptyList(),
            vehicleReadable = emptyList(),
            vehicleSkipped = emptyList(),
            mcpSources = emptyList(),
            mcpTools = emptyList(),
        )
    }
}

/**
 * 组装根：配置 → provider → 工具（本地 + MCP）→ skill → 引擎。
 *
 * 引擎不感知这一层的存在：`send()` 返回的事件流与单元测试、二期 AOC 用的是同一条，
 * 换调用方不需要改引擎（愿景原则五：能力外挂，内核不动）。
 */
class AgentRuntime(context: Context) {

    private val appContext = context.applicationContext
    private val llmConfigStore = LlmConfigStore(appContext)
    private val mcpServerStore = McpServerStore(appContext)

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val vehicleAllowlist: VehiclePropertyAllowlist = VehiclePropertyAllowlist.parse(
        appContext.assets.open(VEHICLE_ALLOWLIST_ASSET).bufferedReader().use { it.readText() },
    )

    private var skillRegistry: SkillRegistry = SkillRegistry.parseAll(AssetSkillLoader(appContext).load())
    private var toolSystem: ToolSystem = ToolSystem()
    private var mcpStatuses: List<McpSourceStatus> = emptyList()
    private var mcpToolNames: List<String> = emptyList()

    /** 本地工具 + 已加载的远端工具；进页面与每次发送前都可以再调一次。 */
    suspend fun refresh() {
        val localTools: List<Tool> = listOf(
            SystemInfoTool(AndroidSystemInfoReader(appContext)),
            VehicleBasicTool(vehicleAllowlist, AndroidVehicleReader(appContext)),
            // shell 只放只读诊断命令；名单外一律走确认，未注入确认器即拒。
            ShellTool(
                runner = ProcessCommandRunner(),
                autoCommands = setOf("getprop", "uptime", "date", "dumpsys"),
                askCommands = setOf("settings", "pm", "wm"),
                blockedCommands = setOf("rm", "mv", "dd", "reboot", "pm"),
            ),
        )
        val registry = SkillRegistry.parseAll(AssetSkillLoader(appContext).load())
        val system = ToolSystem()
        localTools.forEach(system::register)

        val statuses = mutableListOf<McpSourceStatus>()
        val remoteNames = mutableListOf<String>()
        mcpServerStore.servers().forEach { config ->
            val loaded = McpToolSource(CLIENT_NAME, CLIENT_VERSION) { cfg ->
                StreamableHttpTransport(httpClient, cfg.url, cfg.token)
            }.load(config)
            statuses += loaded.status
            loaded.tools.forEach(system::register)
            remoteNames += loaded.tools.map { it.name }
        }

        skillRegistry = registry
        toolSystem = system
        mcpStatuses = statuses
        mcpToolNames = remoteNames
    }

    suspend fun status(): RuntimeStatus {
        val config = llmConfigStore.current()
        return RuntimeStatus(
            llmConfigured = config?.isUsable == true,
            llmDescribe = config?.describe() ?: "未配置",
            skills = skillRegistry.skills.map { "${it.name}（${it.tools.size} 工具）" },
            skippedSkills = skillRegistry.skipped.map { "${it.source}: ${it.reason}" },
            localTools = toolSystem.all.filterNot { it.category.startsWith("mcp:") }.map { it.name }.distinct(),
            vehicleReadable = vehicleAllowlist.readable.map { it.name },
            vehicleSkipped = vehicleAllowlist.skipped.map { "${it.name}: ${it.reason}" },
            mcpSources = mcpStatuses,
            mcpTools = toolSystem.all.filter { it.category.startsWith("mcp:") }.map { it.name }.distinct(),
        )
    }

    /** 命中的 skill，供界面显示"这一轮走了哪个 skill"。 */
    fun matchedSkill(query: String): SkillDefinition? = skillRegistry.match(query)

    /** 已加载的技能名，语音指令「使用技能 X」按此校验用户点名的技能是否存在。 */
    fun skillNames(): List<String> = skillRegistry.skills.map { it.name }

    fun availableToolNames(): Set<String> = toolSystem.all.map { it.name }.toSet()

    suspend fun send(query: String): Flow<AgentEvent> {
        val config = llmConfigStore.current()
        if (config?.isUsable != true) {
            return flow { emit(AgentEvent.Failed("未配置模型服务：请先在右侧填入 baseURL 与模型名")) }
        }
        val engine = AgentEngine(
            provider = OpenAiCompatibleProvider(httpClient, config),
            context = ContextManager(BASE_SYSTEM_PROMPT + skillRegistry.capabilitySummary()),
            tools = toolSystem,
        )
        val skill = skillRegistry.match(query)
        val subset = skill?.effectiveToolNames(availableToolNames())
        return engine.run(
            query = query,
            instruction = skill?.prompt?.takeIf { it.isNotBlank() },
            toolNames = subset,
        )
    }

    // ---- 配置写入（界面用） ----

    /** 给设置页回填当前配置；apiKey 是用户自己的凭据，只回到他自己的输入框。 */
    suspend fun llmConfig(): LlmConfig? = llmConfigStore.current()

    suspend fun saveLlmConfig(baseUrl: String, model: String, apiKey: String) {
        llmConfigStore.save(
            LlmConfig(baseUrl = baseUrl.trim(), model = model.trim(), apiKey = apiKey.trim()),
        )
    }

    suspend fun saveMcpServer(id: String, url: String) {
        val current = mcpServerStore.servers()
        val replaced = current.filterNot { it.id == id } + McpServerConfig(id = id, url = url.trim())
        mcpServerStore.save(replaced)
    }

    suspend fun deleteMcpServer(id: String) {
        mcpServerStore.save(mcpServerStore.servers().filterNot { it.id == id })
    }

    suspend fun mcpServers(): List<McpServerConfig> = mcpServerStore.servers()

    private companion object {
        const val VEHICLE_ALLOWLIST_ASSET = "vehicle/vehicle_properties.json"
        const val CLIENT_NAME = "aos-agent"
        const val CLIENT_VERSION = "0.1.0"
        const val BASE_SYSTEM_PROMPT =
            "你是运行在车机上的 Agent AOSAgent。回答简短直接，给数值必须带单位；" +
                "工具返回 status 而不是数值时，如实说明读不到及原因，不要编造，也不要把缺失当成 0。当前能力：\n"
    }
}
