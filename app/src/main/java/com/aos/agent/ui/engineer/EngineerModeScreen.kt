package com.aos.agent.ui.engineer

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.aos.agent.R
import com.aos.agent.core.voice.WakeDebug
import com.aos.agent.core.voice.WakeWordPhrases
import com.aos.agent.system.SystemInfo
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

/**
 * 工程师模式（design.md §6.4）：顶栏 + 指标条 + 分组卡片。
 * 本期只读现有 [SystemInfo]，Car API 扫描 / 服务检查 / 传感器留到后续 Step。
 */
@Composable
fun EngineerModeScreen(
    systemInfo: SystemInfo,
    wakePhrases: List<String> = emptyList(),
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
        InfoCards(
            systemInfo = systemInfo,
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        )
        Spacer(modifier = Modifier.height(AOSSpacing.lg))
        WakeDebugCard(phrases = wakePhrases)
    }
}

@Composable
private fun TopBar(onBackClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(AOSSizing.topBarHeight),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = stringResource(R.string.engineer_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = stringResource(R.string.engineer_subtitle),
                style = aosLegendStyle,
                color = AOSTheme.textTertiary,
            )
        }
        AOSSecondaryButton(
            text = stringResource(R.string.action_back),
            onClick = onBackClick,
        )
    }
}

/** 指标条：把最关键的三个值提到顶部，无需读整张卡片即可判断环境。 */
@Composable
private fun MetricStrip(systemInfo: SystemInfo) {
    AOSCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            AOSMetric(
                value = systemInfo.androidVersion,
                label = stringResource(R.string.label_android_version),
            )
            AOSMetric(
                value = systemInfo.sdkInt.toString(),
                label = stringResource(R.string.label_api_level),
            )
            AOSMetric(
                value = if (systemInfo.isAutomotive) {
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
    systemInfo: SystemInfo,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(AOSSpacing.md),
    ) {
        AOSCard(modifier = Modifier.weight(1f)) {
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

        AOSCard(modifier = Modifier.weight(1f)) {
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
