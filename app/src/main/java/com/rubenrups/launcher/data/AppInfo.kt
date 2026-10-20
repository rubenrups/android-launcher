package com.rubenrups.launcher.data

import android.content.ComponentName
import android.os.UserHandle

/** Stable key for a pinned shortcut (a website saved from Chrome, a contact…); never collides with app keys. */
fun shortcutKey(packageName: String, shortcutId: String, profileId: Long): String =
    "shortcut:$packageName/$shortcutId" + if (profileId != 0L) "@$profileId" else ""

data class AppInfo(
    val label: String,
    val component: ComponentName,
    val user: UserHandle,
    /** False for apps that live in a work (or other) profile. */
    val isMainProfile: Boolean = true,
    val profileId: Long = 0L,
    /**
     * Set when this entry is a pinned shortcut rather than an app: the shortcut's id inside [packageName].
     * Everything else (favourites, folders, search, hiding, badges) then works on it like on any app.
     */
    val shortcutId: String? = null,
) {
    /**
     * Stable identifier, used for persistence and list keys. Main-profile app keys are unchanged from earlier
     * versions (so saved favourites and folders still match); other profiles get a suffix, because the same
     * app can be installed in both.
     */
    val key: String = when {
        shortcutId != null -> shortcutKey(component.packageName, shortcutId, if (isMainProfile) 0L else profileId)
        isMainProfile -> component.flattenToString()
        else -> "${component.flattenToString()}@$profileId"
    }
    val packageName: String get() = component.packageName

    val letter: Char = letterOf(label)
}
