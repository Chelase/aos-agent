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
import androidx.compose.material3.Checkbox
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
import androidx.annotation.StringRes
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.aos.agent.R
import com.aos.agent.core.tools.mcp.McpServerConfig
import com.aos.agent.core.update.InstallGate
import com.aos.agent.core.update.UpdateFailure
import com.aos.agent.core.update.UpdateOutcome
import com.aos.agent.core.voice.WakeVariant
import com.aos.agent.core.voice.WakeWordPhrases
import com.aos.agent.data.store.VoiceSettings
import com.aos.agent.system.voice.WakeModelState
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
    wakeModelState: WakeModelState = WakeModelState.Missing,
    onToggleTheme: () -> Unit = {},
    onToggleTts: (Boolean) -> Unit = {},
    onToggleContinuous: (Boolean) -> Unit = {},
    onToggleBargeIn: (Boolean) -> Unit = {},
    onToggleWake: (Boolean) -> Unit = {},
    onSaveAgentName: (String) -> Unit = {},
    onToggleWakeVariant: (WakeVariant, Boolean) -> Unit = { _, _ -> },
    onDownloadWakeModel: () -> Unit = {},
    appVersionLabel: String = "",
    updateChecking: Boolean = false,
    updateOutcome: UpdateOutcome? = null,
    installGate: InstallGate = InstallGate.PERMISSION_NEEDED,
    downloadPercent: Int? = null,
    @StringRes installMessageRes: Int? = null,
    onCheckUpdate: () -> Unit = {},
    onDownloadAndInstall: () -> Unit = {},
    onOpenInstallPermissionSettings: () -> Unit = {},
) {
    var baseUrl by remember { mutableStateOf(currentLlm?.first.orEmpty()) }
    var model by remember { mutableStateOf(currentLlm?.second.orEmpty()) }
    var apiKey by remember { mutableStateOf(currentLlm?.third.orEmpty()) }
    var mcpName by remember { mutableStateOf("fixture") }
    var mcpUrl by remember { mutableStateOf("http://localhost:9101/mcp") }
    var hint by remember { mutableStateOf<String?>(null) }
    var agentNameDraft by remember(voiceSettings.agentName) { mutableStateOf(voiceSettings.agentName) }
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
                AOSRowDivider()
                // Agent 名字 → 唤醒词。名字与说法两行都直接摊开，不折叠进二级页
                Field(
                    label = stringResource(R.string.settings_agent_name),
                    value = agentNameDraft,
                    onValueChange = { agentNameDraft = it },
                    keyboardType = KeyboardType.Text,
                )
                AOSPrimaryButton(
                    text = stringResource(R.string.settings_agent_name_save),
                    onClick = {
                        onSaveAgentName(agentNameDraft)
                        hint = savedLabel
                    },
                    enabled = agentNameDraft.isNotBlank() && agentNameDraft.trim() != voiceSettings.agentName,
                )
                Spacer(modifier = Modifier.height(AOSSpacing.xs))
                Text(
                    text = stringResource(R.string.settings_agent_name_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = AOSTheme.textTertiary,
                )
                AOSRowDivider()
                Text(
                    text = stringResource(R.string.settings_wake_variants),
                    style = MaterialTheme.typography.labelSmall,
                    color = AOSTheme.textTertiary,
                )
                WakeVariant.entries.forEach { variant ->
                    WakeVariantRow(
                        label = when (variant) {
                            WakeVariant.HELLO -> stringResource(
                                R.string.settings_wake_variant_hello,
                                voiceSettings.agentName,
                            )

                            WakeVariant.HI -> stringResource(
                                R.string.settings_wake_variant_hi,
                                voiceSettings.agentName,
                            )

                            WakeVariant.BARE -> stringResource(
                                R.string.settings_wake_variant_bare,
                                voiceSettings.agentName,
                            )
                        },
                        checked = variant in voiceSettings.wakeVariants,
                        onCheckedChange = { onToggleWakeVariant(variant, it) },
                    )
                }
                // 裸名会把日常提到名字都当成叫它，勾选时把代价写在眼前
                if (WakeVariant.BARE in voiceSettings.wakeVariants) {
                    Text(
                        text = stringResource(R.string.settings_wake_variant_bare_risk),
                        style = MaterialTheme.typography.labelSmall,
                        color = AOSTheme.statusWarning,
                    )
                }
                AOSRowDivider()
                val wakeReady = wakeModelState is WakeModelState.Ready
                val wakePhrases = WakeWordPhrases.build(voiceSettings.agentName, voiceSettings.wakeVariants)
                VoiceSwitchRow(
                    label = stringResource(R.string.settings_voice_wake, WakeWordPhrases.displayOf(wakePhrases)),
                    checked = voiceSettings.wakeWordEnabled && wakeReady,
                    enabled = wakeReady,
                    onCheckedChange = onToggleWake,
                )
                // 开关不可用时先说缺什么，再给补上的入口，而不是只留一个灰开关
                if (!wakeReady) {
                    Text(
                        text = stringResource(
                            if (wakeModelState is WakeModelState.Failed) {
                                (wakeModelState as WakeModelState.Failed).reasonRes
                            } else {
                                R.string.wake_needs_model
                            },
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = AOSTheme.textTertiary,
                    )
                }
                when (val model = wakeModelState) {
                    is WakeModelState.Downloading -> Text(
                        text = if (model.percent >= 0) {
                            stringResource(R.string.wake_model_downloading, model.percent)
                        } else {
                            stringResource(R.string.wake_model_downloading_unknown)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )

                    WakeModelState.Missing, is WakeModelState.Failed -> AOSSecondaryButton(
                        text = stringResource(
                            if (model is WakeModelState.Failed) R.string.wake_model_retry
                            else R.string.wake_model_download,
                        ),
                        onClick = onDownloadWakeModel,
                    )

                    WakeModelState.Ready -> Text(
                        text = stringResource(R.string.wake_model_ready),
                        style = MaterialTheme.typography.labelSmall,
                        color = AOSTheme.statusSuccess,
                    )
                }
                Spacer(modifier = Modifier.height(AOSSpacing.sm))
                Text(
                    text = stringResource(R.string.settings_voice_wake_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(AOSSpacing.sm))
                Text(
                    text = stringResource(R.string.settings_voice_privacy),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            AOSSectionHeader(title = stringResource(R.string.settings_section_update))
            AOSCard(modifier = Modifier.fillMaxWidth()) {
                AOSDataRow(
                    label = stringResource(R.string.update_current_version),
                    value = appVersionLabel,
                )
                AOSRowDivider()
                Spacer(modifier = Modifier.height(AOSSpacing.sm))
                AOSPrimaryButton(
                    text = stringResource(
                        if (updateChecking) R.string.update_checking else R.string.update_check,
                    ),
                    onClick = onCheckUpdate,
                    enabled = !updateChecking,
                )
                when (val outcome = updateOutcome) {
                    null -> Unit

                    is UpdateOutcome.UpToDate -> UpdateResultLine(
                        text = stringResource(R.string.update_up_to_date),
                        color = AOSTheme.statusSuccess,
                    )

                    is UpdateOutcome.Available -> {
                        UpdateResultLine(
                            text = stringResource(R.string.update_available, outcome.manifest.versionName),
                            color = MaterialTheme.colorScheme.primary,
                        )
                        UpdateResultLine(
                            text = stringResource(R.string.update_download_hint, outcome.manifest.apkUrl),
                            color = AOSTheme.textTertiary,
                        )
                        Spacer(modifier = Modifier.height(AOSSpacing.sm))
                        val percent = downloadPercent
                        when {
                            percent != null -> UpdateResultLine(
                                text = stringResource(R.string.update_downloading, percent),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )

                            installGate == InstallGate.READY -> AOSPrimaryButton(
                                text = stringResource(R.string.update_install),
                                onClick = onDownloadAndInstall,
                            )

                            // 装不了的时候给的是"为什么 + 下一步"，不是一个灰按钮
                            installGate == InstallGate.PERMISSION_NEEDED -> {
                                UpdateResultLine(
                                    text = stringResource(R.string.update_gate_permission),
                                    color = AOSTheme.statusWarning,
                                )
                                AOSSecondaryButton(
                                    text = stringResource(R.string.update_gate_open_settings),
                                    onClick = onOpenInstallPermissionSettings,
                                )
                            }

                            else -> UpdateResultLine(
                                text = stringResource(R.string.update_gate_no_installer),
                                color = AOSTheme.statusWarning,
                            )
                        }
                    }

                    is UpdateOutcome.Failed -> UpdateResultLine(
                        text = stringResource(updateFailureRes(outcome.failure)),
                        color = AOSTheme.statusError,
                    )
                }
                installMessageRes?.let {
                    UpdateResultLine(
                        text = stringResource(it),
                        color = AOSTheme.statusError,
                    )
                }
            }
        }
    }
}

@StringRes
private fun updateFailureRes(failure: UpdateFailure): Int = when (failure) {
    UpdateFailure.NETWORK -> R.string.update_err_network
    UpdateFailure.BAD_MANIFEST -> R.string.update_err_manifest
    UpdateFailure.UNTRUSTED_SOURCE -> R.string.update_err_untrusted
}

/** 更新结果行：结论、下载地址、失败原因都走这里，保证每次检查都有看得见的回音。 */
@Composable
private fun UpdateResultLine(text: String, color: androidx.compose.ui.graphics.Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = AOSSpacing.xs),
    )
}

/** 唤醒说法勾选行：整行可点，标签就是这句话本身（要念出口的，不随界面语言翻译）。 */
@Composable
private fun WakeVariantRow(
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
        horizontalArrangement = Arrangement.spacedBy(AOSSpacing.sm),
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** 语音开关行：整行可点，开关状态即结果，不额外做确认弹窗。禁用时由调用方在下方给原因。 */
@Composable
private fun VoiceSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = AOSSizing.touchTarget)
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(vertical = AOSSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else AOSTheme.textTertiary,
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
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
