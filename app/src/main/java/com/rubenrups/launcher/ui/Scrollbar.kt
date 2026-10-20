package com.rubenrups.launcher.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val ScrollbarColor = Color.White.copy(alpha = 0.45f)
private val ScrollbarWidth = 4.dp
private val MinThumbHeight = 24.dp

/**
 * Draws a thin always-visible scrollbar on the right edge. Apply it *before* `verticalScroll`
 * in the modifier chain so the bar stays put while the content moves underneath it.
 */
fun Modifier.verticalScrollbar(state: ScrollState): Modifier = drawWithContent {
    drawContent()
    val max = state.maxValue
    if (max > 0) {
        val viewport = size.height
        val thumb = (viewport * viewport / (viewport + max)).coerceAtLeast(MinThumbHeight.toPx())
        drawThumb(top = state.value.toFloat() / max * (viewport - thumb), height = thumb)
    }
}

/**
 * Same bar for lazy lists. A lazy list doesn't know its total height, so this estimates it from the
 * average height of the rows currently on screen: accurate enough for a position indicator.
 */
fun Modifier.verticalScrollbar(state: LazyListState): Modifier = drawWithContent {
    drawContent()
    val info = state.layoutInfo
    val visible = info.visibleItemsInfo
    if (visible.isNotEmpty() && visible.size < info.totalItemsCount) {
        val viewport = size.height
        val rowHeight = visible.sumOf { it.size }.toFloat() / visible.size
        val contentHeight = rowHeight * info.totalItemsCount
        val scrolled = visible.first().index * rowHeight - visible.first().offset
        val thumb = (viewport * viewport / contentHeight).coerceAtLeast(MinThumbHeight.toPx())
        val fraction = (scrolled / (contentHeight - viewport)).coerceIn(0f, 1f)
        drawThumb(top = fraction * (viewport - thumb), height = thumb)
    }
}

private fun DrawScope.drawThumb(top: Float, height: Float) {
    val width = ScrollbarWidth.toPx()
    drawRoundRect(
        color = ScrollbarColor,
        topLeft = Offset(size.width - width - 2.dp.toPx(), top),
        size = Size(width, height),
        cornerRadius = CornerRadius(width / 2),
    )
}
