package com.rubenrups.launcher.data

/** What a "recent apps" spot shows: the apps opened last, or the ones opened most. */
object AppsSource {
    const val RECENT = "recent"
    const val MOST_USED = "most_used"

    val options = listOf(RECENT to "Recently opened", MOST_USED to "Most used")

    /** The title for a spot that shows this source. */
    fun title(source: String) = if (source == MOST_USED) "Most used apps" else "Recent apps"
}

/**
 * The app keys for a recent-apps spot, best first: newest first for [AppsSource.RECENT], most opened first (the
 * newest of equals first) for [AppsSource.MOST_USED]. Callers keep only the apps that are installed and visible.
 */
fun LauncherSettings.topAppKeys(source: String): List<String> =
    if (source == AppsSource.MOST_USED) {
        usage.entries.sortedWith(compareByDescending<Map.Entry<String, AppUsage>> { it.value.count }.thenByDescending { it.value.lastMs }).map { it.key }
    } else {
        recents
    }
