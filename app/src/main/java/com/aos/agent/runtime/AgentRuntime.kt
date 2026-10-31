package com.aos.agent.runtime

import android.content.Context
import com.aos.agent.core.engine.AgentEngine
import com.aos.agent.core.engine.AgentEvent
import com.aos.agent.core.engine.ContextManager
import com.aos.agent.core.engine.PromptAssembler
import com.aos.agent.core.engine.PromptSections
import com.aos.agent.core.llm.LlmConfig
import com.aos.agent.core.llm.LlmMessage
import com.aos.agent.core.llm.OpenAiCompatibleProvider
import com.aos.agent.core.session.SessionLog
import com.aos.agent.core.skills.AssetSkillLoader
import com.aos.agent.core.skills.SkillDefinition
import com.aos.agent.core.skills.SkillRegistry
import com.aos.agent.core.tools.AskUserChannel
import com.aos.agent.core.tools.AskUserTool
import com.aos.agent.core.tools.FileReadTool
import com.aos.agent.core.tools.ProcessCommandRunner
import com.aos.agent.core.tools.ShellTool
import com.aos.agent.core.tools.SystemInfoTool
import com.aos.agent.core.tools.Tool
import com.aos.agent.core.tools.ToolSystem
import com.aos.agent.core.tools.VehicleBasicTool
import com.aos.agent.core.tools.WebSearchTool
import com.aos.agent.core.tools.mcp.McpServerConfig
import com.aos.agent.core.tools.mcp.McpSourceStatus
import com.aos.agent.core.tools.mcp.McpToolSource
import com.aos.agent.core.tools.mcp.StreamableHttpTransport
import com.aos.agent.data.store.LlmConfigStore
import com.aos.agent.data.store.LlmProfileStore
import com.aos.agent.data.store.McpServerStore
import com.aos.agent.system.llm.LlmStateMirror
import com.aos.agent.system.AndroidSystemInfoReader
import com.aos.agent.system.vehicle.AndroidVehicleReader
import com.aos.agent.system.vehicle.VehiclePropertyAllowlist
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
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

    private val vehicleAllowlist: VehiclePropertyAllowlist = VehiclePropertyAllowlist.parseAsset(appContext)

    private var skillRegistry: SkillRegistry = SkillRegistry.parseAll(AssetSkillLoader(appContext).load())
    private var toolSystem: ToolSystem = ToolSystem()
    private var toolConfirmer: com.aos.agent.core.tools.ToolConfirmer? = null
    private var askUserChannel: AskUserChannel? = null
    private var driveRestricted: () -> Boolean = { false }

    val sessionDir: File get() = File(appContext.filesDir, "sessions")
    private var mcpStatuses: List<McpSourceStatus> = emptyList()
    private var mcpToolNames: List<String> = emptyList()
    private val promptAssembler = PromptAssembler(PromptSections.defaults())
    private var sessionContext: ContextManager? = null

    fun setToolApproval(
        confirmer: com.aos.agent.core.tools.ToolConfirmer?,
        isDriveRestricted: () -> Boolean,
    ) {
        toolConfirmer = confirmer
        driveRestricted = isDriveRestricted
    }

    /** 结构化提问通道：弹窗（非驾驶）或语音（驾驶），由界面层注入。 */
    fun setAskUserChannel(channel: AskUserChannel?) {
        askUserChannel = channel
    }

    /** 本地工具 + 已加载的远端工具；进页面与每次发送前都可以再调一次。 */
    suspend fun refresh() {
        val toolOutputDir = File(appContext.filesDir, "tool-output")
        val localTools: List<Tool> = listOf(
            SystemInfoTool(AndroidSystemInfoReader(appContext)),
            VehicleBasicTool(vehicleAllowlist, AndroidVehicleReader(appContext)),
            // shell 只放免确认的只读诊断命令；名单外一律走确认，未注入确认器即拒。
            // pm/cmd/am 等调度器不黑名单：查隐藏模块、启用隐藏功能要靠它们，
            // 车辆硬件动作另有 VehicleHardware 硬禁令兜底。
            ShellTool(
                runner = ProcessCommandRunner(),
                autoCommands = setOf("getprop", "uptime", "date", "dumpsys"),
                askCommands = setOf("settings", "wm"),
                blockedCommands = setOf("rm", "mv", "dd", "reboot"),
                outputDir = toolOutputDir,
            ),
            // 只读分页：读 shell 落盘全文，不重新执行命令，给 shell 减负。
            FileReadTool(listOf(toolOutputDir)),
            // 结构化提问：模型拿不准时问用户，不是危险操作，不经过 Ask 闸。
            AskUserTool(askUserChannel),
            // 联网搜索：只读公开信息，不登录不写回，免确认。
            WebSearchTool(),
        )
        val registry = SkillRegistry.parseAll(AssetSkillLoader(appContext).load())
        val system = ToolSystem(confirmer = toolConfirmer) { driveRestricted() }
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

    /** 新会话：清掉跨轮历史与工具轨迹，旧日志保留作排查证据。 */
    fun resetSession() {
        sessionContext?.reset()
        sessionContext = null
        SessionLog.rotate(sessionDir)
    }

    suspend fun send(query: String): Flow<AgentEvent> {
        val config = llmConfigStore.current()
        if (config?.isUsable != true) {
            return flow { emit(AgentEvent.Failed("未配置模型服务：请先在右侧填入 baseURL 与模型名")) }
        }
        val context = sessionContext ?: ContextManager(
            systemPromptProvider = {
                promptAssembler.assemble(
                    RuntimeEnvironment.snapshot(
                        context = appContext,
                        tools = toolSystem.all,
                        skillSummary = skillRegistry.capabilitySummary(),
                    ),
                )
            },
        ).also {
            // 进程被杀后只恢复最近一轮：知道上一轮发生过什么，不把所有历史搬回来。
            it.restore(SessionLog.readRecent(sessionDir, lastTurns = 1))
            sessionContext = it
        }
        val engine = AgentEngine(
            provider = OpenAiCompatibleProvider(httpClient, config),
            context = context,
            tools = toolSystem,
        )
        val skill = skillRegistry.match(query)
        val subset = skill?.effectiveToolNames(availableToolNames())
        val upstream = engine.run(
            query = query,
            instruction = skill?.prompt?.takeIf { it.isNotBlank() },
            toolNames = subset,
        )
        // 只存正常完成的轮次：Completed 自带本轮工具交换序列，直接落盘。
        return flow {
            upstream.collect { event ->
                if (event is AgentEvent.Completed) {
                    persistTurn(query, event.toolExchange, event.text)
                }
                emit(event)
            }
        }
    }

    private fun persistTurn(query: String, exchange: List<LlmMessage>, answer: String) {
        // IO 上后台线程做，不堵模型事件流；失败只记日志，不让落盘拖垮对话。
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                SessionLog.append(
                    sessionDir,
                    SessionLog.TurnRecord(
                        timestamp = System.currentTimeMillis(),
                        user = query,
                        toolExchange = exchange,
                        assistant = answer,
                    ),
                )
            }
        }
    }

    // ---- 配置写入（界面用） ----

    /** 给设置页回填当前配置；apiKey 是用户自己的凭据，只回到他自己的输入框。 */
    suspend fun llmConfig(): LlmConfig? = llmConfigStore.current()

    suspend fun saveLlmConfig(baseUrl: String, model: String, apiKey: String) {
        llmConfigStore.save(
            LlmConfig(baseUrl = baseUrl.trim(), model = model.trim(), apiKey = apiKey.trim()),
        )
        // 界面手改后「当前配置来自哪个档案」的提示失效；档案列表保留
        LlmProfileStore(appContext).saveActiveProfile(null)
        LlmStateMirror(appContext).refresh()
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
        const val CLIENT_NAME = "aos-agent"
        const val CLIENT_VERSION = "0.1.0"
    }
}
