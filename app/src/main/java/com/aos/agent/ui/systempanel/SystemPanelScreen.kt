package com.aos.agent.ui.systempanel

import androidx.compose.foundation.layout.FlowRow
import com.aos.agent.ui.state.rememberSystemSnapshot
import com.aos.agent.ui.components.AOSMetricCard
import com.aos.agent.ui.components.AOSPageHeader
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.aos.agent.R
import com.aos.agent.core.tools.DegradeStatus
import com.aos.agent.system.SystemInfo
import com.aos.agent.system.SystemInfoProvider
import com.aos.agent.ui.components.AOSCard
import com.aos.agent.ui.components.AOSDataRow
import com.aos.agent.ui.components.AOSLabels
import com.aos.agent.ui.components.AOSRowDivider
import com.aos.agent.ui.components.AOSSectionHeader
import com.aos.agent.ui.components.AOSSecondaryButton
import com.aos.agent.ui.theme.AOSSizing
import com.aos.agent.ui.theme.AOSSpacing

/**
 * 系统感知面板（Batch 1 Step 2，design.md 卡片式仪表布局）：
 * 系统/设备/网络/电源四分区，页面存续期间每 5 秒重采一次，可手动刷新。
 *
 * 电量以 VHAL 为准：这类车机的 `BatteryManager` 恒 0，直接显示会把车演成"没电"。
 */
@Composable
fun SystemPanelScreen(
    provider: SystemInfoProvider,
    onBackClick: () -> Unit,
) {
    val snapshot = rememberSystemSnapshot(provider)
    val shown = snapshot.info
    val batteryStatus = snapshot.batteryStatus
    val updatedAt = snapshot.updatedAt
    val refresh = snapshot::refresh

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
        MetricStrip(info = shown, batteryStatus = batteryStatus, updatedAt = updatedAt)
        Spacer(modifier = Modifier.height(AOSSpacing.lg))
        InfoCards(
            info = shown,
            batteryStatus = batteryStatus,
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        )
    }
}

/**
 * 电量读数文案。有值给百分比；读不到时权限问题单独说明（这是普通安装最常见的真实原因），
 * 其余情况只说"不可用"——细节已经进 logcat，不把诊断串塞进界面语言。
 */
@Composable
private fun batteryLevelLabel(levelPercent: Int?, unavailableStatus: DegradeStatus?): String = when {
    levelPercent != null -> "$levelPercent%"
    unavailableStatus == DegradeStatus.PermissionDenied ->
        stringResource(R.string.battery_level_permission_denied)

    else -> stringResource(R.string.value_unavailable)
}

@Composable
private fun TopBar(
    updatedAt: String,
    onBackClick: () -> Unit,
    onRefreshClick: () -> Unit,
) {
    AOSPageHeader(
        title = stringResource(R.string.panel_title),
        subtitle = stringResource(R.string.panel_updated_at, updatedAt),
        onBackClick = onBackClick,
    ) { AOSSecondaryButton(stringResource(R.string.panel_action_refresh), onRefreshClick) }
}

@Composable
private fun MetricStrip(info: SystemInfo, batteryStatus: DegradeStatus?, updatedAt: String) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AOSSpacing.md),
        verticalArrangement = Arrangement.spacedBy(AOSSpacing.md),
        maxItemsInEachRow = 3,
    ) {
        val metricModifier = Modifier.weight(1f)
        AOSMetricCard(batteryLevelLabel(info.battery.levelPercent, batteryStatus), stringResource(R.string.label_battery_level), metricModifier)
        AOSMetricCard(AOSLabels.networkLabel(info.networkTransport), stringResource(R.string.label_network), metricModifier)
        AOSMetricCard(stringResource(if (info.isAutomotive) R.string.value_yes else R.string.value_no), stringResource(R.string.label_automotive), metricModifier)
    }
}

@Composable
private fun InfoCards(
    info: SystemInfo,
    batteryStatus: DegradeStatus?,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(AOSSpacing.md)) {
        Row(horizontalArrangement = Arrangement.spacedBy(AOSSpacing.md)) {
            SystemCard(info = info, modifier = Modifier.weight(1f))
            DeviceCard(info = info, modifier = Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(AOSSpacing.md)) {
            NetworkCard(info = info, modifier = Modifier.weight(1f))
            PowerCard(info = info, batteryStatus = batteryStatus, modifier = Modifier.weight(1f))
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
private fun PowerCard(info: SystemInfo, batteryStatus: DegradeStatus?, modifier: Modifier = Modifier) {
    AOSCard(modifier = modifier) {
        AOSSectionHeader(title = stringResource(R.string.section_power))
        Spacer(modifier = Modifier.height(AOSSpacing.sm))
        AOSDataRow(
            label = stringResource(R.string.label_battery_level),
            value = batteryLevelLabel(info.battery.levelPercent, batteryStatus),
        )
        AOSRowDivider()
        AOSDataRow(
            label = stringResource(R.string.label_battery_state),
            value = AOSLabels.batteryStateLabel(info.battery),
        )
    }
}
