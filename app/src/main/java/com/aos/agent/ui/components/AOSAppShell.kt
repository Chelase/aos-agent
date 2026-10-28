package com.aos.agent.ui.components

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.aos.agent.R
import com.aos.agent.ui.theme.*

/** 导航只描述目的地，不持有业务状态。MainActivity 仍是唯一装配点。 */
enum class AOSDestination(@param:StringRes val label: Int, val icon: Int) {
    Home(R.string.nav_home, R.drawable.ic_nav_home),
    Chat(R.string.action_chat, R.drawable.ic_nav_chat),
    Terminal(R.string.action_terminal, R.drawable.ic_nav_terminal),
    SystemPanel(R.string.nav_panel, R.drawable.ic_nav_panel),
    Settings(R.string.action_settings, R.drawable.ic_nav_settings),
    Engineer(R.string.nav_engineer, R.drawable.ic_nav_engineer),
}

/** 六页唯一的导航 implementation；驾驶遮罩由宿主在本 Module 之外覆盖。 */
@Composable
fun AOSAppShell(
    destination: AOSDestination,
    driveRestricted: Boolean,
    onNavigate: (AOSDestination) -> Unit,
    onCapture: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    BackHandler(enabled = destination != AOSDestination.Home) { onNavigate(AOSDestination.Home) }
    BoxWithConstraints(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        val railWidth = if (maxWidth < AOSSizing.paneMinWidth) AOSSizing.railCompactWidth else AOSSizing.railWidth
        Row(Modifier.fillMaxSize()) {
            NavigationRail(
                modifier = Modifier.width(railWidth).fillMaxHeight(),
                containerColor = AOSTheme.rail,
                header = {
                    AOSLogo(Modifier.padding(vertical = AOSSpacing.sm))
                },
            ) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    AOSDestination.entries.filter { it != AOSDestination.Engineer }.forEach { item ->
                        RailItem(item, destination, driveRestricted, onNavigate)
                    }
                }
                RailItem(AOSDestination.Engineer, destination, driveRestricted, onNavigate)
            }
            VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = AOSSizing.borderWidth)
            Box(Modifier.weight(1f).fillMaxHeight()) {
                content()
                // 截图是取证动作，悬在内容之上而非占版面；页面自己的 padding 已经让开了边缘
                if (onCapture != null) {
                    CaptureButton(
                        onClick = onCapture,
                        modifier = Modifier.align(Alignment.BottomEnd).padding(AOSSpacing.lg),
                    )
                }
            }
        }
    }
}

@Composable
private fun CaptureButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.capture_screen)
    Box(
        modifier = modifier
            .size(AOSSizing.touchTarget)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_capture),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(AOSSizing.iconSize),
        )
    }
}

@Composable
private fun RailItem(
    item: AOSDestination,
    selected: AOSDestination,
    restricted: Boolean,
    onNavigate: (AOSDestination) -> Unit,
) {
    val enabled = !restricted || item !in listOf(AOSDestination.Terminal, AOSDestination.Engineer)
    NavigationRailItem(
        selected = item == selected,
        onClick = { onNavigate(item) },
        enabled = enabled,
        modifier = Modifier.width(AOSSizing.railItem).heightIn(min = AOSSizing.touchTarget),
        icon = {
            Icon(painterResource(item.icon), contentDescription = null, modifier = Modifier.size(AOSSizing.iconSize))
        },
        label = {
            Text(stringResource(item.label), style = MaterialTheme.typography.labelMedium, maxLines = 1)
        },
        colors = NavigationRailItemDefaults.colors(
            selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
            selectedTextColor = MaterialTheme.colorScheme.onSurface,
            indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    )
}

/** 所有页面共享标题层级；actions 仅承载页面真实操作。 */
@Composable
fun AOSPageHeader(
    title: String,
    subtitle: String? = null,
    onBackClick: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = AOSSizing.topBarHeight),
        horizontalArrangement = Arrangement.spacedBy(AOSSpacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
            if (subtitle != null) Text(
                subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }
        actions()
        if (onBackClick != null) AOSSecondaryButton(stringResource(R.string.action_back), onBackClick)
    }
}

/** 双栏在空间不足或大字体时自然退为纵向；采集与业务生命周期由页面持有。 */
@Composable
fun AOSAdaptivePanes(
    modifier: Modifier = Modifier,
    first: @Composable () -> Unit,
    second: @Composable () -> Unit,
) {
    val fontScale = androidx.compose.ui.platform.LocalDensity.current.fontScale
    BoxWithConstraints(modifier) {
        if (maxWidth >= AOSSizing.paneMinWidth && fontScale <= 1.2f) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(AOSSpacing.lg)) {
                Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(AOSSpacing.lg)) { first() }
                Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(AOSSpacing.lg)) { second() }
            }
        } else {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(AOSSpacing.lg)) {
                first()
                second()
            }
        }
    }
}
