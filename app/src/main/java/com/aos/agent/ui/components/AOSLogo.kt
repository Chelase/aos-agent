package com.aos.agent.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aos.agent.ui.theme.AOSTheme
import com.aos.agent.ui.theme.AOSSizing
import kotlin.math.cos
import kotlin.math.sin

/**
 * 品牌标记：点火灯组（start lights）。环上六枚灯点，顶点常亮仪表蓝，
 * 其余熄灭——"就绪的机器"的仪表意象（design.md §5.9）。
 * 颜色一律经主题取值，日间/夜间自动换装；禁止直引色值常量（design.md §9.4）。
 * 用 Canvas 绘制而非位图，保证任意车机分辨率下不失真。
 */
@Composable
fun AOSLogo(
    modifier: Modifier = Modifier,
    size: Dp = AOSSizing.logoSize,
) {
    val accent = MaterialTheme.colorScheme.primary
    val accentDim = AOSTheme.accentDim
    val dimDot = AOSTheme.textTertiary
    Canvas(modifier = modifier.size(size)) {
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        val radius = this.size.minDimension / 2f
        val ringRadius = radius * 0.88f
        val dotRadius = radius * 0.10f

        // 六枚灯点：顶点（index 0）常亮，其余熄灭
        repeat(6) { index ->
            val angle = -Math.PI.toFloat() / 2f + index * Math.PI.toFloat() / 3f
            val dotCenter = Offset(
                x = center.x + ringRadius * cos(angle),
                y = center.y + ringRadius * sin(angle),
            )
            if (index == 0) {
                // 亮灯：光晕 + 实心强调色
                drawCircle(color = accentDim, radius = dotRadius * 2.2f, center = dotCenter)
                drawCircle(color = accent, radius = dotRadius, center = dotCenter)
            } else {
                // 熄灯：降对比实心
                drawCircle(
                    color = dimDot.copy(alpha = 0.45f),
                    radius = dotRadius * 0.8f,
                    center = dotCenter,
                )
            }
        }

        // 轴点：亮灯的回声
        drawCircle(color = accentDim, radius = radius * 0.20f, center = center)
        drawCircle(color = accent, radius = radius * 0.10f, center = center)
    }
}

/** 小尺寸标记，用于顶栏等紧凑位置。 */
@Composable
fun AOSLogoMark(modifier: Modifier = Modifier) {
    AOSLogo(modifier = modifier, size = 32.dp)
}
