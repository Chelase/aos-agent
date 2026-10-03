package com.aos.agent.ui.voice

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import com.aos.agent.R
import com.aos.agent.ui.components.AOSSecondaryButton
import com.aos.agent.ui.theme.AOSSizing
import com.aos.agent.ui.theme.AOSSpacing
import com.aos.agent.ui.theme.AOSTheme

/**
 * 行驶受限（CarUxRestrictions=RESTRICTED）全屏语音遮罩。
 *
 * 只放三样东西：状态灯化的大麦克风（≥[AOSSizing.driveTarget]）、它的状态行、返回首页；
 * 文字输入、对话列表、终端一概不出现（`docs/design-agent-handoff.md` 驾驶模式约束）。
 * 语音不可用时不藏也不装可用——按钮保持禁用态，原因走 [VoiceStatusLine]，
 * 因为行驶中"按了没反应又没说为什么"是最坏的一种界面。
 */
@Composable
fun DriveVoiceMask(
    state: VoiceUiState,
    onMicClick: () -> Unit,
    onBackHome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // 遮罩必须自己吃掉手势：Compose 的命中路径会把事件继续发给被盖住的页面，
            // 实测点到底层页面的"返回"能把遮罩整个跳没——那就不叫遮罩了。
            // 子节点先于父节点收到事件，所以不影响本层的大麦克风与返回首页。
            .pointerInput(Unit) {
                awaitEachGesture {
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        event.changes.forEach { it.consume() }
                    } while (event.changes.any { it.pressed })
                }
            }
            .padding(AOSSizing.safeZone),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.drive_mask_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(modifier = Modifier.height(AOSSpacing.xs))
            Text(
                text = stringResource(R.string.drive_mask_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = AOSTheme.textTertiary,
            )
            Spacer(modifier = Modifier.height(AOSSpacing.lg))
            VoiceMicButton(
                phase = state.phase,
                enabled = state.usable,
                onClick = onMicClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(min = AOSSizing.driveTarget),
                targetSize = AOSSizing.driveTarget,
                labelStyle = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(AOSSpacing.sm))
            VoiceStatusLine(state = state)
            Spacer(modifier = Modifier.height(AOSSpacing.xl))
            AOSSecondaryButton(
                text = stringResource(R.string.drive_mask_back_home),
                onClick = onBackHome,
                height = AOSSizing.driveTarget,
            )
        }
    }
}
