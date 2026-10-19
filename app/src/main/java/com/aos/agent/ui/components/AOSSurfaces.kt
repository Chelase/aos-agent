package com.aos.agent.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import com.aos.agent.ui.theme.AOSDataText
import com.aos.agent.ui.theme.AOSSizing
import com.aos.agent.ui.theme.AOSSpacing
import com.aos.agent.ui.theme.AOSTheme
import com.aos.agent.ui.theme.aosLegendStyle

/**
 * 承载面组件：卡片、区块图例、数据行。
 * 规范见 `.agent-rules/docs/原型/design.md` §4.4 / §5.3 / §3.2 Legend。
 */

/** 标准卡片：v6 平面表面与 20dp 圆角，颜色与尺寸均取主题。 */
@Composable
fun AOSCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(AOSSizing.cardCorner))
            .background(MaterialTheme.colorScheme.surface)
            .padding(AOSSizing.cardPadding),
        content = content,
    )
}

/** 共享区块标题，与 v6 的简洁 Material 层级一致。 */
@Composable
fun AOSSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title, modifier = modifier.fillMaxWidth(),
        style = aosLegendStyle, color = MaterialTheme.colorScheme.onSurface,
    )
}

/**
 * 数据行：左标签（次级文本）+ 右值（等宽字体）。
 * 等宽字体保证多行数值右对齐时字符宽度一致，扫视更快。
 */
@Composable
fun AOSDataRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = AOSSpacing.xs),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.42f),
        )
        Spacer(modifier = Modifier.width(AOSSpacing.md))
        Text(
            text = value,
            style = AOSDataText.standard,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(0.58f),
        )
    }
}

/** 数据行之间的细分隔线。 */
@Composable
fun AOSRowDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier,
        thickness = AOSSizing.borderWidth,
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

/** 关键指标：大号数值 + 下方说明标签。 */
@Composable
fun AOSMetric(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(AOSSpacing.xs),
    ) {
        Text(
            text = value,
            style = AOSDataText.large,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = AOSTheme.textTertiary,
        )
    }
}

/** 指标一律从页面真实状态传入，同一承载面跨首页、系统与工程页复用。 */
@Composable
fun AOSMetricCard(value: String, label: String, modifier: Modifier = Modifier) {
    AOSCard(modifier) { AOSMetric(value, label) }
}
