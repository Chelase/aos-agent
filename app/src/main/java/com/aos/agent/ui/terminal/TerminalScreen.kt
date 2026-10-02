package com.aos.agent.ui.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aos.agent.R
import com.aos.agent.terminal.TerminalUiState
import com.aos.agent.terminal.TerminalSession
import com.aos.agent.terminal.TerminalViewModel
import com.aos.agent.ui.components.AOSCard
import com.aos.agent.ui.components.AOSPrimaryButton
import com.aos.agent.ui.components.AOSSecondaryButton
import com.aos.agent.ui.components.AOSStatusBadge
import com.aos.agent.ui.components.AOSStatusTone
import com.aos.agent.ui.theme.AOSSizing
import com.aos.agent.ui.theme.AOSSpacing
import com.aos.agent.ui.theme.AOSTheme
import com.aos.agent.ui.theme.aosLegendStyle

/**
 * 终端页（design.md 卡片式顶栏 + 满宽终端区 + 输入栏）：
 * 真实 shell 经 PTY 驱动，画布纵向拖拽翻回滚，输入栏支持多行粘贴。
 */
@Composable
fun TerminalScreen(
    viewModel: TerminalViewModel,
    onBackClick: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val frameTick by viewModel.frameTick.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.start() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(AOSSizing.safeZone),
    ) {
        TopBar(
            running = state is TerminalUiState.Running,
            onBackClick = onBackClick,
        )
        Spacer(modifier = Modifier.height(AOSSpacing.lg))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            val session = (state as? TerminalUiState.Running)?.session
            TerminalCanvas(
                session = session,
                frameTick = frameTick,
                onSizeChanged = viewModel::updateSize,
            )
            when (val current = state) {
                TerminalUiState.Starting -> CenteredNotice(
                    text = stringResource(R.string.terminal_starting),
                )
                is TerminalUiState.Exited -> CenteredNotice(
                    text = stringResource(R.string.terminal_session_exited, current.exitCode),
                    actionText = stringResource(R.string.terminal_restart),
                    onAction = viewModel::restart,
                )
                is TerminalUiState.Running -> Unit
            }
        }

        Spacer(modifier = Modifier.height(AOSSpacing.md))
        InputBar(
            enabled = state is TerminalUiState.Running,
            onSend = viewModel::send,
            onControl = viewModel::sendControl,
        )
    }
}

@Composable
private fun TopBar(
    running: Boolean,
    onBackClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(AOSSizing.topBarHeight),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = stringResource(R.string.terminal_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = stringResource(R.string.terminal_subtitle),
                style = aosLegendStyle,
                color = AOSTheme.textTertiary,
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AOSSpacing.md),
        ) {
            AOSStatusBadge(
                text = stringResource(
                    if (running) R.string.terminal_status_running else R.string.terminal_status_stopped,
                ),
                tone = if (running) AOSStatusTone.SUCCESS else AOSStatusTone.NEUTRAL,
            )
            AOSSecondaryButton(
                text = stringResource(R.string.action_back),
                onClick = onBackClick,
            )
        }
    }
}

@Composable
private fun CenteredNotice(
    text: String,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        AOSCard(modifier = Modifier.fillMaxWidth(0.6f)) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                if (actionText != null && onAction != null) {
                    Spacer(modifier = Modifier.height(AOSSpacing.md))
                    AOSPrimaryButton(text = actionText, onClick = onAction)
                }
            }
        }
    }
}

@Composable
private fun InputBar(
    enabled: Boolean,
    onSend: (String) -> Unit,
    onControl: (Byte) -> Unit,
) {
    var input by remember { mutableStateOf("") }

    val submit: () -> Unit = {
        if (input.isNotBlank()) {
            onSend(input.replace("\n", "\r") + "\r")
            input = ""
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(AOSSpacing.sm)) {
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            singleLine = true,
            placeholder = {
                Text(
                    text = stringResource(R.string.terminal_input_hint),
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { submit() }),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(AOSSpacing.sm)) {
            AOSSecondaryButton(
                text = stringResource(R.string.terminal_key_tab),
                enabled = enabled,
                onClick = { onControl(0x09) },
            )
            AOSSecondaryButton(
                text = stringResource(R.string.terminal_key_ctrl_c),
                enabled = enabled,
                onClick = { onControl(0x03) },
            )
            AOSSecondaryButton(
                text = stringResource(R.string.terminal_key_esc),
                enabled = enabled,
                onClick = { onControl(0x1B) },
            )
            Spacer(modifier = Modifier.weight(1f))
            AOSPrimaryButton(
                text = stringResource(R.string.terminal_send),
                enabled = enabled,
                onClick = submit,
            )
        }
    }
}
