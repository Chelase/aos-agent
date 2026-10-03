package com.aos.agent.ui.voice

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aos.agent.R
import com.aos.agent.ui.components.AOSStatusDot
import com.aos.agent.ui.components.AOSStatusTone
import com.aos.agent.ui.theme.AOSSizing
import com.aos.agent.ui.theme.AOSSpacing
import com.aos.agent.ui.theme.AOSTheme

/**
 * 语音控件：麦克风按钮（灯语化）与状态行。
 *
 * 按钮最小高度取 `AOSSizing.touchTarget`（56dp，驾驶场景下限）；
 * 禁用态不隐藏也不装可用，原因文案紧邻其下（design.md §5.8）。
 */

@Composable
fun VoiceMicButton(
    phase: VoicePhase,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val glyphColor = if (enabled) MaterialTheme.colorScheme.primary else AOSTheme.textTertiary

    Column(
        modifier = modifier
            .scale(if (pressed && enabled) 0.97f else 1f)
            .heightIn(min = AOSSizing.touchTarget)
            .clip(RoundedCornerShape(AOSSizing.cardCorner))
            .background(
                if (enabled) {
                    MaterialTheme.colorScheme.surfaceVariant
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                },
            )
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
            .padding(horizontal = AOSSpacing.md, vertical = AOSSpacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AOSSpacing.sm),
        ) {
            AOSStatusDot(tone = toneOf(phase))
            MicGlyph(color = glyphColor, modifier = Modifier.size(20.dp))
            Text(
                text = stringResource(labelOf(phase)),
                style = MaterialTheme.typography.labelSmall,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else AOSTheme.textTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 状态行：部分识别结果实时上屏，失败与回执紧随其后，不留静默。 */
@Composable
fun VoiceStatusLine(state: VoiceUiState, modifier: Modifier = Modifier) {
    val noticeArg = state.noticeArg
    val noticeRes = state.noticeRes
    val text = when {
        state.phase == VoicePhase.LISTENING && state.partial.isNotBlank() -> state.partial
        noticeRes != null && noticeArg != null -> stringResource(noticeRes, noticeArg)
        noticeRes != null -> stringResource(noticeRes)
        !state.usable && state.blockedReasonRes != null -> stringResource(state.blockedReasonRes)
        else -> null
    } ?: return

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AOSSpacing.sm),
    ) {
        AOSStatusDot(tone = toneOf(state.phase))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = when (state.phase) {
                VoicePhase.LISTENING -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

private fun toneOf(phase: VoicePhase): AOSStatusTone = when (phase) {
    VoicePhase.IDLE -> AOSStatusTone.NEUTRAL
    VoicePhase.WAITING -> AOSStatusTone.NEUTRAL
    VoicePhase.LISTENING -> AOSStatusTone.SUCCESS
    VoicePhase.THINKING -> AOSStatusTone.INFO
    VoicePhase.SPEAKING -> AOSStatusTone.WARNING
}

private fun labelOf(phase: VoicePhase): Int = when (phase) {
    VoicePhase.IDLE -> R.string.voice_action_listen
    VoicePhase.WAITING -> R.string.voice_action_waiting
    VoicePhase.LISTENING -> R.string.voice_action_listening
    VoicePhase.THINKING -> R.string.voice_action_thinking
    VoicePhase.SPEAKING -> R.string.voice_action_speaking
}

/** 话筒字形：纯 Compose 绘制原语画的胶囊 + 拾音弧 + 支架，避免为单个图标引入图标库。 */
@Composable
private fun MicGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxWidth().fillMaxHeight()) {
        val capsuleWidth = size.width * 0.34f
        val left = (size.width - capsuleWidth) / 2f
        drawRoundRect(
            color = color,
            topLeft = androidx.compose.ui.geometry.Offset(left, size.height * 0.06f),
            size = Size(capsuleWidth, size.height * 0.52f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(capsuleWidth / 2f, capsuleWidth / 2f),
        )
        drawArc(
            color = color,
            startAngle = 20f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.2f, size.height * 0.22f),
            size = Size(size.width * 0.6f, size.height * 0.6f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = size.width * 0.1f),
        )
        drawRect(
            color = color,
            topLeft = androidx.compose.ui.geometry.Offset(
                size.width / 2f - size.width * 0.05f,
                size.height * 0.62f,
            ),
            size = Size(size.width * 0.1f, size.height * 0.3f),
        )
    }
}
