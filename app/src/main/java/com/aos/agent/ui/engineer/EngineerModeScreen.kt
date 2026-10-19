package com.aos.agent.ui.engineer

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.FlowRow
import com.aos.agent.ui.components.AOSMetricCard
import com.aos.agent.ui.components.AOSAdaptivePanes
import com.aos.agent.ui.components.AOSPageHeader
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.aos.agent.R
import com.aos.agent.core.voice.WakeDebug
import com.aos.agent.core.voice.WakeWordPhrases
import com.aos.agent.system.DriveUxDiagnostics
import com.aos.agent.system.SystemInfo
import com.aos.agent.ui.components.AOSCard
import com.aos.agent.ui.components.AOSDataRow
import com.aos.agent.ui.components.AOSLabels
import com.aos.agent.ui.components.AOSRowDivider
import com.aos.agent.ui.components.AOSSectionHeader
import com.aos.agent.ui.components.AOSSecondaryButton
import com.aos.agent.ui.theme.AOSSizing
import com.aos.agent.ui.theme.AOSSpacing
import com.aos.agent.ui.theme.AOSTheme
import com.aos.agent.ui.theme.aosLegendStyle

/**
 * 工程师模式（design.md §6.4）：顶栏 + 指标条 + 分组卡片。
 * 本期只读现有 [SystemInfo]，Car API 扫描 / 服务检查 / 传感器留到后续 Step。
 */
@Composable
fun EngineerModeScreen(
    systemInfo: SystemInfo,
    wakePhrases: List<String> = emptyList(),
    driveDiagnostics: DriveUxDiagnostics? = null,
    onBackClick: () -> Unit,
) {
    // 观测只在页面活着时收集，离开即清空——不把现场留在常驻内存里
    DisposableEffect(Unit) {
        WakeDebug.open()
        onDispose { WakeDebug.close() }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(AOSSizing.safeZone),
    ) {
        TopBar(onBackClick = onBackClick)
        Spacer(modifier = Modifier.height(AOSSpacing.lg))
        MetricStrip(systemInfo = systemInfo)
        Spacer(modifier = Modifier.height(AOSSpacing.lg))
        AOSAdaptivePanes(
            modifier = Modifier.fillMaxWidth().weight(1f),
            first = {
                if (driveDiagnostics != null) DriveDiagnosticsCard(diagnostics = driveDiagnostics)
                WakeDebugCard(phrases = wakePhrases)
            },
            second = { InfoCards(systemInfo = systemInfo, modifier = Modifier.fillMaxWidth()) },
        )
    }
}

@Composable
private fun TopBar(onBackClick: () -> Unit) {
    AOSPageHeader(
        title = stringResource(R.string.engineer_title),
        subtitle = stringResource(R.string.engineer_subtitle),
        onBackClick = onBackClick,
    )
}

/** 指标条：把最关键的三个值提到顶部，无需读整张卡片即可判断环境。 */
@Composable
private fun MetricStrip(systemInfo: SystemInfo) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AOSSpacing.md),
        verticalArrangement = Arrangement.spacedBy(AOSSpacing.md),
        maxItemsInEachRow = 3,
    ) {
        val metricModifier = Modifier.weight(1f)
        AOSMetricCard(systemInfo.androidVersion, stringResource(R.string.label_android_version), metricModifier)
        AOSMetricCard(systemInfo.sdkInt.toString(), stringResource(R.string.label_api_level), metricModifier)
        AOSMetricCard(stringResource(if (systemInfo.isAutomotive) R.string.value_yes else R.string.value_no), stringResource(R.string.label_automotive), metricModifier)
    }
}

@Composable
private fun InfoCards(
    systemInfo: SystemInfo,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(AOSSpacing.lg),
    ) {
        AOSCard(modifier = Modifier.fillMaxWidth()) {
            AOSSectionHeader(title = stringResource(R.string.section_system))
            Spacer(modifier = Modifier.height(AOSSpacing.sm))
            AOSDataRow(
                label = stringResource(R.string.label_android_version),
                value = systemInfo.androidVersion,
            )
            AOSRowDivider()
            AOSDataRow(
                label = stringResource(R.string.label_api_level),
                value = systemInfo.sdkInt.toString(),
            )
            AOSRowDivider()
            AOSDataRow(
                label = stringResource(R.string.label_automotive),
                value = if (systemInfo.isAutomotive) {
                    stringResource(R.string.value_yes)
                } else {
                    stringResource(R.string.value_no)
                },
            )
            AOSRowDivider()
            AOSDataRow(
                label = stringResource(R.string.label_network),
                value = AOSLabels.networkLabel(systemInfo.networkTransport),
            )
        }

        AOSCard(modifier = Modifier.fillMaxWidth()) {
            AOSSectionHeader(title = stringResource(R.string.section_device))
            Spacer(modifier = Modifier.height(AOSSpacing.sm))
            AOSDataRow(
                label = stringResource(R.string.label_manufacturer),
                value = systemInfo.manufacturer,
            )
            AOSRowDivider()
            AOSDataRow(
                label = stringResource(R.string.label_brand),
                value = systemInfo.brand,
            )
            AOSRowDivider()
            AOSDataRow(
                label = stringResource(R.string.label_model),
                value = systemInfo.model,
            )
            AOSRowDivider()
            AOSDataRow(
                label = stringResource(R.string.label_device),
                value = systemInfo.device,
            )
        }
    }
}

/**
 * 驾驶态诊断卡：
 *
 * Bound（已绑定）只说真实状态；Unavailable（哪一段失败）显式告警——
 * 行驶遮罩永不出现时，这里必须能说出为什么，而不是首页只写"未知"。
 */
@Composable
private fun DriveDiagnosticsCard(diagnostics: DriveUxDiagnostics) {
    AOSCard(modifier = Modifier.fillMaxWidth()) {
        AOSSectionHeader(title = stringResource(R.string.section_drive_diag))
        Spacer(modifier = Modifier.height(AOSSpacing.sm))
        when (diagnostics) {
            is DriveUxDiagnostics.Bound -> {
                AOSDataRow(
                    label = stringResource(R.string.drive_diag_state),
                    value = AOSLabels.driveRestrictionLabel(diagnostics.restriction),
                )
                AOSRowDivider()
                AOSDataRow(
                    label = stringResource(R.string.drive_diag_binding),
                    value = diagnostics.carService,
                )
            }

            is DriveUxDiagnostics.Unavailable -> {
                AOSDataRow(
                    label = stringResource(R.string.drive_diag_state),
                    value = AOSLabels.driveRestrictionLabel(
                        com.aos.agent.system.DriveRestriction.UNKNOWN,
                    ),
                )
                AOSRowDivider()
                AOSDataRow(
                    label = stringResource(R.string.drive_diag_reason),
                    value = AOSLabels.driveFailureLabel(diagnostics.stage) +
                        "（${diagnostics.reason}）",
                )
                Spacer(modifier = Modifier.height(AOSSpacing.sm))
                Text(
                    text = stringResource(R.string.drive_diag_unavailable_hint),
                    style = aosLegendStyle,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

/**
 * 唤醒调试卡：真车上"喊五次醒几次、平时会不会乱醒"的唯一眼睛。
 *
 * 显示的是 Vosk 原始输出（受限语法下只可能是选定的说法或 `[unk]`，日常对话内容流不进来），
 * 只在内存里、不落盘。
 */
@Composable
private fun WakeDebugCard(phrases: List<String>) {
    val debug by WakeDebug.state.collectAsState()
    Column(modifier = Modifier.fillMaxWidth()) {
        AOSCard(modifier = Modifier.fillMaxWidth()) {
            AOSSectionHeader(title = stringResource(R.string.section_wake_debug))
            Spacer(modifier = Modifier.height(AOSSpacing.sm))
            AOSDataRow(
                label = stringResource(R.string.wake_debug_phrases),
                value = if (phrases.isEmpty()) {
                    stringResource(R.string.wake_debug_phrases_none)
                } else {
                    WakeWordPhrases.displayOf(phrases)
                },
            )
            AOSRowDivider()
            AOSDataRow(
                label = stringResource(R.string.wake_debug_hits),
                value = debug.hits.toString(),
            )
            AOSRowDivider()
            AOSDataRow(
                label = stringResource(R.string.wake_debug_misses),
                value = debug.misses.toString(),
            )
            Spacer(modifier = Modifier.height(AOSSpacing.sm))
            if (debug.observations.isEmpty()) {
                Text(
                    text = stringResource(R.string.wake_debug_empty),
                    style = aosLegendStyle,
                    color = AOSTheme.textTertiary,
                )
            } else {
                debug.observations.asReversed().forEach { item ->
                    AOSDataRow(
                        label = "+%.1fs".format(item.sinceOpenMillis / 1000f),
                        value = stringResource(
                            if (item.matched) R.string.wake_debug_result_hit else R.string.wake_debug_result_miss,
                            item.text.ifBlank { stringResource(R.string.wake_debug_result_empty) },
                        ),
                    )
                    AOSRowDivider()
                }
            }
            Spacer(modifier = Modifier.height(AOSSpacing.sm))
            AOSSecondaryButton(
                text = stringResource(R.string.wake_debug_reset),
                onClick = { WakeDebug.resetCounters() },
            )
        }
    }
}
