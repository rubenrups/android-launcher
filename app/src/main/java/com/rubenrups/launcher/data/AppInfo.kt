package com.rubenrups.launcher.data

import android.content.ComponentName
import android.os.UserHandle

data class AppInfo(
    val label: String,
    val component: ComponentName,
    val user: UserHandle,
    /** False for apps that live in a work (or other) profile. */
    val isMainProfile: Boolean = true,
    val profileId: Long = 0L,
) {
    /**
     * Stable identifier, used for persistence and list keys. Main-profile keys are unchanged from earlier
     * versions (so saved favourites and folders still match); other profiles get a suffix, because the same
     * app can be installed in both.
     */
    val key: String =
        if (isMainProfile) component.flattenToString() else "${component.flattenToString()}@$profileId"
    val packageName: String get() = component.packageName

    val letter: Char = letterOf(label)
}
