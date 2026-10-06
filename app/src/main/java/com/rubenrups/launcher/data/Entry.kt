package com.rubenrups.launcher.data

const val FOLDER_PREFIX = "folder:"

data class AppFolder(val id: String, val name: String, val appKeys: List<String>) {
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
