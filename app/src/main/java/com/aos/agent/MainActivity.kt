package com.aos.agent

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
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
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aos.agent.runtime.AgentRuntime
import com.aos.agent.runtime.RuntimeStatus
import com.aos.agent.core.tools.AskUserAnswer
import com.aos.agent.ui.chat.AskUserDialog
import com.aos.agent.ui.chat.ChatEntry
import com.aos.agent.ui.chat.ChatScreen
import com.aos.agent.ui.chat.ChatTranscript
import com.aos.agent.ui.chat.PendingQuestion
import com.aos.agent.ui.chat.ToolConfirmDialog
import com.aos.agent.ui.chat.UiAskUserChannel
import com.aos.agent.ui.chat.UiToolConfirmer
import com.aos.agent.ui.settings.SettingsDraft
import com.aos.agent.ui.settings.SettingsScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import com.aos.agent.core.engine.AgentEvent
import com.aos.agent.core.llm.LlmConfig
import com.aos.agent.core.tools.mcp.McpServerConfig
import com.aos.agent.core.update.HttpUpdateChecker
import com.aos.agent.core.update.InstallGates
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
import com.aos.agent.system.DriveUxDiagnostics
import com.aos.agent.system.SystemInfoProvider
import com.aos.agent.system.capture.ScreenCapture
import com.aos.agent.system.update.ApkInstaller
import com.aos.agent.system.update.AppVersionReader
import com.aos.agent.system.voice.AndroidSpeechSynthesizer
import com.aos.agent.system.voice.AndroidSpeechTranscriber
import com.aos.agent.system.voice.AndroidVoiceFocus
import com.aos.agent.system.voice.WakeModelInstaller
import com.aos.agent.system.voice.WakeModelState
import com.aos.agent.terminal.TerminalViewModel
import com.aos.agent.ui.browser.BrowserScreen
import com.aos.agent.ui.browser.BrowserViewModel
import com.aos.agent.ui.engineer.EngineerModeScreen
import com.aos.agent.ui.components.AOSAppShell
import com.aos.agent.ui.components.AOSDestination as Destination
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.saveable.SaveableStateHolder
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
        // 浏览器会话同样挂 ViewModel：重建 Activity 时 WebView 不重复创建、页面不丢
        val browserViewModel = ViewModelProvider(this)[BrowserViewModel::class.java]

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
                browserViewModel = browserViewModel,
                voiceController = voiceController,
                voiceSettingsStore = voiceSettingsStore,
                driveRestriction = uxRestrictions.restriction,
                driveDiagnostics = uxRestrictions.diagnostics,
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
                onCapture = {
                    val result = ScreenCapture(this@MainActivity).capture()
                    val message = result.fold(
                        onSuccess = { getString(R.string.capture_saved, it.name) },
                        onFailure = { getString(R.string.capture_failed, it.message ?: "unknown") },
                    )
                    Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
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



@Composable
private fun AOSAgentApp(
    systemInfoProvider: SystemInfoProvider,
    terminalViewModel: TerminalViewModel,
    browserViewModel: BrowserViewModel,
    voiceController: VoiceController,
    voiceSettingsStore: VoiceSettingsStore,
    driveRestriction: StateFlow<DriveRestriction>,
    driveDiagnostics: StateFlow<DriveUxDiagnostics>,
    driveRestrictionPreview: Boolean,
    wakeRequested: StateFlow<Boolean>,    onWakeHandled: () -> Unit,
    languageSwitchable: Boolean,
    onLanguageToggle: () -> Unit,
    darkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onCapture: () -> Unit,
) {
    AOSAgentTheme(darkTheme = darkTheme) {
        var destination by rememberSaveable { mutableStateOf(Destination.Home) }
        // 从聊天点链接进浏览器时，返回应该回聊天而不是首页——记下来源页
        var returnDestination by rememberSaveable { mutableStateOf<Destination?>(null) }
        // 待加载的 URL：切到浏览器页后由 BrowserScreen 消费
        var pendingUrl by rememberSaveable { mutableStateOf<String?>(null) }
        val pageStateHolder = rememberSaveableStateHolder()
        val settingsDraft = remember { SettingsDraft() }
        val restriction by driveRestriction.collectAsStateWithLifecycle()
        val driveDiagnostics by driveDiagnostics.collectAsStateWithLifecycle()
        val voiceState by voiceController.state.collectAsStateWithLifecycle()
        val voiceSettings by voiceSettingsStore.settings.collectAsStateWithLifecycle()
        val wakePending by wakeRequested.collectAsStateWithLifecycle()
        // 预览开关把"未知"也当成明确状态，否则遮罩都出来了状态行还写着未知
        val driveRestricted = driveRestrictionPreview || restriction == DriveRestriction.RESTRICTED
        val driveStateKnown = driveRestrictionPreview || restriction != DriveRestriction.UNKNOWN
        LaunchedEffect(destination) { voiceController.refreshAvailability() }
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
            // 遮罩覆盖导航和页面；底页保留组合但从无障碍树隐藏。
            Box(modifier = if (driveMaskUp) Modifier.semantics { hideFromAccessibility() } else Modifier) {
                AOSAppShell(destination, driveRestricted, onNavigate = { destination = it }, onCapture = onCapture) {
                    // 宿主始终在组合中，rail 切页不会取消请求或留下已释放的语音回调。
                    ChatConsole(
                        runtime = runtime,
                        voice = voiceController,
                        visible = destination == Destination.Chat,
                        driveRestricted = driveRestricted,
                        pageStateHolder = pageStateHolder,
                        onBackClick = { destination = Destination.Home },
                        onOpenSettings = { destination = Destination.Settings },
                        onOpenLink = { url ->
                            // 栈式：记下来源页，返回时回聊天而不是首页
                            returnDestination = Destination.Chat
                            pendingUrl = url
                            destination = Destination.Browser
                        },
                    )
                    if (destination != Destination.Chat) {
                        pageStateHolder.SaveableStateProvider(destination.name) {
                            when (destination) {
                                Destination.Home -> HomeScreen(
                                    systemInfo = systemInfo,
                                    provider = systemInfoProvider,
                                    voiceState = voiceState,
                                    appVersionLabel = remember { AppVersionReader.read(context).versionName },
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
                                    wakePhrases = WakeWordPhrases.build(voiceSettings.agentName, voiceSettings.wakeVariants),
                                    driveDiagnostics = driveDiagnostics,
                                    onBackClick = { destination = Destination.Home },
                                )
                                Destination.Terminal -> TerminalScreen(
                                    viewModel = terminalViewModel,
                                    onBackClick = { destination = Destination.Home },
                                )
                                Destination.Browser -> BrowserScreen(
                                    viewModel = browserViewModel,
                                    initialUrl = pendingUrl,
                                    onUrlConsumed = { pendingUrl = null },
                                    onBackClick = {
                                        // 栈式返回：从聊天进来的回聊天，其他来源回首页
                                        val back = returnDestination ?: Destination.Home
                                        returnDestination = null
                                        destination = back
                                    },
                                )
                                Destination.SystemPanel -> SystemPanelScreen(
                                    provider = systemInfoProvider,
                                    onBackClick = { destination = Destination.Home },
                                )
                                Destination.Settings -> SettingsHost(
                                    draft = settingsDraft,
                                    runtime = runtime,
                                    voiceSettingsStore = voiceSettingsStore,
                                    speechInputAvailable = voiceController.speechInputAvailable,
                                    speechOutputAvailable = voiceController.speechOutputAvailable,
                                    darkTheme = darkTheme,
                                    onToggleTheme = onToggleTheme,
                                    onBackClick = { destination = Destination.Home },
                                )
                                Destination.Chat -> Unit
                            }
                        }
                    }
                }
            }
            if (driveMaskUp) {
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
    visible: Boolean,
    driveRestricted: Boolean,
    pageStateHolder: SaveableStateHolder,
    onBackClick: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenLink: (String) -> Unit,
) {
    val entries = remember { mutableStateListOf<ChatEntry>() }
    var status by remember { mutableStateOf(RuntimeStatus.EMPTY) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val confirmer = remember { UiToolConfirmer() }
    val pendingConfirm by confirmer.pending.collectAsStateWithLifecycle()
    val askChannel = remember { UiAskUserChannel() }
    val pendingQuestion by askChannel.pending.collectAsStateWithLifecycle()
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
            VoiceCommand.NewSession -> {
                entries.clear()
                runtime.resetSession()
            }
            VoiceCommand.OpenSettings, VoiceCommand.UseMcp -> onOpenSettings()
            VoiceCommand.CloseSettings, VoiceCommand.GoHome -> onBackClick()
            is VoiceCommand.UseSkill -> {
                val named = runtime.skillNames().firstOrNull { it.equals(command.skill, ignoreCase = true) }
                if (named != null) submit(named) else entries += ChatEntry.Note(skillMissing(command.skill))
            }
        }
    }

    LaunchedEffect(visible, driveRestricted) {
        runtime.setToolApproval(confirmer) { driveRestricted }
        runtime.setAskUserChannel(askChannel)
        if (visible) {
            runtime.refresh()
            status = runtime.status()
            voice.refreshAvailability()
        }
    }

    // 每次重组都重绑，避免回调里留住上一轮的 entries / busy
    // （问句拦截在下面的 SideEffect 里，这里只保留命令通道）
    SideEffect {
        voice.onCommand = { command -> runCommand(command) }
    }

    // 驾驶态不弹窗：播报问题 + 选项，用户说"第 N 个"或选项关键词回答。
    // 非驾驶态走下面的 AskUserDialog 弹窗；语音可用时同样播报一遍。
    // 问句播报复用 onAnswer：播完按连续模式回聆听，正好接住用户的语音回答。
    LaunchedEffect(pendingQuestion, driveRestricted) {
        val pending = pendingQuestion ?: return@LaunchedEffect
        val spoken = buildString {
            append(pending.question)
            pending.options.forEachIndexed { index, option ->
                append("第${index + 1}个，${option.label}")
                if (option.description.isNotBlank()) append("，${option.description}")
                append("；")
            }
        }
        if (driveRestricted || voiceState.usable) {
            voice.onAnswer(spoken)
        }
    }

    // 问句待回答时，语音识别到的文本先给 askChannel 做选项匹配，
    // 匹配不上才当新问题发给引擎。
    SideEffect {
        val askPending = pendingQuestion
        voice.onQuery = { text ->
            val pending = askPending
            if (pending != null && answerAsk(askChannel, pending, text)) {
                // 已作为问句回答消费
            } else {
                submit(text)
            }
        }
        voice.onCommand = { command -> runCommand(command) }
    }

    if (visible) {
        pageStateHolder.SaveableStateProvider(Destination.Chat.name) {
            ChatScreen(
                entries = entries,
                status = status,
                busy = busy,
                voiceState = voiceState,
                onBackClick = onBackClick,
                onSend = { query -> submit(query) },
                onOpenLink = onOpenLink,
                onVoiceToggle = {
                    if (voiceState.needsPermission) {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    } else {
                        voice.toggle()
                    }
                },
            )
            pendingConfirm?.let { pending ->
                ToolConfirmDialog(
                    pending = pending,
                    onAllow = { confirmer.respond(true) },
                    onDeny = { confirmer.respond(false) },
                )
            }
            // 驾驶态不弹这个框（遮罩盖住），只走语音；非驾驶态弹窗点选。
            if (!driveRestricted) {
                pendingQuestion?.let { pending ->
                    AskUserDialog(
                        pending = pending,
                        onAnswer = { askChannel.respond(it) },
                        onDismiss = { askChannel.dismiss() },
                    )
                }
            }
        }
    }
}

/**
 * 语音回答解析："第 N 个"/"N"/"选第一个"按序号；选项 label 原样出现按关键词。
 * 自由输入允许时，匹配不上就当自由文本回灌；否则返回 false 让调用方当新问题处理。
 */
private fun answerAsk(
    channel: UiAskUserChannel,
    pending: PendingQuestion,
    heard: String,
): Boolean {
    val text = heard.trim()
    if (text.isEmpty()) return false
    val index = parseOptionIndex(text, pending.options.size)
    if (index != null) {
        val option = pending.options[index]
        channel.respond(
            AskUserAnswer(
                selectedIndex = index,
                text = if (option.description.isNotBlank()) "${option.label}：${option.description}" else option.label,
            ),
        )
        return true
    }
    pending.options.forEachIndexed { i, option ->
        if (option.label.isNotBlank() && text.contains(option.label)) {
            channel.respond(
                AskUserAnswer(
                    selectedIndex = i,
                    text = if (option.description.isNotBlank()) "${option.label}：${option.description}" else option.label,
                ),
            )
            return true
        }
    }
    if (pending.allowFreeText) {
        channel.respond(AskUserAnswer(selectedIndex = null, text = text))
        return true
    }
    return false
}

private fun parseOptionIndex(text: String, optionCount: Int): Int? {
    // "第2个"/"选第一个"/"2"都认；中文数字一二三四也认。
    val digit = Regex("第?\\s*([1-4])\\s*个?").find(text)?.groupValues?.get(1)?.toIntOrNull()
        ?: (text.trim().toIntOrNull()?.takeIf { it in 1..4 })
        ?: "一二三四".indexOf(text.filter { it in "一二三四" }.takeIf { it.length == 1 } ?: "").takeIf { it >= 0 }?.plus(1)
    return digit?.minus(1)?.takeIf { it in 0 until optionCount }
}

/** 设置宿主：读写外观、模型、MCP 与语音偏好，保存后立刻刷新运行时并回到首页。 */
@Composable
private fun SettingsHost(
    draft: SettingsDraft,
    runtime: AgentRuntime,
    voiceSettingsStore: VoiceSettingsStore,
    speechInputAvailable: Boolean,
    speechOutputAvailable: Boolean,
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
    val installer = remember { ApkInstaller(context) }
    var installGate by remember {
        mutableStateOf(InstallGates.decide(installer.canRequestInstalls(), installer.installerPresent()))
    }
    var downloadPercent by remember { mutableStateOf<Int?>(null) }
    var installMessageRes by remember { mutableStateOf<Int?>(null) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        // 从"允许安装"设置页回来时重探一次，否则按钮会一直停在禁用态
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                installGate = InstallGates.decide(installer.canRequestInstalls(), installer.installerPresent())
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        current = runtime.llmConfig()
        servers = runtime.mcpServers()
    }

    // 模型下完后常驻侧那份实例并不知道，再拉一次服务让它重算，否则开关会一直"开不了"
    LaunchedEffect(wakeModelState) {
        if (wakeModelState is WakeModelState.Ready) AgentWakeWatcher.requestRefresh(context)
    }

    SettingsScreen(
        draft = draft,
        speechInputAvailable = speechInputAvailable,
        speechOutputAvailable = speechOutputAvailable,
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
        installGate = installGate,
        downloadPercent = downloadPercent,
        installMessageRes = installMessageRes,
        onCheckUpdate = {
            updateChecking = true
            updateOutcome = null
            installMessageRes = null
            scope.launch {
                updateOutcome = updateChecker.check()
                updateChecking = false
            }
        },
        onDownloadAndInstall = {
            val manifest = (updateOutcome as? UpdateOutcome.Available)?.manifest ?: return@SettingsScreen
            scope.launch {
                installMessageRes = null
                val outcome = installer.download(manifest.apkUrl, manifest.sha256) { percent ->
                    launch(Dispatchers.Main) { downloadPercent = percent }
                }
                downloadPercent = null
                installMessageRes = when (outcome.result) {
                    ApkInstaller.DownloadResult.DOWNLOADED -> {
                        val launched = outcome.file?.let { installer.launchInstall(it) } == true
                        if (launched) R.string.update_result_installed else R.string.update_gate_no_installer
                    }

                    ApkInstaller.DownloadResult.CHECKSUM_MISMATCH -> R.string.update_result_checksum_failed
                    ApkInstaller.DownloadResult.UNTRUSTED_URL -> R.string.update_result_untrusted
                    else -> R.string.update_result_network
                }
            }
        },
        onOpenInstallPermissionSettings = { installer.openInstallPermissionSettings() },
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
