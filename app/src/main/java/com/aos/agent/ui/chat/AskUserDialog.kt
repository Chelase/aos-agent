package com.aos.agent.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.aos.agent.R
import com.aos.agent.core.tools.AskUserAnswer
import com.aos.agent.ui.theme.AOSSpacing

/**
 * 结构化提问弹窗：问题 + 1-4 个选项 + 可选自由输入。
 * 超时/点外部关闭都算 dismissed，由通道统一处理。
 */
@Composable
fun AskUserDialog(
    pending: PendingQuestion,
    onAnswer: (AskUserAnswer) -> Unit,
    onDismiss: () -> Unit,
) {
    var freeText by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(pending.question) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                pending.options.forEachIndexed { index, option ->
                    ListItem(
                        headlineContent = { Text("${index + 1}. ${option.label}") },
                        supportingContent = option.description.takeIf { it.isNotBlank() }?.let { { Text(it) } },
                        modifier = Modifier.clickable {
                            onAnswer(
                                AskUserAnswer(
                                    selectedIndex = index,
                                    text = if (option.description.isNotBlank()) {
                                        "${option.label}：${option.description}"
                                    } else {
                                        option.label
                                    },
                                ),
                            )
                        },
                    )
                    if (index < pending.options.lastIndex) HorizontalDivider()
                }
                if (pending.allowFreeText) {
                    androidx.compose.material3.OutlinedTextField(
                        value = freeText,
                        onValueChange = { freeText = it },
                        label = { Text(stringResource(R.string.ask_user_free_text)) },
                        modifier = Modifier.fillMaxWidth().padding(top = AOSSpacing.md),
                        singleLine = true,
                    )
                }
            }
        },
        confirmButton = {
            if (pending.allowFreeText) {
                TextButton(
                    onClick = {
                        onAnswer(AskUserAnswer(selectedIndex = null, text = freeText.trim()))
                    },
                    enabled = freeText.isNotBlank(),
                ) {
                    Text(stringResource(R.string.ask_user_send))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.ask_user_skip))
            }
        },
    )
}
