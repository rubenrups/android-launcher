package com.rubenrups.launcher.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class GlucoseUiTest {
    @Test
    fun mgdlIsShownAsIs() = assertEquals("180", formatGlucose(180, mmol = false))

    @Test
    fun mmolHasOneDecimal() {
        assertEquals("10.0", formatGlucose(180, mmol = true))
        assertEquals("5.5", formatGlucose(99, mmol = true))
    }

    @Test
    fun trendArrowsFollowTheLibreCodes() {
        assertEquals("↓", trendArrow(1))
        assertEquals("→", trendArrow(3))
        assertEquals("↑", trendArrow(5))
        assertEquals("", trendArrow(0))
    }

    @Test
    fun ageReadsLikeAPerson() {
        assertEquals("now", agoText(20_000))
        assertEquals("5 min ago", agoText(5 * 60_000L))
        assertEquals("2 h ago", agoText(130 * 60_000L))
        assertEquals("now", agoText(-1_000))
    }
}
