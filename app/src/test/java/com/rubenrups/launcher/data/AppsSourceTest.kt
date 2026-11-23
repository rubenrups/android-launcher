package com.rubenrups.launcher.data

import org.junit.Assert.assertEquals
import org.junit.Test

class AppsSourceTest {
    private val settings = LauncherSettings(
        recents = listOf("c", "a", "b"),
        usage = mapOf(
            "a" to AppUsage(count = 9, lastMs = 100),
            "b" to AppUsage(count = 20, lastMs = 50),
            "c" to AppUsage(count = 9, lastMs = 300),
        ),
    )

    @Test
    fun recentKeepsTheRecentsOrder() = assertEquals(listOf("c", "a", "b"), settings.topAppKeys(AppsSource.RECENT))

    @Test
    fun mostUsedSortsByCountThenNewest() = assertEquals(listOf("b", "c", "a"), settings.topAppKeys(AppsSource.MOST_USED))

    @Test
    fun unknownSourceFallsBackToRecent() = assertEquals(settings.recents, settings.topAppKeys("anything"))

    @Test
    fun titleFollowsTheSource() {
        assertEquals("Most used apps", AppsSource.title(AppsSource.MOST_USED))
        assertEquals("Recent apps", AppsSource.title(AppsSource.RECENT))
    }
}
