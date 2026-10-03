package com.aos.agent

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.lazy.items
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aos.agent.runtime.AgentRuntime
import com.aos.agent.runtime.RuntimeStatus
import com.aos.agent.ui.chat.ChatEntry
import com.aos.agent.ui.chat.ChatScreen
import com.aos.agent.ui.chat.ChatTranscript
import com.aos.agent.ui.settings.SettingsScreen
import kotlinx.coroutines.launch
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import com.aos.agent.core.engine.AgentEvent
import com.aos.agent.core.llm.LlmConfig
import com.aos.agent.core.tools.mcp.McpServerConfig
import com.aos.agent.core.update.HttpUpdateChecker
import com.aos.agent.core.update.UpdateOutcome
import com.aos.agent.core.voice.VoiceCommand
import com.aos.agent.core.voice.WakeWordGate
import com.aos.agent.core.voice.WakeWordPhrases
import com.aos.agent.data.store.ThemeStore
import com.aos.agent.data.store.VoiceSettingsStore
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.aos.agent.i18n.AppLocaleController
import com.aos.agent.i18n.localeStoreFor
import com.aos.agent.service.AgentWakeWatcher
import com.aos.agent.system.AndroidSystemInfoReader
import com.aos.agent.system.CarUxRestrictionsReader
import com.aos.agent.system.DriveRestriction
import com.aos.agent.system.SystemInfoProvider
import com.aos.agent.system.update.AppVersionReader
import com.aos.agent.system.voice.AndroidSpeechSynthesizer
import com.aos.agent.system.voice.AndroidSpeechTranscriber
import com.aos.agent.system.voice.AndroidVoiceFocus
import com.aos.agent.system.voice.WakeModelInstaller
import com.aos.agent.system.voice.WakeModelState
import com.aos.agent.terminal.TerminalViewModel
import com.aos.agent.ui.engineer.EngineerModeScreen
import com.aos.agent.ui.home.HomeScreen
import com.aos.agent.ui.systempanel.SystemPanelScreen
import com.aos.agent.ui.terminal.TerminalScreen
import com.aos.agent.ui.theme.AOSAgentTheme
import com.aos.agent.ui.voice.DriveVoiceMask
import com.aos.agent.ui.voice.VoiceController
import com.aos.agent.ui.voice.VoicePhase
import com.aos.agent.ui.voice.voiceVocabularyFrom
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class MainActivity : ComponentActivity() {

    private lateinit var voiceController: VoiceController
    private lateinit var voiceSettingsStore: VoiceSettingsStore
    private val uxRestrictions by lazy { CarUxRestrictionsReader(this) }

    /**
     * 行驶受限预览开关（仅测试用）：`adb shell am start -n com.aos.agent/.MainActivity --ez <extra> true`。
     *
     * 存在的理由：真值只能由 VHAL 写速度/分心优化位翻起来，而 user 版镜像把
     * `cmd car_service` 全量屏蔽（"requires non-user build"）且 adbd 不能 root，
     * 于是这套遮罩在交付前没有任何可复现的走查手段。它只影响界面裁剪，不给任何权限或数据。
     */
    private val driveRestrictionPreview by lazy {
        intent.getBooleanExtra(EXTRA_PREVIEW_DRIVE_RESTRICTED, false)
    }

    /** 唤醒请求：Intent 拉起（Activity 不在栈上）与进程内事件（Activity 活着）两条路都汇到这里。 */
    private val wakeRequested = MutableStateFlow(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        // 主题在 setContentView 前定死：先选对窗口背景样式，避免启动闪屏错色（design.md §9.3）。
        val themeStore = ThemeStore(this)
        val darkTheme = themeStore.isDarkBlocking()
        setTheme(if (darkTheme) R.style.Theme_AOSAgent_Dark else R.style.Theme_AOSAgent)
        super.onCreate(savedInstanceState)

        // 首启语言兜底在 AOSAgentApplication 完成，此处只负责切换入口。
        val localeController = AppLocaleController(localeStoreFor(this))
        val systemInfoProvider = SystemInfoProvider(AndroidSystemInfoReader(this))
        // 终端会话挂 ViewModel：语言/主题切换重建 Activity 后 shell 不掉
        val terminalViewModel = ViewModelProvider(this)[TerminalViewModel::class.java]

        voiceSettingsStore = VoiceSettingsStore(this)
        // 识别器与 TTS 都绑定主线程与 Activity 生命周期，随 Activity 创建/释放
        voiceController = VoiceController(
            transcriber = AndroidSpeechTranscriber(this),
            synthesizer = AndroidSpeechSynthesizer(this),
            focus = AndroidVoiceFocus(this),
            vocabulary = voiceVocabularyFrom(this),
            settings = voiceSettingsStore.settings,
            scope = lifecycleScope,
            hasPermission = {
                ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
                    PackageManager.PERMISSION_GRANTED
            },
        )
        uxRestrictions.start()
        takeWakeFromIntent(intent)

        setContent {
            AOSAgentApp(
                systemInfoProvider = systemInfoProvider,
                terminalViewModel = terminalViewModel,
                voiceController = voiceController,
                voiceSettingsStore = voiceSettingsStore,
                driveRestriction = uxRestrictions.restriction,
                driveRestrictionPreview = driveRestrictionPreview,
                wakeRequested = wakeRequested,
                onWakeHandled = { wakeRequested.value = false },
                languageSwitchable = localeController.canSwitch,
                onLanguageToggle = { localeController.toggle() },
                darkTheme = darkTheme,
                onToggleTheme = {
                    lifecycleScope.launch {
                        themeStore.saveDark(!darkTheme)
                        recreate()
                    }
                },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        takeWakeFromIntent(intent)
    }

    private fun takeWakeFromIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_VOICE_WAKE, false) == true) wakeRequested.value = true
    }

    override fun onDestroy() {
        voiceController.release()
        uxRestrictions.close()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_PREVIEW_DRIVE_RESTRICTED = "com.aos.agent.extra.PREVIEW_DRIVE_RESTRICTED"
        const val EXTRA_VOICE_WAKE = "com.aos.agent.extra.VOICE_WAKE"
    }
}

private enum class Destination { Home, Engineer, Chat, Settings, Terminal, SystemPanel }

@Composable
private fun AOSAgentApp(
    systemInfoProvider: SystemInfoProvider,
    terminalViewModel: TerminalViewModel,
    voiceController: VoiceController,
    voiceSettingsStore: VoiceSettingsStore,
    driveRestriction: StateFlow<DriveRestriction>,
    driveRestrictionPreview: Boolean,
    wakeRequested: StateFlow<Boolean>,
    onWakeHandled: () -> Unit,
    languageSwitchable: Boolean,
    onLanguageToggle: () -> Unit,
    darkTheme: Boolean,
    onToggleTheme: () -> Unit,
) {
    AOSAgentTheme(darkTheme = darkTheme) {
        var destination by remember { mutableStateOf(Destination.Home) }
        val restriction by driveRestriction.collectAsStateWithLifecycle()
        val voiceState by voiceController.state.collectAsStateWithLifecycle()
        val voiceSettings by voiceSettingsStore.settings.collectAsStateWithLifecycle()
        val wakePending by wakeRequested.collectAsStateWithLifecycle()
        // 预览开关把"未知"也当成明确状态，否则遮罩都出来了状态行还写着未知
        val driveRestricted = driveRestrictionPreview || restriction == DriveRestriction.RESTRICTED
        val driveStateKnown = driveRestrictionPreview || restriction != DriveRestriction.UNKNOWN
        val systemInfo = remember { systemInfoProvider.collect() }
        val context = LocalContext.current
        val runtime = remember { AgentRuntime(context) }

        fun openChatByVoice() {
            destination = Destination.Chat
            voiceController.startFromWake()
        }

        // 界面正在用麦克风时让常驻唤醒让路：同进程两路 AudioRecord 抢一只麦，
        // 抢不过的那路只会报"麦克风被占用"
        LaunchedEffect(voiceState.phase) {
            WakeWordGate.paused.value = voiceState.phase != VoicePhase.IDLE
        }
        LaunchedEffect(Unit) {
            WakeWordGate.wakes.collect { openChatByVoice() }
        }
        LaunchedEffect(wakePending) {
            if (wakePending) {
                onWakeHandled()
                openChatByVoice()
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            val driveMaskUp = driveRestricted && destination != Destination.Home
            // 被遮罩盖住的页面继续组合（对话条目、终端会话不能因为上路就丢），
            // 但从无障碍树里摘掉——否则 TalkBack 还能滑进藏起来的输入框
            Box(
                modifier = if (driveMaskUp) {
                    Modifier.semantics { invisibleToUser() }
                } else {
                    Modifier
                },
            ) {
                when (destination) {
                    Destination.Home -> HomeScreen(
                        systemInfo = systemInfo,
                        // 语言标签走资源而非枚举，切换后由 Activity 重建自动刷新
                        currentLanguageLabel = stringResource(R.string.language_current),
                        targetLanguageLabel = stringResource(R.string.language_switch_to),
                        languageSwitchable = languageSwitchable,
                        driveRestricted = driveRestricted,
                        driveStateKnown = driveStateKnown,
                        onEngineerModeClick = { destination = Destination.Engineer },
                        onChatClick = { destination = Destination.Chat },
                        onTerminalClick = { destination = Destination.Terminal },
                        onSystemPanelClick = { destination = Destination.SystemPanel },
                        onSettingsClick = { destination = Destination.Settings },
                        onLanguageToggle = onLanguageToggle,
                    )

                    Destination.Engineer -> EngineerModeScreen(
                        systemInfo = systemInfo,
                        wakePhrases = WakeWordPhrases.build(
                            voiceSettings.agentName,
                            voiceSettings.wakeVariants,
                        ),
                        onBackClick = { destination = Destination.Home },
                    )

                    Destination.Terminal -> TerminalScreen(
                        viewModel = terminalViewModel,
                        onBackClick = { destination = Destination.Home },
                    )

                    Destination.SystemPanel -> SystemPanelScreen(
                        provider = systemInfoProvider,
                        onBackClick = { destination = Destination.Home },
                    )

                    Destination.Chat -> ChatConsole(
                        runtime = runtime,
                        voice = voiceController,
                        onBackClick = { destination = Destination.Home },
                        onOpenSettings = { destination = Destination.Settings },
                    )

                    Destination.Settings -> SettingsHost(
                        runtime = runtime,
                        voiceSettingsStore = voiceSettingsStore,
                        darkTheme = darkTheme,
                        onToggleTheme = onToggleTheme,
                        onBackClick = { destination = Destination.Home },
                    )
                }
            }

            // 行驶受限：离开首页就只剩语音。首页不糊——首页本来没有文字输入，
            // 把用户最后一块能看的地方也盖掉只会让人以为应用坏了。
            if (driveMaskUp) {
                // 盖住的页面不会再刷新能力（授权对话框可能刚回来），遮罩自己确认一次
                LaunchedEffect(Unit) { voiceController.refreshAvailability() }
                DriveVoiceMask(
                    state = voiceState,
                    onMicClick = { voiceController.toggle() },
                    onBackHome = { destination = Destination.Home },
                )
            }
        }
    }
}

/**
 * 控制台宿主：持有对话状态并把引擎事件折叠进去；引擎本身不知道界面存在。
 *
 * 语音与键盘共用同一条发送通路：识别出的普通提问走 [submit]，命中本地指令的
 * 就地执行（清对话、跳页面），不进模型。
 */
@Composable
private fun ChatConsole(
    runtime: AgentRuntime,
    voice: VoiceController,
    onBackClick: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val entries = remember { mutableStateListOf<ChatEntry>() }
    var status by remember { mutableStateOf(RuntimeStatus.EMPTY) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val voiceState by voice.state.collectAsStateWithLifecycle()
    val noPermission = stringResource(R.string.voice_err_permission)
    val skillMissing = { name: String -> context.getString(R.string.voice_skill_missing, name) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        voice.refreshAvailability()
        if (!granted) entries += ChatEntry.Note(noPermission)
    }

    fun submit(query: String) {
        if (busy) return
        scope.launch {
            busy = true
            voice.onTurnStarted()
            ChatTranscript.startTurn(entries, query, runtime.matchedSkill(query)?.name)
            try {
                runtime.send(query).collect { event ->
                    ChatTranscript.apply(entries, event)
                    when (event) {
                        is AgentEvent.Completed -> voice.onAnswer(event.text)
                        is AgentEvent.Failed -> voice.onTurnFailed()
                        else -> Unit
                    }
                }
            } catch (ignored: Exception) {
                voice.onTurnFailed()
            } finally {
                busy = false
                status = runtime.status()
            }
        }
    }

    fun runCommand(command: VoiceCommand) {
        when (command) {
            VoiceCommand.NewSession -> entries.clear()
            VoiceCommand.OpenSettings, VoiceCommand.UseMcp -> onOpenSettings()
            VoiceCommand.CloseSettings, VoiceCommand.GoHome -> onBackClick()
            is VoiceCommand.UseSkill -> {
                val named = runtime.skillNames().firstOrNull { it.equals(command.skill, ignoreCase = true) }
                if (named != null) submit(named) else entries += ChatEntry.Note(skillMissing(command.skill))
            }
        }
    }

    LaunchedEffect(Unit) {
        runtime.refresh()
        status = runtime.status()
        voice.refreshAvailability()
    }

    // 每次重组都重绑，避免回调里留住上一轮的 entries / busy
    SideEffect {
        voice.onQuery = { text -> submit(text) }
        voice.onCommand = { command -> runCommand(command) }
    }

    ChatScreen(
        entries = entries,
        status = status,
        busy = busy,
        voiceState = voiceState,
        onBackClick = onBackClick,
        onSend = { query -> submit(query) },
        onVoiceToggle = {
            if (voiceState.needsPermission) {
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            } else {
                voice.toggle()
            }
        },
    )
}

/** 设置宿主：读写外观、模型、MCP 与语音偏好，保存后立刻刷新运行时并回到首页。 */
@Composable
private fun SettingsHost(
    runtime: AgentRuntime,
    voiceSettingsStore: VoiceSettingsStore,
    darkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onBackClick: () -> Unit,
) {
    var current by remember { mutableStateOf<com.aos.agent.core.llm.LlmConfig?>(null) }
    var servers by remember { mutableStateOf<List<com.aos.agent.core.tools.mcp.McpServerConfig>>(emptyList()) }
    val voiceSettings by voiceSettingsStore.settings.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val wakeInstaller = remember { WakeModelInstaller(context, scope) }
    val wakeModelState by wakeInstaller.state.collectAsStateWithLifecycle()
    val appVersion = remember { AppVersionReader.read(context) }
    val updateChecker = remember {
        HttpUpdateChecker(localVersionCode = { appVersion.versionCode })
    }
    var updateChecking by remember { mutableStateOf(false) }
    var updateOutcome by remember { mutableStateOf<UpdateOutcome?>(null) }

    LaunchedEffect(Unit) {
        current = runtime.llmConfig()
        servers = runtime.mcpServers()
    }

    // 模型下完后常驻侧那份实例并不知道，再拉一次服务让它重算，否则开关会一直"开不了"
    LaunchedEffect(wakeModelState) {
        if (wakeModelState is WakeModelState.Ready) AgentWakeWatcher.requestRefresh(context)
    }

    SettingsScreen(
        currentLlm = current?.let { Triple(it.baseUrl, it.model, it.apiKey) },
        mcpServers = servers,
        darkTheme = darkTheme,
        voiceSettings = voiceSettings,
        wakeModelState = wakeModelState,
        onToggleTheme = onToggleTheme,
        onToggleTts = { enabled -> scope.launch { voiceSettingsStore.setTtsEnabled(enabled) } },
        onToggleContinuous = { enabled -> scope.launch { voiceSettingsStore.setContinuous(enabled) } },
        onToggleBargeIn = { enabled -> scope.launch { voiceSettingsStore.setBargeInEnabled(enabled) } },
        onToggleWake = { enabled ->
            scope.launch {
                voiceSettingsStore.setWakeWordEnabled(enabled)
                // 开关变化要让常驻侧立刻重算"该不该听"，否则要等到下次进前台才生效
                AgentWakeWatcher.requestRefresh(context)
            }
        },
        onSaveAgentName = { name -> scope.launch { voiceSettingsStore.setAgentName(name) } },
        onToggleWakeVariant = { variant, checked ->
            scope.launch {
                val current = voiceSettingsStore.settings.value.wakeVariants
                voiceSettingsStore.setWakeVariants(if (checked) current + variant else current - variant)
            }
        },
        onDownloadWakeModel = { wakeInstaller.download() },
        appVersionLabel = appVersion.versionName + " (" + appVersion.versionCode + ")",
        updateChecking = updateChecking,
        updateOutcome = updateOutcome,
        onCheckUpdate = {
            updateChecking = true
            updateOutcome = null
            scope.launch {
                updateOutcome = updateChecker.check()
                updateChecking = false
            }
        },
        onBackClick = onBackClick,
        onSaveModel = { baseUrl, model, apiKey ->
            scope.launch {
                runtime.saveLlmConfig(baseUrl, model, apiKey)
                current = runtime.llmConfig()
            }
        },
        onSaveMcp = { name, url ->
            scope.launch {
                runtime.saveMcpServer(name, url)
                runtime.refresh()
                servers = runtime.mcpServers()
            }
        },
        onDeleteMcp = { name ->
            scope.launch {
                runtime.deleteMcpServer(name)
                runtime.refresh()
                servers = runtime.mcpServers()
            }
        },
    )
}
