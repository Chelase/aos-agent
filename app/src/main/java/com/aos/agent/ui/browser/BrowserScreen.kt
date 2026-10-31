package com.aos.agent.ui.browser

import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aos.agent.R
import com.aos.agent.ui.components.AOSCard
import com.aos.agent.ui.components.AOSPageHeader
import com.aos.agent.ui.components.AOSPrimaryButton
import com.aos.agent.ui.components.AOSSecondaryButton
import com.aos.agent.ui.components.AOSStatusBadge
import com.aos.agent.ui.components.AOSStatusTone
import com.aos.agent.ui.theme.AOSDataText
import com.aos.agent.ui.theme.AOSSizing
import com.aos.agent.ui.theme.AOSSpacing

/**
 * 内置浏览器页。
 *
 * 结构沿用既有页面的范式：卡片式顶栏 + 满宽内容区 + 底部操作条，
 * 全部走既有 token，不硬编码色值与字号。
 *
 * 行驶中由宿主的驾驶遮罩整体盖住（浏览器属 parked app，见 mechanisms/web-access.md）。
 */
@Composable
fun BrowserScreen(
    viewModel: BrowserViewModel,
    onBackClick: () -> Unit,
    initialUrl: String? = null,
    onUrlConsumed: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val addressBar by viewModel.addressBar.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // WebView 必须在页面存在期间活着；离开时显式释放 native 内存
    DisposableEffect(Unit) {
        onDispose { viewModel.releaseWebView() }
    }

    // 实例在页面首次进入时创建；重建 Activity 复用 ViewModel 里已有的实例
    LaunchedEffect(Unit) { viewModel.attachWebView(context) }

    // 从聊天点链接进来的：加载该 URL。URL 在 WebView 就绪后才加载，否则会丢
    LaunchedEffect(initialUrl, state) {
        val url = initialUrl ?: return@LaunchedEffect
        val ready = state as? BrowserUiState.Ready ?: return@LaunchedEffect
        if (ready.webView == null) return@LaunchedEffect
        viewModel.navigate(url)
        onUrlConsumed()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(AOSSizing.safeZone),
    ) {
        AOSPageHeader(
            title = stringResource(R.string.browser_title),
            subtitle = state.let { if (it is BrowserUiState.Ready) it.title.ifBlank { null } else null },
            onBackClick = onBackClick,
        )

        when (val current = state) {
            is BrowserUiState.Unavailable -> UnavailableNotice(current.reason)
            else -> {
                val ready = current as? BrowserUiState.Ready ?: BrowserUiState.Ready()
                BrowserBody(
                    viewModel = viewModel,
                    ready = ready,
                    addressBar = addressBar,
                )
            }
        }
    }
}

@Composable
private fun BrowserBody(
    viewModel: BrowserViewModel,
    ready: BrowserUiState.Ready,
    addressBar: String,
) {
    var editing by remember { mutableStateOf(false) }
    // draft 不依赖 addressBar 做 key：地址栏值变化（页面跳转/加载中）会把用户正在输入的内容冲掉
    var draft by remember { mutableStateOf("") }

    fun enterEditing() {
        draft = addressBar
        editing = true
    }

    Column(Modifier.fillMaxSize()) {
        // 地址栏：点击后进入编辑态，提交后导航
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = AOSSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AOSSpacing.sm),
        ) {
            if (editing) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.weight(1f).heightIn(min = AOSSizing.fieldHeight),
                    singleLine = true,
                    shape = RoundedCornerShape(AOSSizing.cardCorner),
                    textStyle = AOSDataText.standard,
                    placeholder = {
                        Text(stringResource(R.string.browser_address_hint), style = MaterialTheme.typography.bodySmall)
                    },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                        onGo = {
                            viewModel.navigate(draft)
                            editing = false
                        },
                    ),
                )
                AOSPrimaryButton(
                    text = stringResource(R.string.action_go),
                    onClick = {
                        viewModel.navigate(draft)
                        editing = false
                    },
                )
            } else {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = AOSSizing.fieldHeight)
                        .clip(RoundedCornerShape(AOSSizing.cardCorner))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .androidClickable { enterEditing() }
                        .padding(horizontal = AOSSpacing.md),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        text = addressBar.ifBlank { stringResource(R.string.browser_address_hint) },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (addressBar.isBlank()) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                AOSSecondaryButton(
                    text = stringResource(R.string.browser_edit_url),
                    onClick = { enterEditing() },
                )
            }
        }

        // 加载进度：只在加载时占位，避免页面跳动
        if (ready.loading) {
            LinearProgressIndicator(
                progress = { ready.progress / 100f },
                modifier = Modifier.fillMaxWidth().height(AOSSizing.borderWidth * 3),
            )
        } else {
            Spacer(Modifier.height(AOSSizing.borderWidth * 3))
        }

        // 错误条：加载失败给可读原因 + 重试，不白屏
        if (ready.error != null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = AOSSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AOSSpacing.sm),
            ) {
                AOSStatusBadge(ready.error, AOSStatusTone.ERROR, modifier = Modifier.weight(1f))
                AOSSecondaryButton(stringResource(R.string.action_retry), viewModel::reload)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(AOSSizing.cardCorner)),
        ) {
            val webView = ready.webView
            if (webView == null) {
                // 实例还没就绪：等 ViewModel 建好（attach 由 LaunchedEffect 触发）
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.browser_starting),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                WebViewPane(webView)
                // 还没加载过页面时 WebView 是黑屏，车机上突兀；用起始页盖住
                if (!ready.hasContent) {
                    StartPage(onOpen = viewModel::navigate)
                }
            }
        }

        // 底部导航条
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = AOSSpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(AOSSpacing.sm),
        ) {
            AOSSecondaryButton(
                text = stringResource(R.string.browser_back),
                onClick = viewModel::goBack,
                enabled = ready.canGoBack,
                modifier = Modifier.weight(1f),
            )
            AOSSecondaryButton(
                text = stringResource(R.string.browser_forward),
                onClick = viewModel::goForward,
                enabled = ready.canGoForward,
                modifier = Modifier.weight(1f),
            )
            AOSSecondaryButton(
                text = stringResource(R.string.browser_reload),
                onClick = viewModel::reload,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * 起始页：WebView 加载任何页面前的封面。
 *
 * 不用卡片堆砌——一行提示 + 一排文字入口就够了。触控目标按车机标准不低于 72dp。
 */
@Composable
private fun StartPage(onOpen: (String) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AOSSpacing.lg),
        ) {
            Text(
                text = stringResource(R.string.browser_home_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = stringResource(R.string.browser_home_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth().widthIn(max = 900.dp),
                horizontalArrangement = Arrangement.spacedBy(AOSSpacing.sm),
            ) {
                listOf(
                    stringResource(R.string.browser_home_duckduckgo) to "https://duckduckgo.com",
                    stringResource(R.string.browser_home_wikipedia) to "https://zh.wikipedia.org",
                    stringResource(R.string.browser_home_android) to "https://developer.android.com",
                ).forEach { (label, url) ->
                    AOSSecondaryButton(
                        text = label,
                        onClick = { onOpen(url) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/**
 * Compose ↔ WebView 桥接。
     *
     * `AndroidView` 的 factory 只创建一次；`update` 里不做事——WebView 是有状态的对象，
     * 重组时重建会丢页面状态。
     *
     * 实例由 ViewModel 创建（探测可用性时顺带就建好了），这里只负责把它交给 AndroidView。
     */
@Composable
private fun WebViewPane(webView: WebView) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = {
            webView.apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
            }
        },
    )
}

/** provider 缺失时的降级说明——能力缺失就明说，不留死控件。 */
@Composable
private fun UnavailableNotice(reason: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AOSCard(modifier = Modifier.fillMaxWidth(0.6f)) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.browser_unavailable),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(AOSSpacing.sm))
                Text(
                    text = reason,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 让 Box 可点击。用独立名字避免与 Material 的 clickable 扩展混淆。 */
private fun Modifier.androidClickable(onClick: () -> Unit): Modifier =
    this.clickable(onClick = onClick)
