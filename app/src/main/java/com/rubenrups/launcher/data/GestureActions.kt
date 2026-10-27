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
    const val LOCK = "action:lock"
    const val MENU = "action:menu"
    const val SETTINGS = "action:settings"
    const val FLASHLIGHT = "action:flashlight"
    const val RECENTS = "action:recents"
    const val QUICK_SETTINGS = "action:quicksettings"
    const val POWER_MENU = "action:powermenu"
    const val SCREENSHOT = "action:screenshot"

    // Only offered for swipes inside car mode.
    const val CLOSE = "action:close"
    const val NEXT = "action:next"
    const val PREVIOUS = "action:previous"
    const val PLAY_PAUSE = "action:playpause"

    /** In the order they are offered in settings. */
    val builtIn: List<Pair<String, String>> = listOf(
        DRAWER to "App drawer",
        SEARCH to "Search apps",
        NOTIFICATIONS to "Notification shade",
        DASHBOARD to "Car mode",
        MENU to "Home menu",
        SETTINGS to "Launcher settings",
        LOCK to "Lock phone",
        FLASHLIGHT to "Flashlight",
        RECENTS to "Recent apps",
        QUICK_SETTINGS to "Quick settings",
        POWER_MENU to "Power menu",
        SCREENSHOT to "Screenshot",
    )

    /** What a swipe inside car mode can do (besides launching an app). */
    val carBuiltIn: List<Pair<String, String>> = listOf(
        CLOSE to "Close car mode",
        NEXT to "Next track",
        PREVIOUS to "Previous track",
        PLAY_PAUSE to "Play / pause",
        DRAWER to "App drawer",
        SEARCH to "Search apps",
        NOTIFICATIONS to "Notification shade",
        SETTINGS to "Launcher settings",
        LOCK to "Lock phone",
        FLASHLIGHT to "Flashlight",
        RECENTS to "Recent apps",
        QUICK_SETTINGS to "Quick settings",
        POWER_MENU to "Power menu",
        SCREENSHOT to "Screenshot",
    )

    /** Actions that need the accessibility service switched on. */
    val needsAccessibility = setOf(LOCK, RECENTS, QUICK_SETTINGS, POWER_MENU, SCREENSHOT)

    /** True while a gesture still leads to the home menu or the launcher settings. */
    fun reachesSettings(s: LauncherSettings): Boolean =
        listOf(s.holdAction, s.doubleTapAction, s.tripleTapAction, s.swipeUp, s.swipeDown, s.swipeLeftApp, s.swipeRightApp)
            .any { it == MENU || it == SETTINGS }

    fun builtInLabel(key: String): String? =
        (builtIn + carBuiltIn).firstOrNull { it.first == key }?.second

    /** Older versions stored the swipe-down choice as an enum name; newer ones store the action key itself. */
    fun fromLegacy(stored: String): String = when (stored) {
        "NOTIFICATIONS" -> NOTIFICATIONS
        "SEARCH" -> SEARCH
        "DASHBOARD" -> DASHBOARD
        "NONE" -> NONE
        else -> stored
    }
}
