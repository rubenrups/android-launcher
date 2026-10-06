package com.rubenrups.launcher.data

const val FOLDER_PREFIX = "folder:"

enum class FolderSort(val label: String) {
    A_Z("A–Z"),
    Z_A("Z–A"),
    MANUAL("Manual"),
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
fun AppFolder.resolve(byKey: Map<String, AppInfo>): FolderEntry {
    val apps = appKeys.mapNotNull { byKey[it] }
    val ordered = when (sort) {
        FolderSort.A_Z -> apps.sortedBy { it.label.lowercase() }
        FolderSort.Z_A -> apps.sortedByDescending { it.label.lowercase() }
        FolderSort.MANUAL -> apps
    }
    return FolderEntry(this, ordered)
}
