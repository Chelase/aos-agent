package com.aos.agent.ui.systempanel

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.aos.agent.R
import com.aos.agent.system.SystemInfo
import com.aos.agent.system.SystemInfoProvider
import com.aos.agent.ui.components.AOSCard
import com.aos.agent.ui.components.AOSDataRow
import com.aos.agent.ui.components.AOSLabels
import com.aos.agent.ui.components.AOSMetric
import com.aos.agent.ui.components.AOSRowDivider
import com.aos.agent.ui.components.AOSSectionHeader
import com.aos.agent.ui.components.AOSSecondaryButton
import com.aos.agent.ui.theme.AOSSizing
import com.aos.agent.ui.theme.AOSSpacing
import com.aos.agent.ui.theme.AOSTheme
import com.aos.agent.ui.theme.aosLegendStyle
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 系统感知面板（Batch 1 Step 2，design.md 卡片式仪表布局）：
 * 系统/设备/网络/电源四分区，页面存续期间每 5 秒重采一次，可手动刷新。
 */
@Composable
fun SystemPanelScreen(
    provider: SystemInfoProvider,
    onBackClick: () -> Unit,
) {
    var info by remember { mutableStateOf(provider.collect()) }
    var updatedAt by remember { mutableStateOf(formatClock()) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(REFRESH_INTERVAL_MS)
            info = provider.collect()
            updatedAt = formatClock()
        }
    }

    val refresh: () -> Unit = {
        info = provider.collect()
        updatedAt = formatClock()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(AOSSizing.safeZone),
    ) {
        TopBar(
            updatedAt = updatedAt,
            onBackClick = onBackClick,
            onRefreshClick = refresh,
        )
        Spacer(modifier = Modifier.height(AOSSpacing.lg))
        MetricStrip(info = info, updatedAt = updatedAt)
        Spacer(modifier = Modifier.height(AOSSpacing.lg))
        InfoCards(
            info = info,
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        )
    }
}

/** 手动刷新/自动刷新共用的采集时刻格式。 */
private fun formatClock(): String =
    SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())

private const val REFRESH_INTERVAL_MS = 5_000L

@Composable
private fun TopBar(
    updatedAt: String,
    onBackClick: () -> Unit,
    onRefreshClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(AOSSizing.topBarHeight),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = stringResource(R.string.panel_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = stringResource(R.string.panel_subtitle),
                style = aosLegendStyle,
                color = AOSTheme.textTertiary,
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AOSSpacing.md),
        ) {
            Text(
                text = stringResource(R.string.panel_updated_at, updatedAt),
                style = aosLegendStyle,
                color = AOSTheme.textTertiary,
            )
            AOSSecondaryButton(
                text = stringResource(R.string.panel_action_refresh),
                onClick = onRefreshClick,
            )
            AOSSecondaryButton(
                text = stringResource(R.string.action_back),
                onClick = onBackClick,
            )
        }
    }
}

@Composable
private fun MetricStrip(info: SystemInfo, updatedAt: String) {
    AOSCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            AOSMetric(
                value = info.battery.levelPercent?.let { "$it%" }
                    ?: stringResource(R.string.value_unavailable),
                label = stringResource(R.string.label_battery_level),
            )
            AOSMetric(
                value = AOSLabels.networkLabel(info.networkTransport),
                label = stringResource(R.string.label_network),
            )
            AOSMetric(
                value = if (info.isAutomotive) {
                    stringResource(R.string.value_yes)
                } else {
                    stringResource(R.string.value_no)
                },
                label = stringResource(R.string.label_automotive),
            )
        }
    }
}

@Composable
private fun InfoCards(info: SystemInfo, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(AOSSpacing.md)) {
        Row(horizontalArrangement = Arrangement.spacedBy(AOSSpacing.md)) {
            SystemCard(info = info, modifier = Modifier.weight(1f))
            DeviceCard(info = info, modifier = Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(AOSSpacing.md)) {
            NetworkCard(info = info, modifier = Modifier.weight(1f))
            PowerCard(info = info, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun SystemCard(info: SystemInfo, modifier: Modifier = Modifier) {
    AOSCard(modifier = modifier) {
        AOSSectionHeader(title = stringResource(R.string.section_system))
        Spacer(modifier = Modifier.height(AOSSpacing.sm))
        AOSDataRow(
            label = stringResource(R.string.label_android_version),
            value = info.androidVersion,
        )
        AOSRowDivider()
        AOSDataRow(
            label = stringResource(R.string.label_api_level),
            value = info.sdkInt.toString(),
        )
        AOSRowDivider()
        AOSDataRow(
            label = stringResource(R.string.label_automotive),
            value = if (info.isAutomotive) {
                stringResource(R.string.value_yes)
            } else {
                stringResource(R.string.value_no)
            },
        )
    }
}

@Composable
private fun DeviceCard(info: SystemInfo, modifier: Modifier = Modifier) {
    AOSCard(modifier = modifier) {
        AOSSectionHeader(title = stringResource(R.string.section_device))
        Spacer(modifier = Modifier.height(AOSSpacing.sm))
        AOSDataRow(
            label = stringResource(R.string.label_manufacturer),
            value = info.manufacturer,
        )
        AOSRowDivider()
        AOSDataRow(
            label = stringResource(R.string.label_brand),
            value = info.brand,
        )
        AOSRowDivider()
        AOSDataRow(
            label = stringResource(R.string.label_model),
            value = info.model,
        )
        AOSRowDivider()
        AOSDataRow(
            label = stringResource(R.string.label_device),
            value = info.device,
        )
    }
}

@Composable
private fun NetworkCard(info: SystemInfo, modifier: Modifier = Modifier) {
    AOSCard(modifier = modifier) {
        AOSSectionHeader(title = stringResource(R.string.section_network))
        Spacer(modifier = Modifier.height(AOSSpacing.sm))
        AOSDataRow(
            label = stringResource(R.string.label_network),
            value = AOSLabels.networkLabel(info.networkTransport),
        )
    }
}

@Composable
private fun PowerCard(info: SystemInfo, modifier: Modifier = Modifier) {
    AOSCard(modifier = modifier) {
        AOSSectionHeader(title = stringResource(R.string.section_power))
        Spacer(modifier = Modifier.height(AOSSpacing.sm))
        AOSDataRow(
            label = stringResource(R.string.label_battery_level),
            value = info.battery.levelPercent?.let { "$it%" }
                ?: stringResource(R.string.value_unavailable),
        )
        AOSRowDivider()
        AOSDataRow(
            label = stringResource(R.string.label_battery_state),
            value = AOSLabels.batteryStateLabel(info.battery),
        )
    }
}
