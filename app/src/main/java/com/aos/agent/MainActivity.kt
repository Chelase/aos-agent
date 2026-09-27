package com.aos.agent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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

@Composable
private fun AOSAgentApp(
    systemInfoProvider: SystemInfoProvider,
    languageSwitchable: Boolean,
    onLanguageToggle: () -> Unit,
) {
    AOSAgentTheme {
        var engineerModeVisible by remember { mutableStateOf(false) }
        val systemInfo = remember { systemInfoProvider.collect() }

        if (engineerModeVisible) {
            EngineerModeScreen(
                systemInfo = systemInfo,
                onBackClick = { engineerModeVisible = false },
            )
        } else {
            HomeScreen(
                systemInfo = systemInfo,
                // 语言标签走资源而非枚举，切换后由 Activity 重建自动刷新
                currentLanguageLabel = stringResource(R.string.language_current),
                targetLanguageLabel = stringResource(R.string.language_switch_to),
                languageSwitchable = languageSwitchable,
                onEngineerModeClick = { engineerModeVisible = true },
                onLanguageToggle = onLanguageToggle,
            )
        }
    }
}
