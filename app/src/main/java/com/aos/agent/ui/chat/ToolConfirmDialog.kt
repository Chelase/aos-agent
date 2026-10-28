package com.aos.agent.ui.chat

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.aos.agent.R

@Composable
fun ToolConfirmDialog(
    pending: PendingConfirm,
    onAllow: () -> Unit,
    onDeny: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDeny,
        title = { Text(stringResource(R.string.chat_tool_confirm_title, pending.toolName)) },
        text = { Text(stringResource(R.string.chat_tool_confirm_body, pending.argsSummary)) },
        confirmButton = {
            TextButton(onClick = onAllow) {
                Text(stringResource(R.string.chat_tool_confirm_allow))
            }
        },
        dismissButton = {
            TextButton(onClick = onDeny) {
                Text(stringResource(R.string.chat_tool_confirm_deny))
            }
        },
    )
}
