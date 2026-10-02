package com.aos.agent.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.unit.dp
import com.aos.agent.ui.theme.AOSDataText
import com.aos.agent.ui.theme.AOSSizing
import com.aos.agent.ui.theme.AOSSpacing
import com.aos.agent.ui.theme.AOSTheme
import com.aos.agent.ui.theme.aosLegendStyle

/**
 * 承载面组件：卡片、区块图例、数据行。
 * 规范见 `.agent-rules/docs/原型/design.md` §4.4 / §5.3 / §3.2 Legend。
 */

/** 标准卡片：bg-secondary 底 + 发丝级描边 + 12dp 仪表窗圆角。 */
@Composable
fun AOSCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(AOSSizing.cardCorner))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = AOSSizing.borderWidth,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(AOSSizing.cardCorner),
            )
            .padding(AOSSizing.cardPadding),
        content = content,
    )
}

/**
 * 区块图例（Legend）：左侧品牌刻度短条 + 加字距图例文字。
 * 像仪表盘上的刻度图例，是本设计语言的签名排版（design.md §3.2），
 * 所有分组标题共用，避免各页面各写一套。
 */
@Composable
fun AOSSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(
            modifier = Modifier
                .size(width = 3.dp, height = 14.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.primary),
        )
        Spacer(modifier = Modifier.width(AOSSpacing.sm))
        Text(
            text = title,
            style = aosLegendStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
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
            style = MaterialTheme.typography.labelSmall,
            color = AOSTheme.textTertiary,
        )
        Spacer(modifier = Modifier.width(AOSSpacing.md))
        Text(
            text = value,
            style = AOSDataText.standard,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
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

/** 关键指标：大号等宽数值 + 下方说明标签。 */
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
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = AOSTheme.textTertiary,
        )
    }
}
