package com.aos.agent.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import com.aos.agent.R
import com.aos.agent.core.web.UrlExtractor
import com.aos.agent.runtime.RuntimeStatus
import com.aos.agent.ui.components.*
import com.aos.agent.ui.theme.*
import com.aos.agent.ui.voice.*

/** v6 对话：气泡、结构化工具轨迹及真实会话摘要。 */
@Composable
fun ChatScreen(
    entries: List<ChatEntry>, status: RuntimeStatus, busy: Boolean,
    voiceState: VoiceUiState, onBackClick: () -> Unit,
    onSend: (String) -> Unit, onVoiceToggle: () -> Unit,
    onOpenLink: (String) -> Unit = {},
) {
    var input by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    LaunchedEffect(voiceState.partial) {
        if (voiceState.partial.isNotBlank()) input = voiceState.partial
    }
    LaunchedEffect(entries.size, busy) {
        if (entries.isNotEmpty()) listState.animateScrollToItem(entries.lastIndex)
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val showSummary = maxWidth >= AOSSizing.paneMinWidth && androidx.compose.ui.platform.LocalDensity.current.fontScale <= 1.2f
        Row(Modifier.fillMaxSize()) {
            Column(Modifier.weight(1f).fillMaxHeight().padding(AOSSizing.safeZone)) {
                AOSPageHeader(stringResource(R.string.chat_title),
                    subtitle = stringResource(if (status.canSend) R.string.chat_model_ready else R.string.chat_model_missing),
                    onBackClick = onBackClick)
                Spacer(Modifier.height(AOSSpacing.md))
                LazyColumn(
                    Modifier.weight(1f).fillMaxWidth(), state = listState,
                    verticalArrangement = Arrangement.spacedBy(AOSSpacing.md),
                    contentPadding = PaddingValues(vertical = AOSSpacing.md),
                ) {
                    if (entries.isEmpty()) item {
                        Column(Modifier.fillMaxWidth().padding(vertical = AOSSpacing.xxl),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(AOSSpacing.md)) {
                            AOSLogo()
                            Text(stringResource(R.string.chat_empty_title), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
                            Text(stringResource(R.string.chat_empty_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    items(entries) { ChatRow(it, onOpenLink) }
                    if (busy) item {
                        Row(horizontalArrangement = Arrangement.spacedBy(AOSSpacing.md), verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(AOSSizing.iconSize), strokeWidth = AOSSizing.borderWidth)
                            Text(stringResource(R.string.chat_sending), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (!voiceState.usable || voiceState.noticeRes != null || voiceState.partial.isNotBlank()) {
                    Surface(shape = RoundedCornerShape(AOSSizing.badgeCorner), color = MaterialTheme.colorScheme.surfaceContainer) {
                        VoiceStatusLine(voiceState, Modifier.padding(AOSSpacing.md))
                    }
                    Spacer(Modifier.height(AOSSpacing.sm))
                }
                if (!status.canSend) Text(stringResource(R.string.chat_configure_hint),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = AOSSpacing.sm))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AOSSpacing.md)) {
                    VoiceMicButton(voiceState.phase, voiceState.usable || voiceState.needsPermission, onVoiceToggle, iconOnly = true)
                    OutlinedTextField(
                        value = input, onValueChange = { input = it },
                        modifier = Modifier.weight(1f).heightIn(min = AOSSizing.fieldHeight),
                        placeholder = { Text(stringResource(R.string.chat_input_hint), style = MaterialTheme.typography.bodySmall) },
                        singleLine = true, shape = RoundedCornerShape(AOSSizing.touchTarget),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = {
                            if (input.isNotBlank() && status.canSend && !busy) { onSend(input.trim()); input = "" }
                        }),
                    )
                    AOSPrimaryButton(stringResource(R.string.chat_send), {
                        if (input.isNotBlank() && status.canSend && !busy) { onSend(input.trim()); input = "" }
                    }, enabled = input.isNotBlank() && status.canSend && !busy)
                }
            }
            if (showSummary) {
                VerticalDivider(thickness = AOSSizing.borderWidth, color = MaterialTheme.colorScheme.outlineVariant)
                Column(Modifier.width(AOSSizing.sidebarWidth).fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surface).padding(AOSSpacing.lg).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(AOSSpacing.md)) {
                    AOSSectionHeader(stringResource(R.string.chat_session))
                    AOSDataRow(stringResource(R.string.chat_session_messages), entries.count { it is ChatEntry.User || it is ChatEntry.Assistant }.toString())
                    AOSRowDivider()
                    AOSDataRow(stringResource(R.string.chat_session_tools), entries.count { it is ChatEntry.ToolCalled }.toString())
                    AOSRowDivider()
                    AOSDataRow(stringResource(R.string.chat_session_skills), status.skills.size.toString())
                    AOSRowDivider()
                    AOSDataRow(stringResource(R.string.chat_session_mcp), status.mcpTools.size.toString())
                    Spacer(Modifier.height(AOSSpacing.lg))
                    AOSSectionHeader(stringResource(R.string.settings_section_model))
                    Text(status.llmDescribe, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun ChatRow(entry: ChatEntry, onOpenLink: (String) -> Unit) {
    val user = entry is ChatEntry.User
    val tool = entry is ChatEntry.ToolCalled || entry is ChatEntry.ToolFinished
    val text = when (entry) {
        is ChatEntry.User -> entry.text
        is ChatEntry.Assistant -> entry.text
        is ChatEntry.Note -> entry.text
        is ChatEntry.ToolCalled -> "${entry.tool}  ${entry.args}"
        is ChatEntry.ToolFinished -> "${entry.tool} [${entry.outcome}] ${entry.payload.take(240)}"
    }
    val background = when {
        user -> MaterialTheme.colorScheme.primary
        tool -> MaterialTheme.colorScheme.surfaceContainer
        entry is ChatEntry.Note -> AOSTheme.warningSurface
        else -> MaterialTheme.colorScheme.surface
    }
    val foreground = when {
        user -> MaterialTheme.colorScheme.onPrimary
        entry is ChatEntry.Note -> AOSTheme.statusWarning
        else -> MaterialTheme.colorScheme.onSurface
    }
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = if (user) Alignment.CenterEnd else Alignment.CenterStart) {
        val bubbleWidth = maxWidth * if (tool) 0.94f else 0.7f
        // 只有助手回答里的链接可点：用户自己输入的文本点开没有意义，
        // 工具轨迹里的 URL 也不是给人点的
        val linkable = entry is ChatEntry.Assistant
        val bubbleModifier = Modifier.widthIn(max = bubbleWidth)
            .clip(RoundedCornerShape(
                topStart = AOSSizing.cardCorner, topEnd = AOSSizing.cardCorner,
                bottomStart = if (user) AOSSizing.cardCorner else AOSSpacing.sm,
                bottomEnd = if (user) AOSSpacing.sm else AOSSizing.cardCorner))
            .background(background).padding(horizontal = AOSSpacing.lg, vertical = AOSSpacing.md)
        if (linkable) {
            LinkifiedText(
                text = text,
                color = foreground,
                modifier = bubbleModifier,
                onOpenLink = onOpenLink,
            )
        } else {
            Text(text,
                style = if (tool) AOSDataText.standard else MaterialTheme.typography.bodyMedium,
                color = foreground,
                modifier = bubbleModifier,
            )
        }
    }
}

/**
 * 把文本里的 https 链接渲染成可点的。
 *
 * 链接必须是 https 公网地址——抽取时已过 [com.aos.agent.core.web.UrlExtractor]
 * 的守卫，明文 http 和内网地址根本不会进来。
 */
@Composable
private fun LinkifiedText(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    onOpenLink: (String) -> Unit,
) {
    val links = remember(text) { UrlExtractor.extract(text) }
    val linkColor = MaterialTheme.colorScheme.primary
    // 回调每次重组都是新实例。用 rememberUpdatedState 兜住，
    // 否则记住的 AnnotatedString 会一直用第一次那个回调。
    val currentOpen by rememberUpdatedState(onOpenLink)
    val annotated = remember(text, links, linkColor) {
        buildAnnotatedString {
            var cursor = 0
            links.forEach { link ->
                if (link.start > cursor) append(text.substring(cursor, link.start))
                withLink(LinkAnnotation.Url(
                    url = link.url,
                    styles = TextLinkStyles(
                        style = SpanStyle(
                            color = linkColor,
                            textDecoration = TextDecoration.Underline,
                        ),
                    ),
                    // 默认行为是交给系统开外部浏览器；车机上应该在内置浏览器里打开
                    linkInteractionListener = LinkInteractionListener { annotation ->
                        val target = (annotation as? LinkAnnotation.Url)?.url
                        if (target != null) currentOpen(target)
                    },
                )) {
                    append(link.url)
                }
                cursor = link.end
            }
            if (cursor < text.length) append(text.substring(cursor))
        }
    }
    Text(
        text = annotated,
        style = MaterialTheme.typography.bodyMedium,
        color = color,
        modifier = modifier,
    )
}
