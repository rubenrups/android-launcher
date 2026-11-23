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

class AppSectionsTest {
    private val settings = LauncherSettings(
        recents = listOf("a", "b", "c"),
        usage = mapOf(
            "z" to AppUsage(count = 50, lastMs = 10),
            "a" to AppUsage(count = 30, lastMs = 500),
            "y" to AppUsage(count = 20, lastMs = 20),
            "b" to AppUsage(count = 1, lastMs = 400),
        ),
    )

    @Test
    fun oneSourceGivesOneTitledRow() {
        val sections = settings.appSections(AppsSource.RECENT, 2) { true }
        assertEquals(listOf("Recent apps" to listOf("a", "b")), sections)
    }

    @Test
    fun bothGivesTwoRowsWithoutRepeats() {
        val sections = settings.appSections(AppsSource.BOTH, 2) { true }
        assertEquals(listOf("a", "b"), sections[0].second)
        // z and y lead the most used once a (the top one) is already in the recent row.
        assertEquals(listOf("z", "y"), sections[1].second)
        assertEquals("Most used apps", sections[1].first)
    }

    @Test
    fun appsThatCannotBeShownAreSkippedBeforeCounting() {
        val sections = settings.appSections(AppsSource.BOTH, 2) { it != "a" }
        assertEquals(listOf("b", "c"), sections[0].second)
        assertEquals(listOf("z", "y"), sections[1].second)
    }

    @Test
    fun nothingToShowGivesNoRows() = assertEquals(emptyList<Pair<String, List<String>>>(), settings.appSections(AppsSource.BOTH, 3) { false })
}
