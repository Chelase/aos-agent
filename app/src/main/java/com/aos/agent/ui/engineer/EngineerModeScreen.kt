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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.aos.agent.R
import com.aos.agent.system.NetworkTransport
import com.aos.agent.system.SystemInfo
import com.aos.agent.ui.components.AOSCard
import com.aos.agent.ui.components.AOSDataRow
import com.aos.agent.ui.components.AOSMetric
import com.aos.agent.ui.components.AOSRowDivider
import com.aos.agent.ui.components.AOSSectionHeader
import com.aos.agent.ui.components.AOSSecondaryButton
import com.aos.agent.ui.theme.AOSSizing
import com.aos.agent.ui.theme.AOSSpacing
import com.aos.agent.ui.theme.AOSTheme

/**
 * 工程师模式（design.md §6.4）：顶栏 + 指标条 + 分组卡片。
 * 本期只读现有 [SystemInfo]，Car API 扫描 / 服务检查 / 传感器留到后续 Step。
 */
@Composable
fun EngineerModeScreen(
    systemInfo: SystemInfo,
    onBackClick: () -> Unit,
) {
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
                style = MaterialTheme.typography.labelSmall,
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

/** 网络承载类型映射到本地化文案。Batch 1 Step 2 的系统面板复用时再提取到共享位置。 */
@Composable
private fun networkLabel(transport: NetworkTransport): String = stringResource(
    when (transport) {
        NetworkTransport.WIFI -> R.string.network_wifi
        NetworkTransport.CELLULAR -> R.string.network_cellular
        NetworkTransport.ETHERNET -> R.string.network_ethernet
        NetworkTransport.OTHER -> R.string.network_other
        NetworkTransport.NONE -> R.string.network_none
        NetworkTransport.UNAVAILABLE -> R.string.network_unavailable
    },
)

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
                value = networkLabel(systemInfo.networkTransport),
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
