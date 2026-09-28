package com.aos.agent.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import com.aos.agent.ui.theme.AOSDataText
import com.aos.agent.ui.theme.AOSSizing
import com.aos.agent.ui.theme.AOSSpacing
import com.aos.agent.ui.theme.AOSTheme

/**
 * 交互控件：按钮、状态标签、功能磁贴、语言开关。
 * 规范见 `.agent-rules/docs/原型/design.md` §5.1 / §5.2。
 *
 * 所有可点击控件最小高度为 [AOSSizing.touchTarget]（56dp），满足驾驶场景触控要求。
 * 按压反馈统一为 0.97 缩放 / 100ms，符合 design.md §8.2。
 */

private const val PRESS_SCALE = 0.97f
private const val PRESS_DURATION_MS = 100

/** 主按钮：品牌色实底。禁用态与磁贴同一处理——降对比、不响应，原因由调用方在附近说明。 */
@Composable
fun AOSPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) PRESS_SCALE else 1f,
        animationSpec = tween(PRESS_DURATION_MS),
        label = "primaryButtonScale",
    )

    Box(
        modifier = modifier
            .scale(scale)
            .height(AOSSizing.touchTarget)
            .clip(RoundedCornerShape(AOSSizing.cardCorner))
            .background(
                if (enabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                },
            )
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = AOSSpacing.lg),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = if (enabled) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                AOSTheme.textTertiary
            },
        )
    }
}

/** 次按钮：透明底 + 品牌色描边。禁用态降对比且不响应，原因由调用方在附近说明。 */
@Composable
fun AOSSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) PRESS_SCALE else 1f,
        animationSpec = tween(PRESS_DURATION_MS),
        label = "secondaryButtonScale",
    )

    Box(
        modifier = modifier
            .scale(scale)
            .height(AOSSizing.touchTarget)
            .clip(RoundedCornerShape(AOSSizing.cardCorner))
            .border(
                width = AOSSizing.borderWidth,
                color = if (enabled) AOSTheme.borderAccent else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(AOSSizing.cardCorner),
            )
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = AOSSpacing.lg),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = if (enabled) {
                MaterialTheme.colorScheme.primary
            } else {
                AOSTheme.textTertiary
            },
        )
    }
}

/** 状态标签语义。 */
enum class AOSStatusTone { SUCCESS, WARNING, ERROR, INFO, NEUTRAL }

@Composable
fun AOSStatusBadge(
    text: String,
    tone: AOSStatusTone,
    modifier: Modifier = Modifier,
) {
    val extended = AOSTheme
    val background: Color
    val foreground: Color
    when (tone) {
        AOSStatusTone.SUCCESS -> {
            background = extended.successSurface
            foreground = extended.statusSuccess
        }
        AOSStatusTone.WARNING -> {
            background = extended.warningSurface
            foreground = extended.statusWarning
        }
        AOSStatusTone.ERROR -> {
            background = extended.errorSurface
            foreground = extended.statusError
        }
        AOSStatusTone.INFO -> {
            background = extended.infoSurface
            foreground = extended.statusInfo
        }
        AOSStatusTone.NEUTRAL -> {
            background = MaterialTheme.colorScheme.surfaceVariant
            foreground = extended.textTertiary
        }
    }

    Box(
        modifier = modifier
            .height(AOSSizing.badgeHeight)
            .clip(RoundedCornerShape(AOSSizing.badgeCorner))
            .background(background)
            .padding(horizontal = AOSSizing.badgePadding),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = foreground,
        )
    }
}

/** 状态圆点，配合文本表示运行态。 */
@Composable
fun AOSStatusDot(
    tone: AOSStatusTone,
    modifier: Modifier = Modifier,
) {
    val extended = AOSTheme
    val color = when (tone) {
        AOSStatusTone.SUCCESS -> extended.statusSuccess
        AOSStatusTone.WARNING -> extended.statusWarning
        AOSStatusTone.ERROR -> extended.statusError
        AOSStatusTone.INFO -> extended.statusInfo
        AOSStatusTone.NEUTRAL -> extended.textTertiary
    }
    Box(
        modifier = modifier
            .size(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(color),
    )
}

/**
 * 功能磁贴：首页 2x2 快捷入口。
 *
 * [enabled] 为 false 时渲染为未开放态——降低对比度并禁用点击，
 * 但仍然可见，让用户知道能力路线图，而不是假装功能已存在。
 */
@Composable
fun AOSActionTile(
    title: String,
    badgeText: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) PRESS_SCALE else 1f,
        animationSpec = tween(PRESS_DURATION_MS),
        label = "actionTileScale",
    )
    val borderColor by animateColorAsState(
        targetValue = if (enabled) AOSTheme.borderAccent else MaterialTheme.colorScheme.outlineVariant,
        animationSpec = tween(200),
        label = "actionTileBorder",
    )

    Box(
        modifier = modifier
            .scale(scale)
            .defaultMinSize(minHeight = 96.dp)
            .clip(RoundedCornerShape(AOSSizing.cardCorner))
            .background(
                if (enabled) MaterialTheme.colorScheme.surface
                else MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
            )
            .border(
                width = AOSSizing.borderWidth,
                color = borderColor,
                shape = RoundedCornerShape(AOSSizing.cardCorner),
            )
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(AOSSpacing.md),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(AOSSpacing.sm),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    AOSTheme.textTertiary
                },
            )
            AOSStatusBadge(
                text = badgeText,
                tone = if (enabled) AOSStatusTone.SUCCESS else AOSStatusTone.NEUTRAL,
            )
        }
    }
}

/**
 * 语言开关。
 *
 * 可切换：`当前语言 | 目标语言`，点右侧即切换（中英两种，用按钮而非下拉，
 * 减少驾驶场景下的交互步数）。
 *
 * 不可切换（低版本车机没有框架级 per-app locale）：不隐藏、也不留一个按了没反应的
 * 死按钮——控件收成"当前语言"状态块（避免出现两个相同标签被读成按钮），
 * 下方给出禁用标签与原因。禁用控件允许低对比，但原因文案是有效信息，
 * 取 onSurfaceVariant 保证深色底上的可读性。
 */
@Composable
fun AOSLanguageSwitch(
    currentLabel: String,
    targetLabel: String,
    description: String,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    disabledLabel: String? = null,
    disabledReason: String? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) PRESS_SCALE else 1f,
        animationSpec = tween(PRESS_DURATION_MS),
        label = "languageSwitchScale",
    )
    val descriptionText = if (enabled) description else (disabledReason ?: description)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(AOSSpacing.sm),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .scale(scale)
                .height(AOSSizing.touchTarget)
                .clip(RoundedCornerShape(AOSSizing.cardCorner))
                .background(
                    if (enabled) {
                        MaterialTheme.colorScheme.surfaceVariant
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    },
                )
                .clickable(
                    enabled = enabled,
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onToggle,
                )
                .semantics { contentDescription = descriptionText }
                .padding(horizontal = AOSSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AOSSpacing.sm),
        ) {
            Text(
                text = currentLabel,
                style = AOSDataText.standard,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    AOSTheme.textTertiary
                },
            )
            if (enabled) {
                Spacer(
                    modifier = Modifier
                        .width(AOSSizing.borderWidth)
                        .height(16.dp)
                        .background(MaterialTheme.colorScheme.outline),
                )
                Text(
                    text = targetLabel,
                    style = AOSDataText.standard,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        if (!enabled) {
            disabledLabel?.let {
                AOSStatusBadge(text = it, tone = AOSStatusTone.NEUTRAL)
            }
            disabledReason?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
