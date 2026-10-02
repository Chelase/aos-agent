package com.aos.agent.ui.theme

import androidx.compose.ui.unit.dp

/**
 * 尺寸 token，对应 `.agent-rules/docs/原型/design.md` §4。
 * 基准网格 8dp；触控目标下限受驾驶安全约束，不得低于 56dp。
 */
object AOSSpacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
}

object AOSSizing {
    /** 横屏安全区内边距，避开系统状态栏/导航栏。 */
    val safeZone = 24.dp

    /** 卡片圆角与内边距（design.md §4.4，v2.0 收紧为 12dp 仪表窗圆角）。 */
    val cardCorner = 12.dp
    val cardPadding = 20.dp

    /** 交互控件最小高度，驾驶场景下限（design.md §1.2）。 */
    val touchTarget = 56.dp

    /** 状态标签（design.md §5.2）。 */
    val badgeHeight = 28.dp
    val badgeCorner = 14.dp
    val badgePadding = 12.dp

    /** 顶部状态栏（design.md §5.5）。 */
    val topBarHeight = 56.dp

    /** 数据行行高与行间距（design.md §5.3）。 */
    val dataRowSpacing = 12.dp

    /** 品牌 Logo 标准尺寸。 */
    val logoSize = 72.dp

    /** 边框描边宽度。 */
    val borderWidth = 1.dp
}
