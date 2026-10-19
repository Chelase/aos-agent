package com.aos.agent.ui.home

import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.aos.agent.R
import com.aos.agent.system.SystemInfo
import com.aos.agent.system.SystemInfoProvider
import com.aos.agent.ui.components.*
import com.aos.agent.ui.theme.*
import com.aos.agent.ui.voice.VoiceUiState
import com.aos.agent.ui.state.rememberSystemSnapshot

/** v6：首页的指标与功能入口共用既有真实数据，不携带原型示例。 */
@Composable
fun HomeScreen(
    systemInfo: SystemInfo,
    currentLanguageLabel: String,
    targetLanguageLabel: String,
    languageSwitchable: Boolean,
    driveRestricted: Boolean = false,
    driveStateKnown: Boolean = true,
    onEngineerModeClick: () -> Unit,
    onChatClick: () -> Unit,
    onTerminalClick: () -> Unit,
    onSystemPanelClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onLanguageToggle: () -> Unit,
    provider: SystemInfoProvider? = null,
    voiceState: VoiceUiState = VoiceUiState(),
    appVersionLabel: String = "",
) {
    val snapshot = provider?.let { rememberSystemSnapshot(it) }
    val info = snapshot?.info ?: systemInfo
    val driveLabel = stringResource(when {
        !driveStateKnown -> R.string.status_drive_mode_unknown
        driveRestricted -> R.string.status_drive_mode_driving
        else -> R.string.status_drive_mode_parked
    })
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .padding(AOSSizing.safeZone).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(AOSSpacing.lg),
    ) {
        Column {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.onBackground)
            Text(info.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AOSSpacing.md)) {
            AOSStatusDot(AOSStatusTone.SUCCESS)
            Text(stringResource(R.string.status_agent_standby) + " · " + driveLabel + " · " + AOSLabels.networkLabel(info.networkTransport),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(AOSSpacing.md),
            verticalArrangement = Arrangement.spacedBy(AOSSpacing.md),
            maxItemsInEachRow = 4,
        ) {
            val metricModifier = Modifier.weight(1f).widthIn(min = AOSSizing.driveMic)
            AOSMetricCard(info.battery.levelPercent?.let { "$it%" } ?: stringResource(R.string.value_unavailable), stringResource(R.string.label_battery_level), metricModifier)
            AOSMetricCard(AOSLabels.networkLabel(info.networkTransport), stringResource(R.string.label_network), metricModifier)
            AOSMetricCard(driveLabel, stringResource(R.string.status_drive_mode), metricModifier)
            AOSMetricCard(appVersionLabel.ifBlank { stringResource(R.string.value_unavailable) }, stringResource(R.string.home_version), metricModifier)
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val wide = maxWidth >= AOSSizing.paneMinWidth
            val features: @Composable () -> Unit = {
                AOSCard(Modifier.fillMaxWidth()) {
                    AOSSectionHeader(stringResource(R.string.home_quick_actions))
                    Spacer(Modifier.height(AOSSpacing.md))
                    val ready = stringResource(R.string.badge_ready)
                    val restricted = stringResource(R.string.drive_entry_unavailable)
                    AOSDestinationRow(stringResource(R.string.action_chat), ready, true, onChatClick)
                    AOSRowDivider()
                    AOSDestinationRow(stringResource(R.string.action_terminal), if (driveRestricted) restricted else ready, !driveRestricted, onTerminalClick)
                    AOSRowDivider()
                    AOSDestinationRow(stringResource(R.string.action_system_panel), ready, true, onSystemPanelClick)
                    AOSRowDivider()
                    AOSDestinationRow(stringResource(R.string.home_voice),
                        if (voiceState.usable) ready else stringResource(voiceState.blockedReasonRes ?: R.string.voice_err_no_service),
                        voiceState.usable, onChatClick)
                }
            }
            val vehicle: @Composable () -> Unit = {
                AOSCard(Modifier.fillMaxWidth()) {
                    AOSSectionHeader(stringResource(R.string.home_vehicle))
                    Spacer(Modifier.height(AOSSpacing.md))
                    AOSDataRow(stringResource(R.string.label_manufacturer), info.manufacturer)
                    AOSRowDivider()
                    AOSDataRow(stringResource(R.string.label_model), info.model)
                    AOSRowDivider()
                    AOSDataRow(stringResource(R.string.label_device), info.device)
                    AOSRowDivider()
                    AOSDataRow(stringResource(R.string.status_platform), stringResource(if (info.isAutomotive) R.string.status_platform_automotive else R.string.status_platform_generic))
                    AOSRowDivider()
                    AOSDataRow(stringResource(R.string.status_drive_mode), driveLabel)
                    Spacer(Modifier.height(AOSSpacing.md))
                    AOSLanguageSwitch(currentLanguageLabel, targetLanguageLabel,
                        stringResource(R.string.language_switch_description), onLanguageToggle,
                        enabled = languageSwitchable,
                        disabledLabel = stringResource(R.string.language_follow_system),
                        disabledReason = stringResource(R.string.language_follow_system_reason))
                }
            }
            if (wide) Row(horizontalArrangement = Arrangement.spacedBy(AOSSpacing.lg)) {
                Box(Modifier.weight(1f)) { features() }
                Box(Modifier.weight(1f)) { vehicle() }
            } else Column(verticalArrangement = Arrangement.spacedBy(AOSSpacing.lg)) { features(); vehicle() }
        }
        if (driveRestricted) Text(stringResource(R.string.drive_entry_unavailable), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
