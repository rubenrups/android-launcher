package com.rubenrups.launcher.data

import android.content.pm.ShortcutInfo

/** One of an app's own quick actions, as shown in its long-press menu. */
data class AppShortcut(val label: String, val info: ShortcutInfo)
