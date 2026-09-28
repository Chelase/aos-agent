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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.aos.agent.R
import com.aos.agent.runtime.RuntimeStatus
import com.aos.agent.ui.components.AOSCard
import com.aos.agent.ui.components.AOSPrimaryButton
import com.aos.agent.ui.components.AOSSectionHeader
import com.aos.agent.ui.components.AOSSecondaryButton
import com.aos.agent.ui.theme.AOSDataText
import com.aos.agent.ui.theme.AOSSizing
import com.aos.agent.ui.theme.AOSSpacing
import com.aos.agent.ui.theme.AOSTheme

/**
 * Agent 控制台：左边对话流与工具轨迹，右边运行状态与配置。
 *
 * 定位是调试与验证面，不是聊天产品——所以工具轨迹与"MCP 到底加载了几个工具"
 * 必须直接摊在屏幕上，而不是藏在日志里。
 */
@Composable
fun ChatScreen(
    entries: List<ChatEntry>,
    status: RuntimeStatus,
    busy: Boolean,
    onBackClick: () -> Unit,
    onSend: (String) -> Unit,
    onSaveModelConfig: (baseUrl: String, model: String, apiKey: String) -> Unit,
    onSaveMcpSource: (name: String, url: String) -> Unit,
) {
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

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

        Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
            Column(modifier = Modifier.weight(2.1f).fillMaxSize()) {
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
                InputRow(
                    input = input,
                    canSend = status.canSend && !busy,
                    blockedReason = status.blockedReason,
                    onInputChange = { input = it },
                    onSend = {
                        val query = input.trim()
                        if (query.isNotEmpty() && status.canSend && !busy) {
                            input = ""
                            onSend(query)
                        }
                    },
                )
            }

            Spacer(modifier = Modifier.width(AOSSpacing.lg))

            StatusAndConfigColumn(
                modifier = Modifier.weight(1f).fillMaxSize(),
                status = status,
                onSaveModelConfig = onSaveModelConfig,
                onSaveMcpSource = onSaveMcpSource,
            )
        }
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
                style = MaterialTheme.typography.labelSmall,
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
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
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

@Composable
private fun StatusAndConfigColumn(
    status: RuntimeStatus,
    modifier: Modifier = Modifier,
    onSaveModelConfig: (baseUrl: String, model: String, apiKey: String) -> Unit,
    onSaveMcpSource: (name: String, url: String) -> Unit,
) {
    var baseUrl by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var mcpName by remember { mutableStateOf("fixture") }
    var mcpUrl by remember { mutableStateOf("http://localhost:9101/mcp") }
    var savedHint by remember { mutableStateOf<String?>(null) }
    val none = stringResource(R.string.chat_none)
    // 点击回调不是 Composable 上下文，资源串要先取好
    val savedLabel = stringResource(R.string.chat_saved_hint)

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(AOSSpacing.md),
    ) {
        AOSSectionHeader(title = stringResource(R.string.chat_config_baseurl))
        AOSCard(modifier = Modifier.fillMaxWidth()) {
            ConfigField(stringResource(R.string.chat_config_baseurl), baseUrl, { baseUrl = it })
            ConfigField(stringResource(R.string.chat_config_model), model, { model = it })
            ConfigField(
                stringResource(R.string.chat_config_key),
                apiKey,
                { apiKey = it },
                password = true,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(AOSSpacing.sm)) {
                AOSSecondaryButton(
                    text = stringResource(R.string.chat_config_save),
                    onClick = {
                        onSaveModelConfig(baseUrl, model, apiKey)
                        savedHint = savedLabel
                    },
                )
                AOSSecondaryButton(
                    text = stringResource(R.string.chat_mcp_save),
                    onClick = {
                        onSaveMcpSource(mcpName, mcpUrl)
                        savedHint = savedLabel
                    },
                )
            }
            ConfigField(stringResource(R.string.chat_mcp_name), mcpName, { mcpName = it })
            ConfigField(stringResource(R.string.chat_mcp_url), mcpUrl, { mcpUrl = it })
            savedHint?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = AOSTheme.statusSuccess,
                )
            }
        }
        AOSSectionHeader(title = stringResource(R.string.chat_status_model))
        AOSCard(modifier = Modifier.fillMaxWidth()) {
            StatusLine(stringResource(R.string.chat_status_model), status.llmDescribe)
            StatusLine(
                stringResource(R.string.chat_status_skills),
                status.skills.joinToString("\n").ifEmpty { none },
            )
            StatusLine(
                stringResource(R.string.chat_status_tools),
                status.localTools.plus(status.mcpTools).joinToString("\n").ifEmpty { none },
            )
            StatusLine(
                stringResource(R.string.chat_status_vehicle),
                status.vehicleReadable.joinToString("\n").ifEmpty { none },
            )
            StatusLine(
                stringResource(R.string.chat_status_blocked),
                status.vehicleSkipped.joinToString("\n").ifEmpty { none },
            )
            StatusLine(
                stringResource(R.string.chat_status_mcp),
                status.mcpSources.joinToString("\n") { source ->
                    if (source.ok) "${source.serverId}: ${source.toolCount} 工具"
                    else "${source.serverId}: 失败 ${source.detail ?: ""}"
                }.ifEmpty { none },
            )
        }


    }
}

@Composable
private fun StatusLine(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = AOSSpacing.xs),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(AOSSpacing.sm),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = AOSTheme.textTertiary,
            modifier = Modifier.width(112.dp),
        )
        Text(
            text = value,
            style = AOSDataText.standard,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ConfigField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    password: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(text = label, style = MaterialTheme.typography.labelSmall) },
        singleLine = true,
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (password) KeyboardType.Password else KeyboardType.Uri,
            imeAction = ImeAction.Next,
        ),
    )
}
