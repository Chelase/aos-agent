package com.aos.agent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.lazy.items
import com.aos.agent.runtime.AgentRuntime
import com.aos.agent.runtime.RuntimeStatus
import com.aos.agent.ui.chat.ChatEntry
import com.aos.agent.ui.chat.ChatScreen
import com.aos.agent.ui.chat.ChatTranscript
import kotlinx.coroutines.launch
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.aos.agent.i18n.AppLocaleController
import com.aos.agent.i18n.localeStoreFor
import com.aos.agent.system.AndroidSystemInfoReader
import com.aos.agent.system.SystemInfoProvider
import com.aos.agent.ui.engineer.EngineerModeScreen
import com.aos.agent.ui.home.HomeScreen
import com.aos.agent.ui.theme.AOSAgentTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 首启语言兜底在 AOSAgentApplication 完成，此处只负责切换入口。
        val localeController = AppLocaleController(localeStoreFor(this))
        val systemInfoProvider = SystemInfoProvider(AndroidSystemInfoReader(this))
        setContent {
            AOSAgentApp(
                systemInfoProvider = systemInfoProvider,
                languageSwitchable = localeController.canSwitch,
                onLanguageToggle = { localeController.toggle() },
            )
        }
    }
}

private enum class Destination { Home, Engineer, Chat }

@Composable
private fun AOSAgentApp(
    systemInfoProvider: SystemInfoProvider,
    languageSwitchable: Boolean,
    onLanguageToggle: () -> Unit,
) {
    AOSAgentTheme {
        var destination by remember { mutableStateOf(Destination.Home) }
        val systemInfo = remember { systemInfoProvider.collect() }
        val context = LocalContext.current
        val runtime = remember { AgentRuntime(context) }

        when (destination) {
            Destination.Home -> HomeScreen(
                systemInfo = systemInfo,
                // 语言标签走资源而非枚举，切换后由 Activity 重建自动刷新
                currentLanguageLabel = stringResource(R.string.language_current),
                targetLanguageLabel = stringResource(R.string.language_switch_to),
                languageSwitchable = languageSwitchable,
                onEngineerModeClick = { destination = Destination.Engineer },
                onChatClick = { destination = Destination.Chat },
                onLanguageToggle = onLanguageToggle,
            )

            Destination.Engineer -> EngineerModeScreen(
                systemInfo = systemInfo,
                onBackClick = { destination = Destination.Home },
            )

            Destination.Chat -> ChatConsole(
                runtime = runtime,
                onBackClick = { destination = Destination.Home },
            )
        }
    }
}

/** 控制台宿主：持有对话状态并把引擎事件折叠进去；引擎本身不知道界面存在。 */
@Composable
private fun ChatConsole(
    runtime: AgentRuntime,
    onBackClick: () -> Unit,
) {
    val entries = remember { mutableStateListOf<ChatEntry>() }
    var status by remember { mutableStateOf(RuntimeStatus.EMPTY) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        runtime.refresh()
        status = runtime.status()
    }

    ChatScreen(
        entries = entries,
        status = status,
        busy = busy,
        onBackClick = onBackClick,
        onSend = { query ->
            scope.launch {
                busy = true
                ChatTranscript.startTurn(entries, query, runtime.matchedSkill(query)?.name)
                try {
                    runtime.send(query).collect { event -> ChatTranscript.apply(entries, event) }
                } finally {
                    busy = false
                    status = runtime.status()
                }
            }
        },
        onSaveModelConfig = { baseUrl, model, apiKey ->
            scope.launch {
                runtime.saveLlmConfig(baseUrl, model, apiKey)
                status = runtime.status()
            }
        },
        onSaveMcpSource = { name, url ->
            scope.launch {
                runtime.saveMcpServer(name, url)
                runtime.refresh()
                status = runtime.status()
            }
        },
    )
}
