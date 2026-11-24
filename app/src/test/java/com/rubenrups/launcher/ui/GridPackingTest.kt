package com.rubenrups.launcher.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class GridPackingTest {
    @Test
    fun aShortCardIsFollowedByTheNextOneInItsColumn() {
        // Two columns: a tall card on the left, a short one on the right, then a third: it goes under the short one.
        val slots = packGrid(columns = 2, spans = listOf(1, 1, 1), heights = listOf(300, 100, 100), gap = 10)
        assertEquals(listOf(GridSlot(0, 0), GridSlot(1, 0), GridSlot(1, 110)), slots)
    }

    @Test
    fun aFullWidthCardWaitsForBothColumns() {
        val slots = packGrid(columns = 2, spans = listOf(1, 1, 2), heights = listOf(300, 100, 80), gap = 10)
        assertEquals(GridSlot(0, 310), slots[2])
    }

    @Test
    fun wideScreenFillsTheHoleBesideATallCard() {
        // Four columns: media (tall), glucose (short), then a two-column card and a one-column card.
        val slots = packGrid(columns = 4, spans = listOf(1, 1, 2, 1), heights = listOf(500, 180, 200, 150), gap = 10)
        assertEquals(GridSlot(0, 0), slots[0])
        assertEquals(GridSlot(1, 0), slots[1])
        // The two-column card starts on an even column: columns 2-3 are free from the top.
        assertEquals(GridSlot(2, 0), slots[2])
        // The last one goes under glucose, the lowest free column, instead of below everything.
        assertEquals(GridSlot(1, 190), slots[3])
    }

    @Test
    fun wideCardsStartOnEvenColumnsOnly() {
        val slots = packGrid(columns = 4, spans = listOf(1, 2), heights = listOf(100, 100), gap = 0)
        // Columns 1-2 would be free from the top, but a two-column card must start on column 0 or 2.
        assertEquals(GridSlot(2, 0), slots[1])
    }

    @Test
    fun anItemWiderThanTheGridIsClamped() {
        val slots = packGrid(columns = 2, spans = listOf(5), heights = listOf(50), gap = 0)
        assertEquals(listOf(GridSlot(0, 0)), slots)
    }
}
