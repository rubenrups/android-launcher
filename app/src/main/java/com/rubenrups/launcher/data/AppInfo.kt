package com.rubenrups.launcher.data

import android.content.ComponentName
import android.os.UserHandle

data class AppInfo(
    val label: String,
    val component: ComponentName,
    val user: UserHandle,
) {
    /** Stable identifier, used for persistence and list keys. */
    val key: String = component.flattenToString()
    val packageName: String get() = component.packageName

    /** Section letter for the A-Z scrubber; anything that is not A-Z collapses into '#'. */
    val letter: Char = label.firstOrNull { it.isLetterOrDigit() }
        ?.uppercaseChar()
        ?.takeIf { it in 'A'..'Z' }
        ?: '#'
}
