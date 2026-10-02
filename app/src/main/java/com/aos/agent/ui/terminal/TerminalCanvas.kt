package com.aos.agent.ui.terminal

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import com.aos.agent.terminal.TerminalSession
import com.aos.agent.terminal.TextStyle.COLOR_INDEX_BACKGROUND
import com.aos.agent.terminal.TextStyle.COLOR_INDEX_CURSOR
import com.aos.agent.terminal.TextStyle.COLOR_INDEX_FOREGROUND
import com.aos.agent.ui.theme.AOSDataText
import kotlin.math.roundToInt

/**
 * 终端画布：Compose 逐行 `Text` 组成等宽字符网格，光标用 Canvas 叠一层色块。
 *
 * 行文本经 TerminalBuffer.getSelectedText 整行取出，`softWrap=false` 保持网格对齐；
 * 纵向拖拽翻回滚，新输出回到最新一行。
 * 不用 android.graphics.Paint.drawText：本机实测（AAOS API 36 x86_64 模拟器）同一画布上
 * drawRect 正常、drawText 静默不渲染（硬件层与软件层皆然，且会连带污染同窗口其他文字）。
 */
@Composable
fun TerminalCanvas(
    session: TerminalSession?,
    frameTick: Int,
    onSizeChanged: (cols: Int, rows: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer(cacheSize = 64)
    val cellWidth = remember(measurer) { measurer.measure("X", AOSDataText.terminal).size.width.toFloat() }
    val rowHeightPx = with(density) { AOSDataText.terminal.lineHeight.toPx() }
    val rowHeight = with(density) { rowHeightPx.toDp() }

    val surfaceColor = MaterialTheme.colorScheme.surface
    val foregroundColor = MaterialTheme.colorScheme.onSurface
    val cursorColor = MaterialTheme.colorScheme.primary

    // 仿真层调色板的底/前景/光标对齐主题 token；ANSI 0-255 保持标准调色板
    remember(session, surfaceColor, foregroundColor, cursorColor) {
        session?.emulator?.mColors?.mCurrentColors?.let { colors ->
            colors[COLOR_INDEX_BACKGROUND] = surfaceColor.toArgb()
            colors[COLOR_INDEX_FOREGROUND] = foregroundColor.toArgb()
            colors[COLOR_INDEX_CURSOR] = cursorColor.toArgb()
        }
        true
    }

    var scrollRows by remember { mutableIntStateOf(0) }
    // 新输出回到最新一行（Phase 1 不做「阅读历史时保持位置」）
    remember(frameTick, session) { scrollRows = 0; true }

    val emulator = session?.emulator
    val rowStyle = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = AOSDataText.terminal.fontSize,
        lineHeight = AOSDataText.terminal.lineHeight,
        color = foregroundColor,
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(surfaceColor)
            .onSizeChanged { size ->
                val cols = (size.width / cellWidth).toInt().coerceAtLeast(2)
                val rows = (size.height / rowHeightPx).toInt().coerceAtLeast(2)
                onSizeChanged(cols, rows)
            }
            .pointerInput(session) {
                detectDragGestures(
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val transcriptRows = emulator?.screen?.activeTranscriptRows ?: 0
                        scrollRows = (scrollRows - dragAmount.y / rowHeightPx).roundToInt()
                            .coerceIn(0, transcriptRows)
                    },
                )
            },
    ) {
        if (emulator == null) return@Box

        val columns = emulator.mColumns
        val screen = emulator.screen
        val topRow = -scrollRows
        val visibleRows = emulator.mRows

        Column(modifier = Modifier.fillMaxSize()) {
            for (index in 0 until visibleRows) {
                val row = topRow + index
                val text = screen.getSelectedText(0, row, columns, row + 1)
                Text(
                    text = text.orEmpty(),
                    style = rowStyle,
                    softWrap = false,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.height(rowHeight),
                )
            }
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            if (!emulator.shouldCursorBeVisible()) return@Canvas
            val cursorIndex = emulator.cursorRow - topRow
            if (cursorIndex < 0 || cursorIndex >= visibleRows) return@Canvas
            drawRect(
                color = cursorColor,
                topLeft = Offset(emulator.cursorCol * cellWidth, cursorIndex * rowHeightPx),
                size = Size(cellWidth, rowHeightPx),
            )
        }
    }
}
