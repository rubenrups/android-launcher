package com.rubenrups.launcher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MindfulTest {
    @Test
    fun noLimitIsNeverReached() {
        assertFalse(MindfulRule(dailyLimit = 0).limitReached(500))
    }

    @Test
    fun limitIsReachedAtTheLimit() {
        val rule = MindfulRule(dailyLimit = 3)
        assertFalse(rule.limitReached(2))
        assertTrue(rule.limitReached(3))
        assertTrue(rule.limitReached(7))
    }

    @Test
    fun onlyAStrictRuleClosesTheAppOverTheLimit() {
        assertTrue(MindfulRule(dailyLimit = 3, strict = false).canOpen(10))
        assertFalse(MindfulRule(dailyLimit = 3, strict = true).canOpen(3))
        assertTrue(MindfulRule(dailyLimit = 3, strict = true).canOpen(2))
    }

    @Test
    fun opensTodayComesFromThePerDayCounts() {
        val dayMs = 86_400_000L
        val now = 2000 * dayMs + 5_000
        val usage = AppUsage(count = 9, lastMs = now, days = mapOf(2000L to 4, 1999L to 5))
        assertEquals(4, usage.opensOnDayOf(now))
        assertEquals(0, (null as AppUsage?).opensOnDayOf(now))
        assertEquals(0, AppUsage(count = 9, lastMs = 0).opensOnDayOf(now))
    }
}
