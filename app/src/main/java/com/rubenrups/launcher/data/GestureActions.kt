package com.rubenrups.launcher.data

/**
 * What a home-screen swipe can do. A gesture setting holds one of these keys, or the key of an app to launch;
 * an empty string means nothing.
 */
object GestureActions {
    const val NONE = ""
    const val DRAWER = "action:drawer"
    const val SEARCH = "action:search"
    const val NOTIFICATIONS = "action:notifications"
    const val DASHBOARD = "action:dashboard"

    /** In the order they are offered in settings. */
    val builtIn: List<Pair<String, String>> = listOf(
        DRAWER to "App drawer",
        SEARCH to "Search apps",
        NOTIFICATIONS to "Notification shade",
        DASHBOARD to "Dashboard",
    )

    fun builtInLabel(key: String): String? = builtIn.firstOrNull { it.first == key }?.second

    /** Older versions stored the swipe-down choice as an enum name; newer ones store the action key itself. */
    fun fromLegacy(stored: String): String = when (stored) {
        "NOTIFICATIONS" -> NOTIFICATIONS
        "SEARCH" -> SEARCH
        "DASHBOARD" -> DASHBOARD
        "NONE" -> NONE
        else -> stored
    }
}
