package com.aos.agent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.aos.agent.system.AndroidSystemInfoReader
import com.aos.agent.system.SystemInfoProvider
import com.aos.agent.ui.engineer.EngineerModeScreen
import com.aos.agent.ui.home.HomeScreen
import com.aos.agent.ui.theme.AOSAgentTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val systemInfoProvider = SystemInfoProvider(AndroidSystemInfoReader(this))
        setContent {
            AOSAgentApp(systemInfoProvider = systemInfoProvider)
        }
    }
}

@Composable
private fun AOSAgentApp(
    systemInfoProvider: SystemInfoProvider,
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
                onEngineerModeClick = { engineerModeVisible = true },
            )
        }
    }
}
