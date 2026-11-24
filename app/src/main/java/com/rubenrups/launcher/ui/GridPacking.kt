package com.rubenrups.launcher.ui

/** Where [packGrid] puts one item: the column it starts in and its top edge. */
internal data class GridSlot(val column: Int, val top: Int)

/**
 * Packs items, in order, into a grid of [columns] columns. Each item covers [spans] columns and is [heights] tall; it goes
 * where it can start highest, so the space beside a short item is filled by a later one instead of staying empty. An item
 * wider than one column starts on a column that is a multiple of its width, so wide items line up with each other. [gap]
 * is the space between items, vertically.
 */
internal fun packGrid(columns: Int, spans: List<Int>, heights: List<Int>, gap: Int): List<GridSlot> {
    val bottoms = IntArray(columns)
    return spans.indices.map { index ->
        val span = spans[index].coerceIn(1, columns)
        var bestColumn = 0
        var bestTop = Int.MAX_VALUE
        var column = 0
        while (column + span <= columns) {
            val top = (column until column + span).maxOf { bottoms[it] }
            if (top < bestTop) {
                bestTop = top
                bestColumn = column
            }
            column += span
        }
        for (c in bestColumn until bestColumn + span) bottoms[c] = bestTop + heights[index] + gap
        GridSlot(bestColumn, bestTop)
    }
}
