package com.aos.agent.ui.systempanel

import android.util.Log
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.aos.agent.R
import com.aos.agent.core.tools.DegradeStatus
import com.aos.agent.system.BatterySnapshot
import com.aos.agent.system.SystemInfo
import com.aos.agent.system.SystemInfoProvider
import com.aos.agent.system.vehicle.AndroidVehicleReader
import com.aos.agent.system.vehicle.VehicleBatteryReader
import com.aos.agent.system.vehicle.VehiclePropertyAllowlist
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
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    val vehicleBattery = rememberVehicleBatteryReader()
    var info by remember { mutableStateOf(provider.collect()) }
    // 合并后的电量：VHAL 有值 > 系统广播 > 不可用。读到之前留空，不拿广播旧值先播一个假百分比。
    var battery by remember { mutableStateOf(BatterySnapshot(null, null)) }
    var batteryStatus by remember { mutableStateOf<DegradeStatus?>(null) }
    var updatedAt by remember { mutableStateOf(formatClock()) }
    val refreshScope = rememberCoroutineScope()

    suspend fun reload() {
        val collected = provider.collect()
        val vehicle = vehicleBattery.read()
        val merged = collected.battery.withVehicleLevel(vehicle.levelPercent)
        // 只在"刚开始读不到"时记一行：状态恢复正常后 batteryStatus 会被清掉，所以下次失效还会记
        val firstFailure = merged.levelPercent == null && batteryStatus == null
        info = collected
        battery = merged
        batteryStatus = if (merged.levelPercent == null) vehicle.unavailableStatus else null
        if (firstFailure) {
            Log.i(TAG, "车辆电量不可用：${vehicle.detail}")
        }
        updatedAt = formatClock()
    }

    LaunchedEffect(Unit) {
        // 先采一次再等周期：Car 属性是异步读，等 5 秒才填会让首帧一直是空值
        reload()
        while (true) {
            delay(REFRESH_INTERVAL_MS)
            reload()
        }
    }

    val refresh: () -> Unit = { refreshScope.launch { reload() } }
    val shown = info.copy(battery = battery)

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
 * 车辆属性连接随面板建、随面板断。
 *
 * `Car.createCar` 只能非主线程调用，所以属性读取全在 [AndroidVehicleReader] 里切到 IO；
 * allowlist 走 assets，换车型不用改这里。
 */
@Composable
private fun rememberVehicleBatteryReader(): VehicleBatteryReader {
    val context = LocalContext.current
    val vehicle = remember { AndroidVehicleReader(context) }
    val reader = remember(vehicle) {
        VehicleBatteryReader(vehicle, VehiclePropertyAllowlist.parseAsset(context))
    }
    DisposableEffect(reader) {
        onDispose { vehicle.close() }
    }
    return reader
}

/** 手动刷新/自动刷新共用的采集时刻格式。 */
private fun formatClock(): String =
    SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())

private const val REFRESH_INTERVAL_MS = 5_000L
private const val TAG = "AOSSystemPanel"

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
private fun MetricStrip(info: SystemInfo, batteryStatus: DegradeStatus?, updatedAt: String) {
    AOSCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            AOSMetric(
                value = batteryLevelLabel(info.battery.levelPercent, batteryStatus),
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
