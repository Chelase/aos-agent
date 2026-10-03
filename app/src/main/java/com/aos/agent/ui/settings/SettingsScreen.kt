package com.aos.agent.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.aos.agent.R
import com.aos.agent.core.tools.mcp.McpServerConfig
import com.aos.agent.data.store.VoiceSettings
import com.aos.agent.ui.components.AOSCard
import com.aos.agent.ui.components.AOSDataRow
import com.aos.agent.ui.components.AOSLanguageSwitch
import com.aos.agent.ui.components.AOSRowDivider
import com.aos.agent.ui.components.AOSSectionHeader
import com.aos.agent.ui.components.AOSPrimaryButton
import com.aos.agent.ui.components.AOSSecondaryButton
import com.aos.agent.ui.theme.AOSDataText
import com.aos.agent.ui.theme.AOSSizing
import com.aos.agent.ui.theme.AOSSpacing
import com.aos.agent.ui.theme.AOSTheme
import com.aos.agent.ui.theme.aosLegendStyle

/**
 * 设置页：模型服务与 MCP 工具来源。
 *
 * 只放"需要写入的配置"。系统感知（版本/网络/电池）属首页系统面板，
 * 只读诊断属工程师模式——三页职责不重叠，避免同一份信息三个地方各写一遍。
 */
@Composable
fun SettingsScreen(
    currentLlm: Triple<String, String, String>?,
    mcpServers: List<McpServerConfig>,
    onBackClick: () -> Unit,
    onSaveModel: (baseUrl: String, model: String, apiKey: String) -> Unit,
    onSaveMcp: (name: String, url: String) -> Unit,
    onDeleteMcp: (name: String) -> Unit,
    darkTheme: Boolean = false,
    voiceSettings: VoiceSettings = VoiceSettings(),
    onToggleTheme: () -> Unit = {},
    onToggleTts: (Boolean) -> Unit = {},
    onToggleContinuous: (Boolean) -> Unit = {},
    onToggleBargeIn: (Boolean) -> Unit = {},
) {
    var baseUrl by remember { mutableStateOf(currentLlm?.first.orEmpty()) }
    var model by remember { mutableStateOf(currentLlm?.second.orEmpty()) }
    var apiKey by remember { mutableStateOf(currentLlm?.third.orEmpty()) }
    var mcpName by remember { mutableStateOf("fixture") }
    var mcpUrl by remember { mutableStateOf("http://localhost:9101/mcp") }
    var hint by remember { mutableStateOf<String?>(null) }
    var showKey by remember { mutableStateOf(false) }
    val savedLabel = stringResource(R.string.settings_saved)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(AOSSizing.safeZone),
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
                    text = stringResource(R.string.settings_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = stringResource(R.string.settings_subtitle),
                    style = aosLegendStyle,
                    color = AOSTheme.textTertiary,
                )
            }
            AOSSecondaryButton(text = stringResource(R.string.action_back), onClick = onBackClick)
        }

        Spacer(modifier = Modifier.height(AOSSpacing.lg))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(AOSSpacing.lg),
        ) {
            AOSSectionHeader(title = stringResource(R.string.settings_section_appearance))
            AOSCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.theme_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = AOSTheme.textTertiary,
                )
                Spacer(modifier = Modifier.height(AOSSpacing.sm))
                // 复用语言开关形态：当前主题 | 目标主题，点击切换（design.md §6.2）
                AOSLanguageSwitch(
                    currentLabel = stringResource(if (darkTheme) R.string.theme_dark else R.string.theme_light),
                    targetLabel = stringResource(if (darkTheme) R.string.theme_light else R.string.theme_dark),
                    description = stringResource(R.string.theme_switch_description),
                    onToggle = onToggleTheme,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            AOSSectionHeader(title = stringResource(R.string.settings_section_model))
            AOSCard(modifier = Modifier.fillMaxWidth()) {
                Field(
                    label = stringResource(R.string.chat_config_baseurl),
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                )
                Field(
                    label = stringResource(R.string.chat_config_model),
                    value = model,
                    onValueChange = { model = it },
                )
                Field(
                    label = stringResource(R.string.chat_config_key),
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    visual = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardType = KeyboardType.Password,
                )
                Text(
                    text = stringResource(R.string.settings_key_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = AOSTheme.textTertiary,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(AOSSpacing.sm)) {
                    AOSPrimaryButton(
                        text = stringResource(R.string.settings_save_model),
                        onClick = {
                            onSaveModel(baseUrl, model, apiKey)
                            hint = savedLabel
                        },
                        enabled = baseUrl.isNotBlank() && model.isNotBlank(),
                    )
                    AOSSecondaryButton(
                        text = if (showKey) {
                            stringResource(R.string.settings_hide_key)
                        } else {
                            stringResource(R.string.settings_show_key)
                        },
                        onClick = { showKey = !showKey },
                    )
                }
            }

            AOSSectionHeader(title = stringResource(R.string.settings_section_mcp))
            AOSCard(modifier = Modifier.fillMaxWidth()) {
                if (mcpServers.isEmpty()) {
                    Text(
                        text = stringResource(R.string.settings_mcp_empty),
                        style = MaterialTheme.typography.labelSmall,
                        color = AOSTheme.textTertiary,
                    )
                }
                mcpServers.forEach { server ->
                    AOSDataRow(
                        label = server.id,
                        value = server.url + " · " + server.permission.name.lowercase(),
                    )
                    Spacer(modifier = Modifier.height(AOSSpacing.xs))
                }
                Field(
                    label = stringResource(R.string.chat_mcp_name),
                    value = mcpName,
                    onValueChange = { mcpName = it },
                )
                Field(
                    label = stringResource(R.string.chat_mcp_url),
                    value = mcpUrl,
                    onValueChange = { mcpUrl = it },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(AOSSpacing.sm)) {
                    AOSPrimaryButton(
                        text = stringResource(R.string.settings_mcp_save),
                        onClick = {
                            onSaveMcp(mcpName, mcpUrl)
                            hint = savedLabel
                        },
                        enabled = mcpName.isNotBlank() && mcpUrl.isNotBlank(),
                    )
                    AOSSecondaryButton(
                        text = stringResource(R.string.settings_mcp_delete),
                        onClick = {
                            onDeleteMcp(mcpName)
                            hint = savedLabel
                        },
                        enabled = mcpServers.any { it.id == mcpName },
                    )
                }
            }

            hint?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = AOSTheme.statusSuccess,
                )
            }

            AOSSectionHeader(title = stringResource(R.string.settings_section_voice))
            AOSCard(modifier = Modifier.fillMaxWidth()) {
                VoiceSwitchRow(
                    label = stringResource(R.string.settings_voice_tts),
                    checked = voiceSettings.ttsEnabled,
                    onCheckedChange = onToggleTts,
                )
                AOSRowDivider()
                VoiceSwitchRow(
                    label = stringResource(R.string.settings_voice_continuous),
                    checked = voiceSettings.continuous,
                    onCheckedChange = onToggleContinuous,
                )
                AOSRowDivider()
                VoiceSwitchRow(
                    label = stringResource(R.string.settings_voice_barge_in),
                    checked = voiceSettings.bargeInEnabled,
                    onCheckedChange = onToggleBargeIn,
                )
                // 打断能力的硬件前提写在开关正下方，不留到用户自己踩坑
                Text(
                    text = stringResource(R.string.settings_voice_barge_in_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = AOSTheme.textTertiary,
                )
                Spacer(modifier = Modifier.height(AOSSpacing.sm))
                Text(
                    text = stringResource(R.string.settings_voice_privacy),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 语音开关行：整行可点，开关状态即结果，不额外做确认弹窗。 */
@Composable
private fun VoiceSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = AOSSizing.touchTarget)
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = AOSSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    visual: VisualTransformation = VisualTransformation.None,
    keyboardType: KeyboardType = KeyboardType.Uri,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = AOSSpacing.sm),
        label = { Text(text = label, style = MaterialTheme.typography.labelSmall) },
        singleLine = true,
        shape = RoundedCornerShape(AOSSizing.cardCorner),
        visualTransformation = visual,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Next),
    )
}
