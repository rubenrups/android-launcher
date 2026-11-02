package com.rubenrups.launcher.data

/** How the app drawer orders its apps and folders. The keys are what is saved in settings. */
object DrawerSort {
    const val A_Z = "az"
    const val Z_A = "za"
    const val MOST_USED = "most_used"
    const val RECENT = "recent"

    const val FOLDERS_TOP = "top"
    const val FOLDERS_BOTTOM = "bottom"
    const val FOLDERS_MIXED = "mixed"

    /** Offered for both apps and folders, in this order. */
    val options = listOf(A_Z to "A–Z", Z_A to "Z–A", MOST_USED to "Most used", RECENT to "Recently used")

    val folderPositions = listOf(
        FOLDERS_TOP to "At the top",
        FOLDERS_BOTTOM to "At the bottom",
        FOLDERS_MIXED to "Mixed in with the apps",
    )

    /** The A-Z scrubber only makes sense when the list really runs alphabetically. */
    fun isAlphabetical(sort: String) = sort == A_Z || sort == Z_A

    private fun Entry.uses(usage: Map<String, AppUsage>): Int = when (this) {
        is AppEntry -> usage[key]?.count ?: 0
        is FolderEntry -> folder.appKeys.sumOf { usage[it]?.count ?: 0 }
    }

    private fun Entry.lastUsed(usage: Map<String, AppUsage>): Long = when (this) {
        is AppEntry -> usage[key]?.lastMs ?: 0L
        is FolderEntry -> folder.appKeys.maxOfOrNull { usage[it]?.lastMs ?: 0L } ?: 0L
    }

    private val alphabetical: Comparator<Entry> = compareBy<Entry>({ it.letter }, { it.label.lowercase() })

    fun comparator(sort: String, usage: Map<String, AppUsage>): Comparator<Entry> = when (sort) {
        Z_A -> alphabetical.reversed()
        MOST_USED -> compareByDescending<Entry> { it.uses(usage) }.thenByDescending { it.lastUsed(usage) }.then(alphabetical)
        RECENT -> compareByDescending<Entry> { it.lastUsed(usage) }.then(alphabetical)
        else -> alphabetical
    }

    /** Puts the drawer's folders and loose apps in order: each group by its own sort, folders on top, at the bottom or mixed in. */
    fun arrange(
        entries: List<Entry>,
        appSort: String,
        folderSort: String,
        folderPosition: String,
        usage: Map<String, AppUsage>,
    ): List<Entry> {
        val folders = entries.filterIsInstance<FolderEntry>()
        val apps = entries.filterIsInstance<AppEntry>()
        return when (folderPosition) {
            FOLDERS_MIXED -> entries.sortedWith(comparator(appSort, usage))
            FOLDERS_BOTTOM -> apps.sortedWith(comparator(appSort, usage)) + folders.sortedWith(comparator(folderSort, usage))
            else -> folders.sortedWith(comparator(folderSort, usage)) + apps.sortedWith(comparator(appSort, usage))
        }
    }
}
