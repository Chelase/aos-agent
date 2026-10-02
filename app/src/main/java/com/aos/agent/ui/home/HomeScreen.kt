package com.aos.agent.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.aos.agent.R
import com.aos.agent.system.SystemInfo
import com.aos.agent.ui.components.AOSCard
import com.aos.agent.ui.components.AOSDataRow
import com.aos.agent.ui.components.AOSDestinationRow
import com.aos.agent.ui.components.AOSLanguageSwitch
import com.aos.agent.ui.components.AOSLogo
import com.aos.agent.ui.components.AOSSectionHeader
import com.aos.agent.ui.components.AOSStatusBadge
import com.aos.agent.ui.components.AOSStatusDot
import com.aos.agent.ui.components.AOSStatusTone
import com.aos.agent.ui.theme.AOSSizing
import com.aos.agent.ui.theme.AOSSpacing
import com.aos.agent.ui.theme.AOSTheme

/**
 * 首页三栏布局（design.md §6.2）：品牌区 / 快捷操作 / 状态一览。
 * 横屏车机不做纵向堆叠，三栏并列保证一屏可见、扫视时间 < 1s。
 */
@Composable
fun HomeScreen(
    systemInfo: SystemInfo,
    currentLanguageLabel: String,
    targetLanguageLabel: String,
    languageSwitchable: Boolean,
    onEngineerModeClick: () -> Unit,
    onChatClick: () -> Unit,
    onTerminalClick: () -> Unit,
    onSystemPanelClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onLanguageToggle: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .background(
                // 左上角品牌光晕，避免大面积纯色背景显得扁平
                Brush.radialGradient(
                    colors = listOf(AOSTheme.accentDim, MaterialTheme.colorScheme.background),
                    center = Offset(0f, 0f),
                    radius = 1200f,
                ),
            )
            .padding(AOSSizing.safeZone),
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(AOSSpacing.lg),
        ) {
            BrandColumn(
                systemInfo = systemInfo,
                modifier = Modifier
                    .weight(0.30f)
                    .fillMaxHeight(),
            )
            QuickActionsColumn(
                onEngineerModeClick = onEngineerModeClick,
                onChatClick = onChatClick,
                onTerminalClick = onTerminalClick,
                onSystemPanelClick = onSystemPanelClick,
                onSettingsClick = onSettingsClick,
                modifier = Modifier
                    .weight(0.42f)
                    .fillMaxHeight(),
            )
            GlanceColumn(
                systemInfo = systemInfo,
                currentLanguageLabel = currentLanguageLabel,
                targetLanguageLabel = targetLanguageLabel,
                languageSwitchable = languageSwitchable,
                onLanguageToggle = onLanguageToggle,
                modifier = Modifier
                    .weight(0.28f)
                    .fillMaxHeight(),
            )
        }
    }
}

@Composable
private fun BrandColumn(
    systemInfo: SystemInfo,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
    ) {
        AOSLogo()
        Spacer(modifier = Modifier.height(AOSSpacing.lg))
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(AOSSpacing.xs))
        Text(
            text = stringResource(R.string.app_tagline),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(AOSSpacing.lg))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AOSSpacing.sm),
        ) {
            AOSStatusDot(tone = AOSStatusTone.SUCCESS)
            Text(
                text = stringResource(R.string.status_agent_standby),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.height(AOSSpacing.md))
        Text(
            text = systemInfo.summary,
            style = MaterialTheme.typography.labelSmall,
            color = AOSTheme.textTertiary,
        )
    }
}

@Composable
private fun QuickActionsColumn(
    onEngineerModeClick: () -> Unit,
    onChatClick: () -> Unit,
    onTerminalClick: () -> Unit,
    onSystemPanelClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
    ) {
        AOSSectionHeader(title = stringResource(R.string.home_quick_actions))
        Spacer(modifier = Modifier.height(AOSSpacing.md))

        val ready = stringResource(R.string.badge_ready)

        // 点标导航行（design.md §5.10）：行即界面。
        // 终端（Batch 2 Step 1）/ 系统面板（Batch 1 Step 2）/ 对话 / 工程师模式 / 设置均已交付。
        DestinationRow(
            title = stringResource(R.string.action_terminal),
            badgeText = ready,
            enabled = true,
            onClick = onTerminalClick,
        )
        RowDivider()
        DestinationRow(
            title = stringResource(R.string.action_chat),
            badgeText = ready,
            enabled = true,
            onClick = onChatClick,
        )
        RowDivider()
        DestinationRow(
            title = stringResource(R.string.action_system_panel),
            badgeText = ready,
            enabled = true,
            onClick = onSystemPanelClick,
        )
        RowDivider()
        DestinationRow(
            title = stringResource(R.string.action_engineer_mode),
            badgeText = ready,
            enabled = true,
            onClick = onEngineerModeClick,
        )
        RowDivider()
        DestinationRow(
            title = stringResource(R.string.action_settings),
            badgeText = ready,
            enabled = true,
            onClick = onSettingsClick,
        )
    }
}

@Composable
private fun DestinationRow(
    title: String,
    badgeText: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    AOSDestinationRow(
        title = title,
        badgeText = badgeText,
        enabled = enabled,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun RowDivider() {
    HorizontalDivider(
        thickness = AOSSizing.borderWidth,
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

@Composable
private fun GlanceColumn(
    systemInfo: SystemInfo,
    currentLanguageLabel: String,
    targetLanguageLabel: String,
    languageSwitchable: Boolean,
    onLanguageToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
    ) {
        AOSSectionHeader(title = stringResource(R.string.home_at_a_glance))
        Spacer(modifier = Modifier.height(AOSSpacing.md))

        AOSCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.status_agent),
                    style = MaterialTheme.typography.labelSmall,
                    color = AOSTheme.textTertiary,
                )
                AOSStatusBadge(
                    text = stringResource(R.string.status_agent_standby),
                    tone = AOSStatusTone.SUCCESS,
                )
            }
            Spacer(modifier = Modifier.height(AOSSpacing.sm))
            AOSDataRow(
                label = stringResource(R.string.status_platform),
                value = if (systemInfo.isAutomotive) {
                    stringResource(R.string.status_platform_automotive)
                } else {
                    stringResource(R.string.status_platform_generic)
                },
            )
            AOSDataRow(
                label = stringResource(R.string.status_drive_mode),
                value = stringResource(R.string.status_drive_mode_parked),
            )
        }

        Spacer(modifier = Modifier.height(AOSSpacing.md))

        AOSCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.language_label),
                style = MaterialTheme.typography.labelSmall,
                color = AOSTheme.textTertiary,
            )
            Spacer(modifier = Modifier.height(AOSSpacing.sm))
            AOSLanguageSwitch(
                currentLabel = currentLanguageLabel,
                targetLabel = targetLanguageLabel,
                description = stringResource(R.string.language_switch_description),
                onToggle = onLanguageToggle,
                enabled = languageSwitchable,
                disabledLabel = stringResource(R.string.language_follow_system),
                disabledReason = stringResource(R.string.language_follow_system_reason),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
