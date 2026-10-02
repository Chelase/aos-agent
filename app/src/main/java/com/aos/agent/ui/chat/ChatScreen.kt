package com.aos.agent.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.aos.agent.R
import com.aos.agent.runtime.RuntimeStatus
import com.aos.agent.ui.components.AOSPrimaryButton
import com.aos.agent.ui.components.AOSSecondaryButton
import com.aos.agent.ui.theme.AOSDataText
import com.aos.agent.ui.theme.AOSSizing
import com.aos.agent.ui.theme.AOSSpacing
import com.aos.agent.ui.theme.AOSTheme
import com.aos.agent.ui.voice.VoiceMicButton
import com.aos.agent.ui.voice.VoiceStatusLine
import com.aos.agent.ui.voice.VoiceUiState
import com.aos.agent.ui.theme.aosLegendStyle

/**
 * 对话页（design.md §6.3，v4.0 纯化）：单栏满宽——顶栏、对话流、输入行。
 *
 * 对话即对话：用户消息、助手回复与工具轨迹逐行滚动，不常驻运行状态面板；
 * 配置入口在首页设置，不在本页。未配置模型等阻断态以警示行出现在输入区上方，
 * 并指引到首页设置。输入行左侧是麦克风：语音与键盘是同一条通路的两种输入，
 * 识别中的部分结果直接进输入框（所见即所发）。
 */
@Composable
fun ChatScreen(
    entries: List<ChatEntry>,
    status: RuntimeStatus,
    busy: Boolean,
    voiceState: VoiceUiState,
    onBackClick: () -> Unit,
    onSend: (String) -> Unit,
    onVoiceToggle: () -> Unit,
) {
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // 语音部分结果直接落到输入框，用户可以在发送前改字
    LaunchedEffect(voiceState.partial) {
        if (voiceState.partial.isNotBlank()) input = voiceState.partial
    }

    LaunchedEffect(entries.size, busy) {
        if (entries.isNotEmpty()) listState.animateScrollToItem(entries.lastIndex)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(AOSSizing.safeZone),
    ) {
        TopBar(onBackClick = onBackClick)
        Spacer(modifier = Modifier.height(AOSSpacing.lg))

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(AOSSpacing.sm),
        ) {
            if (entries.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.chat_empty_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = AOSTheme.textTertiary,
                    )
                }
            }
            items(entries) { entry -> ChatRow(entry) }
            if (busy) {
                item {
                    Text(
                        text = stringResource(R.string.chat_sending),
                        style = MaterialTheme.typography.labelSmall,
                        color = AOSTheme.textTertiary,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(AOSSpacing.sm))
        VoiceStatusLine(state = voiceState, modifier = Modifier.padding(bottom = AOSSpacing.xs))
        InputRow(
            input = input,
            canSend = status.canSend && !busy,
            blockedReason = status.blockedReason,
            voiceState = voiceState,
            onInputChange = { input = it },
            onSend = {
                val query = input.trim()
                if (query.isNotEmpty() && status.canSend && !busy) {
                    input = ""
                    onSend(query)
                }
            },
            onVoiceToggle = onVoiceToggle,
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
                text = stringResource(R.string.chat_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = stringResource(R.string.chat_subtitle),
                style = aosLegendStyle,
                color = AOSTheme.textTertiary,
            )
        }
        AOSSecondaryButton(text = stringResource(R.string.action_back), onClick = onBackClick)
    }
}

@Composable
private fun ChatRow(entry: ChatEntry) {
    when (entry) {
        is ChatEntry.User -> Text(
            text = "› ${entry.text}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )

        is ChatEntry.Assistant -> Text(
            text = entry.text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        is ChatEntry.Note -> Text(
            text = entry.text,
            style = MaterialTheme.typography.labelSmall,
            color = AOSTheme.statusWarning,
        )

        is ChatEntry.ToolCalled -> Text(
            text = "→ ${entry.tool} ${entry.args}",
            style = AOSDataText.standard,
            color = AOSTheme.statusInfo,
        )

        is ChatEntry.ToolFinished -> Text(
            text = "← ${entry.tool} [${entry.outcome}] ${entry.payload.take(240)}",
            style = AOSDataText.standard,
            color = when (entry.outcome) {
                "ok" -> AOSTheme.statusSuccess
                "rejected" -> AOSTheme.statusWarning
                else -> AOSTheme.statusError
            },
        )
    }
}

@Composable
private fun InputRow(
    input: String,
    canSend: Boolean,
    blockedReason: String?,
    voiceState: VoiceUiState,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onVoiceToggle: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        blockedReason?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                color = AOSTheme.statusWarning,
                modifier = Modifier.padding(bottom = AOSSpacing.xs),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AOSSpacing.sm),
        ) {
            VoiceMicButton(
                phase = voiceState.phase,
                enabled = voiceState.usable,
                onClick = onVoiceToggle,
            )
            OutlinedTextField(
                value = input,
                onValueChange = onInputChange,
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        text = stringResource(R.string.chat_input_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = AOSTheme.textTertiary,
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(AOSSizing.cardCorner),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
            )
            AOSPrimaryButton(
                text = stringResource(R.string.chat_send),
                onClick = onSend,
                enabled = canSend,
            )
        }
    }
}
