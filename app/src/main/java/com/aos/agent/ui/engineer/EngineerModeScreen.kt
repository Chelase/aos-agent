package com.aos.agent.ui.engineer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aos.agent.system.SystemInfo

@Composable
fun EngineerModeScreen(
    systemInfo: SystemInfo,
    onBackClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "工程师模式",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(text = "Android 版本: ${systemInfo.androidVersion}")
        Text(text = "API Level: ${systemInfo.sdkInt}")
        Text(text = "厂商: ${systemInfo.manufacturer}")
        Text(text = "品牌: ${systemInfo.brand}")
        Text(text = "型号: ${systemInfo.model}")
        Text(text = "设备名: ${systemInfo.device}")
        Text(text = "Automotive: ${systemInfo.isAutomotive}")
        Button(onClick = onBackClick) {
            Text(text = "返回")
        }
    }
}
