package com.aos.agent.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aos.agent.ui.theme.AOSAccentPrimary
import com.aos.agent.ui.theme.AOSAccentSecondary
import com.aos.agent.ui.theme.AOSSizing
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 品牌标记：双层六边形 + 中心节点 + 辐射连线，对应设计原型 Screen 1 的 Logo。
 * 用 Canvas 绘制而非位图，保证任意车机分辨率下不失真。
 */
@Composable
fun AOSLogo(
    modifier: Modifier = Modifier,
    size: Dp = AOSSizing.logoSize,
) {
    Canvas(modifier = modifier.size(size)) {
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        val outerRadius = this.size.minDimension / 2f
        val innerRadius = outerRadius * 0.58f

        val outerVertices = hexagonVertices(center, outerRadius)

        // 外层六边形：品牌主色描边
        drawPath(
            path = polygonPath(outerVertices),
            color = AOSAccentPrimary,
            style = Stroke(width = outerRadius * 0.07f),
        )

        // 内层六边形：次强调色，旋转 30° 形成层次
        drawPath(
            path = polygonPath(hexagonVertices(center, innerRadius, rotationDegrees = 30f)),
            color = AOSAccentSecondary.copy(alpha = 0.55f),
            style = Stroke(width = outerRadius * 0.045f),
        )

        // 中心到各顶点的连线：数据节点意象
        outerVertices.forEach { vertex ->
            drawLine(
                brush = Brush.linearGradient(
                    colors = listOf(
                        AOSAccentPrimary.copy(alpha = 0.65f),
                        AOSAccentPrimary.copy(alpha = 0.08f),
                    ),
                    start = center,
                    end = vertex,
                ),
                start = center,
                end = vertex,
                strokeWidth = outerRadius * 0.025f,
            )
            drawCircle(
                color = AOSAccentPrimary,
                radius = outerRadius * 0.055f,
                center = vertex,
            )
        }

        // 中心节点：实心 + 光晕
        drawCircle(
            color = AOSAccentPrimary.copy(alpha = 0.18f),
            radius = outerRadius * 0.26f,
            center = center,
        )
        drawCircle(
            color = AOSAccentPrimary,
            radius = outerRadius * 0.12f,
            center = center,
        )
    }
}

private fun hexagonVertices(
    center: Offset,
    radius: Float,
    rotationDegrees: Float = 0f,
): List<Offset> {
    val rotation = rotationDegrees * PI.toFloat() / 180f
    return List(6) { index ->
        // 从正上方起画，顶点角度间隔 60°
        val angle = rotation - PI.toFloat() / 2f + index * PI.toFloat() / 3f
        Offset(
            x = center.x + radius * cos(angle),
            y = center.y + radius * sin(angle),
        )
    }
}

private fun polygonPath(vertices: List<Offset>): Path = Path().apply {
    vertices.forEachIndexed { index, vertex ->
        if (index == 0) moveTo(vertex.x, vertex.y) else lineTo(vertex.x, vertex.y)
    }
    close()
}

/** 小尺寸标记，用于顶栏等紧凑位置。 */
@Composable
fun AOSLogoMark(modifier: Modifier = Modifier) {
    AOSLogo(modifier = modifier, size = 32.dp)
}
