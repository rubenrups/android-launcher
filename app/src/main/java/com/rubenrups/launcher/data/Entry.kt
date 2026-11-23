package com.rubenrups.launcher.data

const val FOLDER_PREFIX = "folder:"

enum class FolderSort(val label: String) {
    A_Z("A–Z"),
    Z_A("Z–A"),
    MANUAL("Manual"),
    RECENT("Most recent"),
    MOST_USED("Most used"),
}

/**
 * How often and how recently an app was opened from the launcher. [days] counts the launches per calendar day
 * (days since 1970) for the last [UsageWindow.KEEP_DAYS] days, which is what the "last 7 / 30 / 90 days" and
 * "fading" most-used modes need; [count] is the all-time total, including launches from before days were kept.
 */
data class AppUsage(val count: Int, val lastMs: Long, val days: Map<Long, Int> = emptyMap()) {
    companion object {
        /** [prev] plus one launch at [nowMs]; days older than the kept span are dropped. */
        fun record(prev: AppUsage?, nowMs: Long): AppUsage {
            val today = UsageWindow.day(nowMs)
            val kept = prev?.days.orEmpty().filterKeys { today - it < UsageWindow.KEEP_DAYS }
            return AppUsage((prev?.count ?: 0) + 1, nowMs, kept + (today to ((kept[today] ?: 0) + 1)))
        }
    }
}

data class AppFolder(
    val id: String,
    val name: String,
    /** Membership, and the display order when [sort] is [FolderSort.MANUAL]. */
    val appKeys: List<String>,
    val sort: FolderSort = FolderSort.A_Z,
    /** An emoji or symbol shown instead of the app count; null means "show the count". */
    val icon: String? = null,
) {
    /** Key used in the favourites list; app keys are component names so can never collide with this. */
    val key: String get() = FOLDER_PREFIX + id
}

/** Section letter for the A-Z scrubber; anything that is not A-Z collapses into '#'. */
fun letterOf(label: String): Char =
    label.firstOrNull { it.isLetterOrDigit() }
        ?.uppercaseChar()
        ?.takeIf { it in 'A'..'Z' }
        ?: '#'

/** One row in the drawer or favourites: either an app or a folder of apps. */
sealed interface Entry {
    val key: String
    val label: String
    val letter: Char
}

data class AppEntry(val app: AppInfo) : Entry {
    override val key: String get() = app.key
    override val label: String get() = app.label
    override val letter: Char get() = app.letter
}

data class FolderEntry(val folder: AppFolder, val apps: List<AppInfo>) : Entry {
    override val key: String get() = folder.key
    override val label: String get() = folder.name
    override val letter: Char get() = letterOf(folder.name)
}

/** Builds the displayable folder: installed apps only, in the folder's chosen order. */
fun AppFolder.resolve(byKey: Map<String, AppInfo>, usage: Map<String, AppUsage> = emptyMap()): FolderEntry {
    val apps = appKeys.mapNotNull { byKey[it] }
    val ordered = when (sort) {
        FolderSort.A_Z -> apps.sortedBy { it.label.lowercase() }
        FolderSort.Z_A -> apps.sortedByDescending { it.label.lowercase() }
        FolderSort.MANUAL -> apps
        // Apps never opened from the launcher sort last, alphabetically.
        FolderSort.RECENT -> apps.sortedWith(
            compareByDescending<AppInfo> { usage[it.key]?.lastMs ?: 0L }.thenBy { it.label.lowercase() },
        )
        FolderSort.MOST_USED -> apps.sortedWith(
            compareByDescending<AppInfo> { usage[it.key]?.count ?: 0 }
                .thenByDescending { usage[it.key]?.lastMs ?: 0L }
                .thenBy { it.label.lowercase() },
        )
    }
    return FolderEntry(this, ordered)
}
