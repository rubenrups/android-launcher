package com.rubenrups.launcher.data

/** What a "recent apps" spot shows: the apps opened last, or the ones opened most. */
object AppsSource {
    const val RECENT = "recent"
    const val MOST_USED = "most_used"
    /** Both at once: a recent row and, below it, a most-used row that skips the apps already shown above. */
    const val BOTH = "both"

    val options = listOf(RECENT to "Recently opened", MOST_USED to "Most used", BOTH to "Both, one row each")

    /** The title for a spot that shows this source. */
    fun title(source: String) = when (source) {
        MOST_USED -> "Most used apps"
        BOTH -> "Recent and most used apps"
        else -> "Recent apps"
    }
}

/**
 * The rows a recent-apps spot shows, as (title, keys) pairs with at most [count] apps each; [available] says which
 * apps can be shown (installed and not hidden). With [AppsSource.BOTH] there are two rows, and the most-used one
 * leaves out what the recent row already has, so the same app never appears twice.
 */
fun LauncherSettings.appSections(source: String, count: Int, available: (String) -> Boolean): List<Pair<String, List<String>>> {
    fun keys(src: String) = topAppKeys(src).filter(available)
    if (source != AppsSource.BOTH) {
        val keys = keys(source).take(count)
        return if (keys.isEmpty()) emptyList() else listOf(AppsSource.title(source) to keys)
    }
    val recent = keys(AppsSource.RECENT).take(count)
    val most = keys(AppsSource.MOST_USED).filter { it !in recent }.take(count)
    return listOfNotNull(
        recent.takeIf { it.isNotEmpty() }?.let { AppsSource.title(AppsSource.RECENT) to it },
        most.takeIf { it.isNotEmpty() }?.let { AppsSource.title(AppsSource.MOST_USED) to it },
    )
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
