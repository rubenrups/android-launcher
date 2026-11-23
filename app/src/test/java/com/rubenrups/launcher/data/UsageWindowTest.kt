package com.rubenrups.launcher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageWindowTest {
    private val dayMs = 86_400_000L
    private val now = 1000 * dayMs

    @Test
    fun recordCountsPerDayAndAllTime() {
        val once = AppUsage.record(null, now)
        val twice = AppUsage.record(once, now + 1000)
        assertEquals(2, twice.count)
        assertEquals(mapOf(1000L to 2), twice.days)
    }

    @Test
    fun recordDropsDaysOlderThanTheKeptSpan() {
        val old = AppUsage(count = 5, lastMs = 0, days = mapOf(1000L - 200 to 3, 1000L - 10 to 2))
        val next = AppUsage.record(old, now)
        assertEquals(setOf(990L, 1000L), next.days.keys)
        assertEquals(6, next.count)
    }

    @Test
    fun windowCountsOnlyLaunchesInsideIt() {
        val usage = AppUsage(count = 40, lastMs = now, days = mapOf(1000L to 2, 995L to 3, 960L to 10, 900L to 20))
        assertEquals(40.0, UsageWindow.score(usage, UsageWindow.ALL, now), 0.0)
        assertEquals(5.0, UsageWindow.score(usage, UsageWindow.DAYS_7, now), 0.0)
        assertEquals(5.0, UsageWindow.score(usage, UsageWindow.DAYS_30, now), 0.0)
        assertEquals(15.0, UsageWindow.score(usage, UsageWindow.DAYS_90, now), 0.0)
    }

    @Test
    fun fadingCountsRecentLaunchesMore() {
        val recent = AppUsage(count = 2, lastMs = now, days = mapOf(1000L to 2))
        val old = AppUsage(count = 2, lastMs = now, days = mapOf(1000L - 28 to 2))
        assertTrue(UsageWindow.score(recent, UsageWindow.FADE, now) > UsageWindow.score(old, UsageWindow.FADE, now))
        // 28 days is two half-lives: a quarter of the weight.
        assertEquals(0.5, UsageWindow.score(old, UsageWindow.FADE, now), 1e-9)
    }

    @Test
    fun mostUsedListHonoursTheWindow() {
        val settings = LauncherSettings(
            mostUsedWindow = UsageWindow.DAYS_7,
            usage = mapOf(
                "heavyButOld" to AppUsage(count = 500, lastMs = now - 60 * dayMs, days = mapOf(940L to 500)),
                "light" to AppUsage(count = 3, lastMs = now, days = mapOf(1000L to 3)),
                "legacyOnly" to AppUsage(count = 80, lastMs = now - 400 * dayMs),
            ),
        )
        // Only apps launched inside the window are listed; the heavy old one and the dateless legacy one drop out.
        assertEquals(listOf("light"), settings.topAppKeys(AppsSource.MOST_USED, now))
        assertEquals(
            listOf("heavyButOld", "legacyOnly", "light"),
            settings.copy(mostUsedWindow = UsageWindow.ALL).topAppKeys(AppsSource.MOST_USED, now),
        )
    }
}
