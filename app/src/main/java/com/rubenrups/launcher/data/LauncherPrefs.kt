package com.rubenrups.launcher.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

enum class WebSearchEngine(val label: String, val urlTemplate: String?) {
    OFF("Off", null),
    SYSTEM("System default", null),
    GOOGLE("Google", "https://www.google.com/search?q=%s"),
    DUCKDUCKGO("DuckDuckGo", "https://duckduckgo.com/?q=%s"),
}

/** A contact for a car-mode call button. */
data class CallContact(val name: String, val number: String)

/** A saved place for the car-mode navigation buttons. */
data class Destination(val name: String, val address: String, val icon: String = "📍")

/** The items that can sit under the time on the home screen, in their default order. */
object HeaderItems {
    const val DATE = "date"
    const val BATTERY = "battery"
    const val WEATHER = "weather"
    const val GLUCOSE = "glucose"
    const val EVENT = "event"

    val all: List<Pair<String, String>> = listOf(
        DATE to "Date",
        BATTERY to "Battery",
        WEATHER to "Weather",
        GLUCOSE to "Glucose",
        EVENT to "Next calendar event",
    )
}

/** The cards of the car-mode screen: each can be switched off and reordered. */
object DashboardModules {
    const val GLUCOSE = "glucose"
    const val NAVIGATION = "navigation"
    const val MEDIA = "media"
    const val WEATHER = "weather"
    const val QUICK = "quick"
    const val STATS = "stats"
    const val APPS = "apps"
    const val CALLS = "calls"
    const val CLOCK = "clock"
    const val RECENTS = "recents"
    const val NOTIFICATIONS = "notifications"

    /** Default order, and the names shown in settings. */
    val all: List<Pair<String, String>> = listOf(
        CLOCK to "Clock",
        GLUCOSE to "Glucose",
        MEDIA to "Media player",
        NAVIGATION to "Navigation",
        NOTIFICATIONS to "Notifications",
        APPS to "Quick launch apps",
        RECENTS to "Recent apps",
        CALLS to "Call buttons",
        WEATHER to "Hourly weather",
        QUICK to "Quick settings",
        STATS to "Battery, weather and next event",
    )
}

data class LauncherSettings(
    val favourites: List<String> = emptyList(),
    val hidden: Set<String> = emptySet(),
    val showClock: Boolean = true,
    val showNotifications: Boolean = true,
    val showMedia: Boolean = true,
    val favouriteTextSp: Int = 22,
    /** Horizontal: right-aligned text and icons. Independent of [anchorBottom]. */
    val alignEnd: Boolean = false,
    /** Vertical: pin the favourites list to the bottom of the screen (thumb reach) instead of the top. */
    val anchorBottom: Boolean = false,
    val showIcons: Boolean = true,
    val showSearchButton: Boolean = true,
    /** What each swipe on the home screen does: a [GestureActions] key or an app key; empty = nothing. */
    val swipeUp: String = GestureActions.DRAWER,
    val swipeDown: String = GestureActions.NOTIFICATIONS,
    val iconPack: String? = null,
    val folders: List<AppFolder> = emptyList(),
    val showCalendar: Boolean = false,
    val showWeather: Boolean = false,
    /** "stacked", "side" (details beside the time) or "inline" (one wrapping row). */
    val headerLayout: String = "stacked",
    val headerShowDate: Boolean = true,
    val headerShowBattery: Boolean = true,
    val clockSizeSp: Int = 56,
    /** Order of the items under the time ([HeaderItems] keys); missing ones are added at the end. */
    val headerOrder: List<String> = HeaderItems.all.map { it.first },
    /** "system", "24h" or "12h". */
    val timeFormat: String = "system",
    /** "short" (Mon, Oct 19), "long" (Monday 19 October), "day_month" (19 Oct) or "numeric" (the phone's short date). */
    val dateFormat: String = "short",
    val headerTextSp: Int = 16,
    /** "light", "normal" or "bold". */
    val clockWeight: String = "light",
    /** The app drawer: "list" or "grid", with icon size, columns and whether names show under the icons. */
    val drawerLayout: String = "list",
    val drawerIconDp: Int = 52,
    val drawerGridColumns: Int = 4,
    val drawerGridLabels: Boolean = true,
    val weatherCity: String = "",
    val weatherFahrenheit: Boolean = false,
    val webSearch: WebSearchEngine = WebSearchEngine.SYSTEM,
    val showRecents: Boolean = true,
    /** A row of the last launched apps, icons only, along the bottom of the home screen. Off by default. */
    val showHomeRecents: Boolean = false,
    /** How many recent apps the home row shows (the launcher remembers 10). */
    val homeRecentsCount: Int = 5,
    /** One of [HomeRecentsAlign]. */
    val homeRecentsAlign: String = HomeRecentsAlign.SPREAD,
    /** With more than 5 apps, split the row into two balanced rows. */
    val homeRecentsTwoRows: Boolean = false,
    val homeRecentsIconDp: Int = 40,
    /** Space between icons; unused while spread evenly. */
    val homeRecentsGapDp: Int = 16,
    /** Lifts the row this far above the bottom of the screen. */
    val homeRecentsBottomDp: Int = 0,
    /** Most recently launched app keys, newest first. */
    val recents: List<String> = emptyList(),
    val widgetIds: List<Int> = emptyList(),
    /** Optional read-only GitHub token so the update check can see a private repository. */
    val updateToken: String = "",
    /** Dark text when the wallpaper is light. Off by default: white text with a shadow suits most wallpapers. */
    val adaptiveText: Boolean = false,
    val twoColumnsLandscape: Boolean = true,
    /** Scroll the clock, media, widgets and favourites as one page instead of the favourites having their own area. */
    val homeScrollAsOne: Boolean = false,
    /** "list" (icon and name), "icons" (icons only, one column) or "grid" (icons in several columns). */
    val favouritesLayout: String = "list",
    val favouritesIconDp: Int = 52,
    val favouritesGridColumns: Int = 4,
    val favouritesGridLabels: Boolean = true,
    /** The home-screen media player. */
    val mediaShowArt: Boolean = true,
    val mediaShowArtist: Boolean = true,
    val mediaShowSkip: Boolean = true,
    val mediaShowExtras: Boolean = true,
    val mediaSeekBar: Boolean = false,
    val mediaLarge: Boolean = false,
    val mediaOpacity: Int = 45,
    val mediaHideWhenPaused: Boolean = false,
    /** The car-mode media card. */
    val carMediaShowArt: Boolean = true,
    val carMediaShowArtist: Boolean = true,
    val carMediaShowExtras: Boolean = true,
    val carMediaSeekBar: Boolean = false,
    /** How many notifications the car-mode notifications card lists. */
    val carNotificationCount: Int = 4,
    /** Home-screen folders open as a see-through popup; off = they expand in place under the folder row. */
    val folderPopupOnHome: Boolean = true,
    /** App keys launched by a left / right swipe on the home screen; empty = nothing. */
    val swipeLeftApp: String = "",
    val swipeRightApp: String = "",
    val glucoseEnabled: Boolean = false,
    /** LibreLinkUp follower login. Stored on this device only and never exported. */
    val glucoseEmail: String = "",
    val glucosePassword: String = "",
    val glucoseMmol: Boolean = false,
    /** Thresholds in mg/dL: below [glucoseLow] or above [glucoseHigh] the value is coloured. */
    val glucoseLow: Int = 70,
    val glucoseHigh: Int = 180,
    val showNavigation: Boolean = true,
    /** App opened by tapping the glucose value; empty = find the LibreLink app automatically. */
    val glucoseApp: String = "",
    /** The drawer slides up attached beneath the home screen (both move together) instead of fading over it. */
    val drawerAttached: Boolean = false,
    /** How solid the background behind the drawer / dashboard is, in percent. */
    val drawerOpacity: Int = 90,
    val dashboardOpacity: Int = 100,
    /** Scale the home screen down a little while an app starts. */
    val launchAnimation: Boolean = true,
    /** [DashboardModules] keys that are switched off. Stored as the hidden ones so new cards appear by default. */
    val dashboardHidden: Set<String> = emptySet(),
    /** [DashboardModules] keys shown at half width; neighbouring half cards share a row. */
    val dashboardHalf: Set<String> = emptySet(),
    /** The order of the car-mode cards ([DashboardModules] keys); missing ones are added at the end. */
    val dashboardOrder: List<String> = DashboardModules.all.map { it.first },
    /** Keep the screen on while car mode is open. */
    val keepScreenOn: Boolean = true,
    /** Theme: an accent colour key (see AccentOptions, or "wallpaper") and a font key (see FontOptions). */
    val accentColor: String = "blue",
    val fontFamily: String = "default",
    /** Extra answers in the drawer's search box. */
    val searchCalculator: Boolean = true,
    val searchConversions: Boolean = true,
    val searchSystemScreens: Boolean = true,
    val searchContacts: Boolean = false,
    /** Write a backup file into [autoBackupFolder] every [autoBackupDays] days while the launcher is in use. */
    val autoBackup: Boolean = false,
    /** The chosen folder (an Android document-tree address). Device-specific, so not part of a backup. */
    val autoBackupFolder: String = "",
    val autoBackupDays: Int = 1,
    val lastBackupMs: Long = 0L,
    /** Focus mode: [focusApps] (app keys) are hidden while it is on, by hand or on a schedule. */
    val focusApps: Set<String> = emptySet(),
    val focusManual: Boolean = false,
    val focusScheduleOn: Boolean = false,
    /** Days the schedule runs, as java.util.Calendar day numbers (Sunday = 1 … Saturday = 7). */
    val focusDays: Set<Int> = setOf(2, 3, 4, 5, 6),
    /** Start and end of the schedule, in minutes after midnight. */
    val focusStartMin: Int = 9 * 60,
    val focusEndMin: Int = 17 * 60,
    /** Focus mode is paused until this time (epoch millis). */
    val focusSnoozeUntil: Long = 0L,
    /** Open car mode by itself when connecting to one of [carBluetoothDevices] (device addresses). */
    val carAutoBluetooth: Boolean = false,
    val carBluetoothDevices: Set<String> = emptySet(),
    /** Open car mode when the phone is plugged in (by default only when it is held in landscape). */
    val carAutoCharging: Boolean = false,
    val carChargingLandscapeOnly: Boolean = true,
    /** Close car mode again when the trigger goes away, if it was opened by one of the triggers. */
    val carAutoClose: Boolean = true,
    /** Priority-only do-not-disturb while car mode is open. */
    val carDnd: Boolean = false,
    /** People for the car-mode call buttons. Private, so never in a backup. */
    val callContacts: List<CallContact> = emptyList(),
    /** Start the call straight away (needs the Phone permission); off = open the dialer with the number filled in. */
    val callDirect: Boolean = false,
    /** The first-run setup guide has been shown (or skipped). */
    val onboardingDone: Boolean = false,
    /** Launch counts and last-launch times, for the folder sorts "most used" and "most recent". */
    val usage: Map<String, AppUsage> = emptyMap(),
    /** How a folder opened as a popup from the home screen looks. */
    val folderPopupGrid: Boolean = false,
    val folderPopupColumns: Int = 4,
    val folderPopupOpacity: Int = 60,
    val folderPopupTextSp: Int = 18,
    /** How much the screen behind the popup is dimmed, in percent. */
    val folderPopupDim: Int = 12,
    /** The same look settings for the popup opened from the app drawer (which is more solid and dims more by default). */
    val drawerPopupGrid: Boolean = false,
    val drawerPopupColumns: Int = 4,
    val drawerPopupOpacity: Int = 95,
    val drawerPopupTextSp: Int = 18,
    val drawerPopupDim: Int = 60,
    /** App keys shown as big buttons on the car-mode quick launch card. */
    val carApps: List<String> = emptyList(),
    /** Come back to car mode, rather than the plain home screen, after visiting another app. */
    val rememberCarMode: Boolean = true,
    /** Whether car mode was open when we last changed it; restored after the launcher restarts. Not backed up. */
    val carModeWasOpen: Boolean = false,
    /** Swipes inside car mode: a [GestureActions] key or an app key; empty = nothing. */
    val carSwipeUp: String = GestureActions.NONE,
    val carSwipeDown: String = GestureActions.CLOSE,
    val carSwipeLeft: String = GestureActions.NEXT,
    val carSwipeRight: String = GestureActions.PREVIOUS,
    /** Places for the car-mode navigation buttons. Personal, so never exported in a backup. */
    val destinations: List<Destination> = emptyList(),
    val autoCheckUpdates: Boolean = true,
    /** How often the launcher looks for a new build while it is on screen, in minutes. */
    val updateCheckMinutes: Int = 180,
    /** Fetch a new build in the background on Wi-Fi so installing is one tap. */
    val autoDownloadUpdates: Boolean = true,
    /** The build whose banner was dismissed; a newer build shows it again. */
    val dismissedUpdate: Int = 0,
)

/** Persists favourites, hidden apps and user settings in SharedPreferences. */
class LauncherPrefs(context: Context) {

    private val prefs = context.getSharedPreferences("launcher", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<LauncherSettings> = _settings.asStateFlow()

    init {
        upgradePlainSecrets()
    }

    /**
     * The credentials are stored encrypted (see [SecretBox]). A value that is not ciphertext is how older versions
     * saved it: it is used as is, and [upgradePlainSecrets] rewrites it encrypted right away.
     * A value that cannot be opened (the Keystore key is gone) reads as empty, so the user is asked again.
     */
    private fun readSecret(key: String): String {
        val stored = prefs.getString(key, "").orEmpty()
        return if (SecretBox.isEncrypted(stored)) SecretBox.decrypt(stored).orEmpty() else stored
    }

    /** Never falls back to plain text: if the Keystore is unavailable the secret is simply not kept. */
    private fun sealSecret(plain: String): String = SecretBox.encrypt(plain).orEmpty()

    private fun upgradePlainSecrets() {
        val legacy = SECRET_CREDENTIAL_KEYS.filter { key ->
            prefs.getString(key, "").orEmpty().let { it.isNotEmpty() && !SecretBox.isEncrypted(it) }
        }
        if (legacy.isEmpty()) return
        val editor = prefs.edit()
        legacy.forEach { key -> editor.putString(key, sealSecret(prefs.getString(key, "").orEmpty())) }
        editor.apply()
    }

    /**
     * Everything the user set up (favourites, folders, hidden apps, every setting), as JSON.
     * The GitHub token is left out on purpose: a backup file is easy to share by accident.
     */
    fun exportJson(): String {
        val out = JSONObject().put("_format", BACKUP_FORMAT)
        prefs.all.forEach { (key, value) ->
            if (key !in SECRET_KEYS && value != null) out.put(key, value)
        }
        return out.toString(2)
    }

    /** Replaces the current setup with a backup. Returns false (changing nothing) if the file isn't one of ours. */
    fun importJson(json: String): Boolean {
        val data = runCatching { JSONObject(json) }.getOrNull() ?: return false
        if (data.optInt("_format", -1) != BACKUP_FORMAT) return false
        val editor = prefs.edit()
        // Keep the token (never exported) and replace everything else.
        prefs.all.keys.filter { it !in SECRET_KEYS }.forEach { editor.remove(it) }
        data.keys().forEach { key ->
            if (key.startsWith("_") || key in SECRET_KEYS) return@forEach
            when (val value = data.get(key)) {
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is String -> editor.putString(key, value)
            }
        }
        editor.apply()
        _settings.value = load()
        return true
    }

    fun update(transform: (LauncherSettings) -> LauncherSettings) {
        val next = transform(_settings.value)
        _settings.value = next
        save(next)
    }

    private fun load() = LauncherSettings(
        favourites = prefs.getString(KEY_FAVOURITES, "").orEmpty().split(SEPARATOR).filter { it.isNotBlank() },
        hidden = prefs.getString(KEY_HIDDEN, "").orEmpty().split(SEPARATOR).filter { it.isNotBlank() }.toSet(),
        showClock = prefs.getBoolean(KEY_SHOW_CLOCK, true),
        showNotifications = prefs.getBoolean(KEY_SHOW_NOTIFICATIONS, true),
        showMedia = prefs.getBoolean(KEY_SHOW_MEDIA, true),
        favouriteTextSp = prefs.getInt(KEY_TEXT_SP, 22),
        alignEnd = prefs.getBoolean(KEY_ALIGN_END, false),
        // Before this was its own setting, right-aligned lists were also bottom-anchored; keep that for existing users.
        anchorBottom = prefs.getBoolean(KEY_ANCHOR_BOTTOM, prefs.getBoolean(KEY_ALIGN_END, false)),
        showIcons = prefs.getBoolean(KEY_SHOW_ICONS, true),
        showSearchButton = prefs.getBoolean(KEY_SHOW_SEARCH_BUTTON, true),
        swipeUp = if (prefs.contains(KEY_SWIPE_UP)) prefs.getString(KEY_SWIPE_UP, "").orEmpty() else GestureActions.DRAWER,
        swipeDown = if (prefs.contains(KEY_SWIPE_DOWN)) {
            GestureActions.fromLegacy(prefs.getString(KEY_SWIPE_DOWN, "").orEmpty())
        } else {
            GestureActions.NOTIFICATIONS
        },
        iconPack = prefs.getString(KEY_ICON_PACK, null),
        folders = decodeFolders(prefs.getString(KEY_FOLDERS, null)),
        showCalendar = prefs.getBoolean(KEY_SHOW_CALENDAR, false),
        showWeather = prefs.getBoolean(KEY_SHOW_WEATHER, false),
        headerLayout = prefs.getString(KEY_HEADER_LAYOUT, "stacked") ?: "stacked",
        headerShowDate = prefs.getBoolean(KEY_HEADER_DATE, true),
        headerShowBattery = prefs.getBoolean(KEY_HEADER_BATTERY, true),
        clockSizeSp = prefs.getInt(KEY_CLOCK_SIZE, 56),
        headerOrder = prefs.getString(KEY_HEADER_ORDER, "").orEmpty().split(SEPARATOR).filter { it.isNotBlank() }
            .let { saved -> saved.filter { k -> HeaderItems.all.any { it.first == k } } + HeaderItems.all.map { it.first }.filter { it !in saved } },
        timeFormat = prefs.getString(KEY_TIME_FORMAT, "system") ?: "system",
        dateFormat = prefs.getString(KEY_DATE_FORMAT, "short") ?: "short",
        headerTextSp = prefs.getInt(KEY_HEADER_TEXT, 16).coerceIn(12, 28),
        clockWeight = prefs.getString(KEY_CLOCK_WEIGHT, "light") ?: "light",
        drawerLayout = prefs.getString(KEY_DRAWER_LAYOUT, "list") ?: "list",
        drawerIconDp = prefs.getInt(KEY_DRAWER_ICON, 52).coerceIn(32, 80),
        drawerGridColumns = prefs.getInt(KEY_DRAWER_COLUMNS, 4).coerceIn(3, 7),
        drawerGridLabels = prefs.getBoolean(KEY_DRAWER_LABELS, true),
        weatherCity = prefs.getString(KEY_WEATHER_CITY, "").orEmpty(),
        weatherFahrenheit = prefs.getBoolean(KEY_WEATHER_F, false),
        webSearch = runCatching { WebSearchEngine.valueOf(prefs.getString(KEY_WEB_SEARCH, "").orEmpty()) }
            .getOrDefault(WebSearchEngine.SYSTEM),
        showRecents = prefs.getBoolean(KEY_SHOW_RECENTS, true),
        showHomeRecents = prefs.getBoolean(KEY_SHOW_HOME_RECENTS, false),
        homeRecentsCount = prefs.getInt(KEY_HOME_RECENTS_COUNT, 5).coerceIn(3, 10),
        homeRecentsTwoRows = prefs.getBoolean(KEY_HOME_RECENTS_TWO_ROWS, false),
        homeRecentsAlign = prefs.getString(KEY_HOME_RECENTS_ALIGN, HomeRecentsAlign.SPREAD).orEmpty(),
        homeRecentsIconDp = prefs.getInt(KEY_HOME_RECENTS_ICON, 40).coerceIn(28, 64),
        homeRecentsGapDp = prefs.getInt(KEY_HOME_RECENTS_GAP, 16).coerceIn(0, 40),
        homeRecentsBottomDp = prefs.getInt(KEY_HOME_RECENTS_BOTTOM, 0).coerceIn(0, 160),
        recents = prefs.getString(KEY_RECENTS, "").orEmpty().split(SEPARATOR).filter { it.isNotBlank() },
        widgetIds = prefs.getString(KEY_WIDGETS, "").orEmpty().split(SEPARATOR).mapNotNull { it.toIntOrNull() },
        updateToken = readSecret(KEY_UPDATE_TOKEN),
        adaptiveText = prefs.getBoolean(KEY_ADAPTIVE_TEXT, false),
        twoColumnsLandscape = prefs.getBoolean(KEY_TWO_COLUMNS, true),
        homeScrollAsOne = prefs.getBoolean(KEY_HOME_SCROLL_ONE, false),
        favouritesLayout = prefs.getString(KEY_FAV_LAYOUT, "list") ?: "list",
        favouritesIconDp = prefs.getInt(KEY_FAV_ICON, 52).coerceIn(32, 80),
        favouritesGridColumns = prefs.getInt(KEY_FAV_COLUMNS, 4).coerceIn(2, 6),
        favouritesGridLabels = prefs.getBoolean(KEY_FAV_LABELS, true),
        mediaShowArt = prefs.getBoolean(KEY_MEDIA_ART, true),
        mediaShowArtist = prefs.getBoolean(KEY_MEDIA_ARTIST, true),
        mediaShowSkip = prefs.getBoolean(KEY_MEDIA_SKIP, true),
        mediaShowExtras = prefs.getBoolean(KEY_MEDIA_EXTRAS, true),
        mediaSeekBar = prefs.getBoolean(KEY_MEDIA_SEEK, false),
        mediaLarge = prefs.getBoolean(KEY_MEDIA_LARGE, false),
        mediaOpacity = prefs.getInt(KEY_MEDIA_OPACITY, 45).coerceIn(0, 100),
        mediaHideWhenPaused = prefs.getBoolean(KEY_MEDIA_HIDE_PAUSED, false),
        carMediaShowArt = prefs.getBoolean(KEY_CAR_MEDIA_ART, true),
        carMediaShowArtist = prefs.getBoolean(KEY_CAR_MEDIA_ARTIST, true),
        carMediaShowExtras = prefs.getBoolean(KEY_CAR_MEDIA_EXTRAS, true),
        carMediaSeekBar = prefs.getBoolean(KEY_CAR_MEDIA_SEEK, false),
        carNotificationCount = prefs.getInt(KEY_CAR_NOTIFS, 4).coerceIn(2, 8),
        folderPopupOnHome = prefs.getBoolean(KEY_FOLDER_POPUP, true),
        swipeLeftApp = prefs.getString(KEY_SWIPE_LEFT, "").orEmpty(),
        swipeRightApp = prefs.getString(KEY_SWIPE_RIGHT, "").orEmpty(),
        glucoseEnabled = prefs.getBoolean(KEY_GLUCOSE_ON, false),
        glucoseEmail = readSecret(KEY_GLUCOSE_EMAIL),
        glucosePassword = readSecret(KEY_GLUCOSE_PASSWORD),
        glucoseMmol = prefs.getBoolean(KEY_GLUCOSE_MMOL, false),
        glucoseLow = prefs.getInt(KEY_GLUCOSE_LOW, 70),
        glucoseHigh = prefs.getInt(KEY_GLUCOSE_HIGH, 180),
        showNavigation = prefs.getBoolean(KEY_SHOW_NAVIGATION, true),
        glucoseApp = prefs.getString(KEY_GLUCOSE_APP, "").orEmpty(),
        drawerAttached = prefs.getBoolean(KEY_DRAWER_ATTACHED, false),
        drawerOpacity = prefs.getInt(KEY_DRAWER_OPACITY, 90),
        dashboardOpacity = prefs.getInt(KEY_DASHBOARD_OPACITY, 100),
        launchAnimation = prefs.getBoolean(KEY_LAUNCH_ANIMATION, true),
        dashboardHalf = prefs.getString(KEY_DASH_HALF, "").orEmpty().split(SEPARATOR).filter { it.isNotBlank() }.toSet(),
        dashboardHidden = prefs.getString(KEY_DASH_HIDDEN, "").orEmpty().split(SEPARATOR).filter { it.isNotBlank() }.toSet(),
        dashboardOrder = prefs.getString(KEY_DASH_ORDER, "").orEmpty().split(SEPARATOR).filter { it.isNotBlank() }
            .let { saved -> saved.filter { k -> DashboardModules.all.any { it.first == k } } + DashboardModules.all.map { it.first }.filter { it !in saved } },
        keepScreenOn = prefs.getBoolean(KEY_KEEP_SCREEN_ON, true),
        accentColor = prefs.getString(KEY_ACCENT, "blue").orEmpty(),
        fontFamily = prefs.getString(KEY_FONT, "default").orEmpty(),
        searchCalculator = prefs.getBoolean(KEY_SEARCH_CALC, true),
        searchConversions = prefs.getBoolean(KEY_SEARCH_CONVERT, true),
        searchSystemScreens = prefs.getBoolean(KEY_SEARCH_SYSTEM, true),
        searchContacts = prefs.getBoolean(KEY_SEARCH_CONTACTS, false),
        autoBackup = prefs.getBoolean(KEY_AUTO_BACKUP, false),
        autoBackupFolder = prefs.getString(KEY_AUTO_BACKUP_FOLDER, "").orEmpty(),
        autoBackupDays = prefs.getInt(KEY_AUTO_BACKUP_DAYS, 1),
        lastBackupMs = prefs.getLong(KEY_LAST_BACKUP, 0L),
        focusApps = prefs.getString(KEY_FOCUS_APPS, "").orEmpty().split(SEPARATOR).filter { it.isNotBlank() }.toSet(),
        focusManual = prefs.getBoolean(KEY_FOCUS_MANUAL, false),
        focusScheduleOn = prefs.getBoolean(KEY_FOCUS_SCHEDULE, false),
        focusDays = prefs.getString(KEY_FOCUS_DAYS, null)?.split(SEPARATOR)?.mapNotNull { it.toIntOrNull() }?.toSet() ?: setOf(2, 3, 4, 5, 6),
        focusStartMin = prefs.getInt(KEY_FOCUS_START, 9 * 60),
        focusEndMin = prefs.getInt(KEY_FOCUS_END, 17 * 60),
        focusSnoozeUntil = prefs.getLong(KEY_FOCUS_SNOOZE, 0L),
        carAutoBluetooth = prefs.getBoolean(KEY_CAR_AUTO_BT, false),
        carBluetoothDevices = prefs.getString(KEY_CAR_BT_DEVICES, "").orEmpty().split(SEPARATOR).filter { it.isNotBlank() }.toSet(),
        carAutoCharging = prefs.getBoolean(KEY_CAR_AUTO_CHARGE, false),
        carChargingLandscapeOnly = prefs.getBoolean(KEY_CAR_CHARGE_LANDSCAPE, true),
        carAutoClose = prefs.getBoolean(KEY_CAR_AUTO_CLOSE, true),
        carDnd = prefs.getBoolean(KEY_CAR_DND, false),
        callContacts = decodeContacts(prefs.getString(KEY_CALL_CONTACTS, null)),
        callDirect = prefs.getBoolean(KEY_CALL_DIRECT, false),
        onboardingDone = prefs.getBoolean(KEY_ONBOARDING_DONE, false),
        usage = decodeUsage(prefs.getString(KEY_USAGE, null)),
        folderPopupGrid = prefs.getBoolean(KEY_POPUP_GRID, false),
        folderPopupColumns = prefs.getInt(KEY_POPUP_COLUMNS, 4),
        folderPopupOpacity = prefs.getInt(KEY_POPUP_OPACITY, 60),
        folderPopupTextSp = prefs.getInt(KEY_POPUP_TEXT_SP, 18),
        folderPopupDim = prefs.getInt(KEY_POPUP_DIM, 12),
        drawerPopupGrid = prefs.getBoolean(KEY_DPOPUP_GRID, false),
        drawerPopupColumns = prefs.getInt(KEY_DPOPUP_COLUMNS, 4),
        drawerPopupOpacity = prefs.getInt(KEY_DPOPUP_OPACITY, 95),
        drawerPopupTextSp = prefs.getInt(KEY_DPOPUP_TEXT_SP, 18),
        drawerPopupDim = prefs.getInt(KEY_DPOPUP_DIM, 60),
        carApps = prefs.getString(KEY_CAR_APPS, "").orEmpty().split(SEPARATOR).filter { it.isNotBlank() },
        rememberCarMode = prefs.getBoolean(KEY_REMEMBER_CAR, true),
        carModeWasOpen = prefs.getBoolean(KEY_CAR_WAS_OPEN, false),
        carSwipeUp = prefs.getString(KEY_CAR_UP, GestureActions.NONE).orEmpty(),
        carSwipeDown = prefs.getString(KEY_CAR_DOWN, GestureActions.CLOSE).orEmpty(),
        carSwipeLeft = prefs.getString(KEY_CAR_LEFT, GestureActions.NEXT).orEmpty(),
        carSwipeRight = prefs.getString(KEY_CAR_RIGHT, GestureActions.PREVIOUS).orEmpty(),
        destinations = loadDestinations(),
        autoCheckUpdates = prefs.getBoolean(KEY_AUTO_CHECK, true),
        updateCheckMinutes = prefs.getInt(KEY_UPDATE_MINUTES, 180),
        autoDownloadUpdates = prefs.getBoolean(KEY_AUTO_DOWNLOAD, true),
        dismissedUpdate = prefs.getInt(KEY_DISMISSED_UPDATE, 0),
    )

    private fun save(s: LauncherSettings) {
        prefs.edit()
            .putString(KEY_FAVOURITES, s.favourites.joinToString(SEPARATOR))
            .putString(KEY_HIDDEN, s.hidden.joinToString(SEPARATOR))
            .putBoolean(KEY_SHOW_CLOCK, s.showClock)
            .putBoolean(KEY_SHOW_NOTIFICATIONS, s.showNotifications)
            .putBoolean(KEY_SHOW_MEDIA, s.showMedia)
            .putInt(KEY_TEXT_SP, s.favouriteTextSp)
            .putBoolean(KEY_ALIGN_END, s.alignEnd)
            .putBoolean(KEY_ANCHOR_BOTTOM, s.anchorBottom)
            .putBoolean(KEY_SHOW_ICONS, s.showIcons)
            .putBoolean(KEY_SHOW_SEARCH_BUTTON, s.showSearchButton)
            .putString(KEY_SWIPE_UP, s.swipeUp)
            .putString(KEY_SWIPE_DOWN, s.swipeDown)
            .putString(KEY_ICON_PACK, s.iconPack)
            .putString(KEY_FOLDERS, encodeFolders(s.folders))
            .putBoolean(KEY_SHOW_CALENDAR, s.showCalendar)
            .putBoolean(KEY_SHOW_WEATHER, s.showWeather)
            .putString(KEY_HEADER_LAYOUT, s.headerLayout)
            .putBoolean(KEY_HEADER_DATE, s.headerShowDate)
            .putBoolean(KEY_HEADER_BATTERY, s.headerShowBattery)
            .putInt(KEY_CLOCK_SIZE, s.clockSizeSp)
            .putString(KEY_HEADER_ORDER, s.headerOrder.joinToString(SEPARATOR))
            .putString(KEY_TIME_FORMAT, s.timeFormat)
            .putString(KEY_DATE_FORMAT, s.dateFormat)
            .putInt(KEY_HEADER_TEXT, s.headerTextSp)
            .putString(KEY_CLOCK_WEIGHT, s.clockWeight)
            .putString(KEY_DRAWER_LAYOUT, s.drawerLayout)
            .putInt(KEY_DRAWER_ICON, s.drawerIconDp)
            .putInt(KEY_DRAWER_COLUMNS, s.drawerGridColumns)
            .putBoolean(KEY_DRAWER_LABELS, s.drawerGridLabels)
            .putString(KEY_WEATHER_CITY, s.weatherCity)
            .putBoolean(KEY_WEATHER_F, s.weatherFahrenheit)
            .putString(KEY_WEB_SEARCH, s.webSearch.name)
            .putBoolean(KEY_SHOW_RECENTS, s.showRecents)
            .putBoolean(KEY_SHOW_HOME_RECENTS, s.showHomeRecents)
            .putInt(KEY_HOME_RECENTS_COUNT, s.homeRecentsCount)
            .putString(KEY_HOME_RECENTS_ALIGN, s.homeRecentsAlign)
            .putBoolean(KEY_HOME_RECENTS_TWO_ROWS, s.homeRecentsTwoRows)
            .putInt(KEY_HOME_RECENTS_ICON, s.homeRecentsIconDp)
            .putInt(KEY_HOME_RECENTS_GAP, s.homeRecentsGapDp)
            .putInt(KEY_HOME_RECENTS_BOTTOM, s.homeRecentsBottomDp)
            .putString(KEY_RECENTS, s.recents.joinToString(SEPARATOR))
            .putString(KEY_WIDGETS, s.widgetIds.joinToString(SEPARATOR))
            .putString(KEY_UPDATE_TOKEN, sealSecret(s.updateToken))
            .putBoolean(KEY_ADAPTIVE_TEXT, s.adaptiveText)
            .putBoolean(KEY_TWO_COLUMNS, s.twoColumnsLandscape)
            .putBoolean(KEY_HOME_SCROLL_ONE, s.homeScrollAsOne)
            .putString(KEY_FAV_LAYOUT, s.favouritesLayout)
            .putInt(KEY_FAV_ICON, s.favouritesIconDp)
            .putInt(KEY_FAV_COLUMNS, s.favouritesGridColumns)
            .putBoolean(KEY_FAV_LABELS, s.favouritesGridLabels)
            .putBoolean(KEY_MEDIA_ART, s.mediaShowArt)
            .putBoolean(KEY_MEDIA_ARTIST, s.mediaShowArtist)
            .putBoolean(KEY_MEDIA_SKIP, s.mediaShowSkip)
            .putBoolean(KEY_MEDIA_EXTRAS, s.mediaShowExtras)
            .putBoolean(KEY_MEDIA_SEEK, s.mediaSeekBar)
            .putBoolean(KEY_MEDIA_LARGE, s.mediaLarge)
            .putInt(KEY_MEDIA_OPACITY, s.mediaOpacity)
            .putBoolean(KEY_MEDIA_HIDE_PAUSED, s.mediaHideWhenPaused)
            .putBoolean(KEY_CAR_MEDIA_ART, s.carMediaShowArt)
            .putBoolean(KEY_CAR_MEDIA_ARTIST, s.carMediaShowArtist)
            .putBoolean(KEY_CAR_MEDIA_EXTRAS, s.carMediaShowExtras)
            .putBoolean(KEY_CAR_MEDIA_SEEK, s.carMediaSeekBar)
            .putInt(KEY_CAR_NOTIFS, s.carNotificationCount)
            .putBoolean(KEY_FOLDER_POPUP, s.folderPopupOnHome)
            .putString(KEY_SWIPE_LEFT, s.swipeLeftApp)
            .putString(KEY_SWIPE_RIGHT, s.swipeRightApp)
            .putBoolean(KEY_GLUCOSE_ON, s.glucoseEnabled)
            .putString(KEY_GLUCOSE_EMAIL, sealSecret(s.glucoseEmail))
            .putString(KEY_GLUCOSE_PASSWORD, sealSecret(s.glucosePassword))
            .putBoolean(KEY_GLUCOSE_MMOL, s.glucoseMmol)
            .putInt(KEY_GLUCOSE_LOW, s.glucoseLow)
            .putInt(KEY_GLUCOSE_HIGH, s.glucoseHigh)
            .putBoolean(KEY_SHOW_NAVIGATION, s.showNavigation)
            .putString(KEY_GLUCOSE_APP, s.glucoseApp)
            .putBoolean(KEY_DRAWER_ATTACHED, s.drawerAttached)
            .putInt(KEY_DRAWER_OPACITY, s.drawerOpacity)
            .putInt(KEY_DASHBOARD_OPACITY, s.dashboardOpacity)
            .putBoolean(KEY_LAUNCH_ANIMATION, s.launchAnimation)
            .putString(KEY_DASH_HIDDEN, s.dashboardHidden.joinToString(SEPARATOR))
            .putString(KEY_DASH_HALF, s.dashboardHalf.joinToString(SEPARATOR))
            .putString(KEY_DASH_ORDER, s.dashboardOrder.joinToString(SEPARATOR))
            .putBoolean(KEY_KEEP_SCREEN_ON, s.keepScreenOn)
            .putString(KEY_ACCENT, s.accentColor)
            .putString(KEY_FONT, s.fontFamily)
            .putBoolean(KEY_SEARCH_CALC, s.searchCalculator)
            .putBoolean(KEY_SEARCH_CONVERT, s.searchConversions)
            .putBoolean(KEY_SEARCH_SYSTEM, s.searchSystemScreens)
            .putBoolean(KEY_SEARCH_CONTACTS, s.searchContacts)
            .putBoolean(KEY_AUTO_BACKUP, s.autoBackup)
            .putString(KEY_AUTO_BACKUP_FOLDER, s.autoBackupFolder)
            .putInt(KEY_AUTO_BACKUP_DAYS, s.autoBackupDays)
            .putLong(KEY_LAST_BACKUP, s.lastBackupMs)
            .putString(KEY_FOCUS_APPS, s.focusApps.joinToString(SEPARATOR))
            .putBoolean(KEY_FOCUS_MANUAL, s.focusManual)
            .putBoolean(KEY_FOCUS_SCHEDULE, s.focusScheduleOn)
            .putString(KEY_FOCUS_DAYS, s.focusDays.joinToString(SEPARATOR))
            .putInt(KEY_FOCUS_START, s.focusStartMin)
            .putInt(KEY_FOCUS_END, s.focusEndMin)
            .putLong(KEY_FOCUS_SNOOZE, s.focusSnoozeUntil)
            .putBoolean(KEY_CAR_AUTO_BT, s.carAutoBluetooth)
            .putString(KEY_CAR_BT_DEVICES, s.carBluetoothDevices.joinToString(SEPARATOR))
            .putBoolean(KEY_CAR_AUTO_CHARGE, s.carAutoCharging)
            .putBoolean(KEY_CAR_CHARGE_LANDSCAPE, s.carChargingLandscapeOnly)
            .putBoolean(KEY_CAR_AUTO_CLOSE, s.carAutoClose)
            .putBoolean(KEY_CAR_DND, s.carDnd)
            .putString(KEY_CALL_CONTACTS, encodeContacts(s.callContacts))
            .putBoolean(KEY_CALL_DIRECT, s.callDirect)
            .putBoolean(KEY_ONBOARDING_DONE, s.onboardingDone)
            .putString(KEY_USAGE, encodeUsage(s.usage))
            .putBoolean(KEY_POPUP_GRID, s.folderPopupGrid)
            .putInt(KEY_POPUP_COLUMNS, s.folderPopupColumns)
            .putInt(KEY_POPUP_OPACITY, s.folderPopupOpacity)
            .putInt(KEY_POPUP_TEXT_SP, s.folderPopupTextSp)
            .putInt(KEY_POPUP_DIM, s.folderPopupDim)
            .putBoolean(KEY_DPOPUP_GRID, s.drawerPopupGrid)
            .putInt(KEY_DPOPUP_COLUMNS, s.drawerPopupColumns)
            .putInt(KEY_DPOPUP_OPACITY, s.drawerPopupOpacity)
            .putInt(KEY_DPOPUP_TEXT_SP, s.drawerPopupTextSp)
            .putInt(KEY_DPOPUP_DIM, s.drawerPopupDim)
            .putString(KEY_CAR_APPS, s.carApps.joinToString(SEPARATOR))
            .putBoolean(KEY_REMEMBER_CAR, s.rememberCarMode)
            .putBoolean(KEY_CAR_WAS_OPEN, s.carModeWasOpen)
            .putString(KEY_CAR_UP, s.carSwipeUp)
            .putString(KEY_CAR_DOWN, s.carSwipeDown)
            .putString(KEY_CAR_LEFT, s.carSwipeLeft)
            .putString(KEY_CAR_RIGHT, s.carSwipeRight)
            .putString(KEY_DESTINATIONS, encodeDestinations(s.destinations))
            .putBoolean(KEY_AUTO_CHECK, s.autoCheckUpdates)
            .putInt(KEY_UPDATE_MINUTES, s.updateCheckMinutes)
            .putBoolean(KEY_AUTO_DOWNLOAD, s.autoDownloadUpdates)
            .putInt(KEY_DISMISSED_UPDATE, s.dismissedUpdate)
            .apply()
    }

    private fun encodeContacts(list: List<CallContact>): String =
        JSONArray().apply { list.forEach { put(JSONObject().put("name", it.name).put("number", it.number)) } }.toString()

    private fun decodeContacts(json: String?): List<CallContact> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(json)
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                CallContact(o.getString("name"), o.getString("number"))
            }
        }.getOrDefault(emptyList())
    }

    private fun encodeDestinations(list: List<Destination>): String =
        JSONArray().apply {
            list.forEach { put(JSONObject().put("name", it.name).put("address", it.address).put("icon", it.icon)) }
        }.toString()

    /** The saved list; builds it from the old single Home / Work addresses the first time. */
    private fun loadDestinations(): List<Destination> {
        val json = prefs.getString(KEY_DESTINATIONS, null)
        if (json == null) {
            val home = prefs.getString(KEY_HOME_ADDRESS, "").orEmpty()
            val work = prefs.getString(KEY_WORK_ADDRESS, "").orEmpty()
            return listOfNotNull(
                home.takeIf { it.isNotBlank() }?.let { Destination("Home", it, "🏠") },
                work.takeIf { it.isNotBlank() }?.let { Destination("Work", it, "💼") },
            )
        }
        return runCatching {
            val array = JSONArray(json)
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                Destination(o.optString("name"), o.optString("address"), o.optString("icon").ifBlank { "📍" })
            }
        }.getOrDefault(emptyList())
    }

    private fun encodeUsage(usage: Map<String, AppUsage>): String =
        JSONObject().apply { usage.forEach { (key, u) -> put(key, JSONArray().put(u.count).put(u.lastMs)) } }.toString()

    private fun decodeUsage(json: String?): Map<String, AppUsage> {
        if (json.isNullOrBlank()) return emptyMap()
        return runCatching {
            val o = JSONObject(json)
            o.keys().asSequence().associateWith { key ->
                val pair = o.getJSONArray(key)
                AppUsage(pair.getInt(0), pair.getLong(1))
            }
        }.getOrDefault(emptyMap())
    }

    private fun encodeFolders(folders: List<AppFolder>): String =
        JSONArray().apply {
            folders.forEach { f ->
                put(
                    JSONObject().put("id", f.id).put("name", f.name).put("apps", JSONArray(f.appKeys)).put("sort", f.sort.name).put("icon", f.icon.orEmpty()),
                )
            }
        }.toString()

    private fun decodeFolders(json: String?): List<AppFolder> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(json)
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                val apps = o.getJSONArray("apps")
                AppFolder(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    appKeys = (0 until apps.length()).map { apps.getString(it) },
                    sort = runCatching { FolderSort.valueOf(o.optString("sort")) }.getOrDefault(FolderSort.A_Z),
                    icon = o.optString("icon").takeIf { it.isNotBlank() },
                )
            }
        }.getOrDefault(emptyList())
    }

    private companion object {
        const val SEPARATOR = "|"
        const val BACKUP_FORMAT = 1
        const val KEY_FAVOURITES = "favourites"
        const val KEY_HIDDEN = "hidden"
        const val KEY_SHOW_CLOCK = "show_clock"
        const val KEY_SHOW_NOTIFICATIONS = "show_notifications"
        const val KEY_SHOW_MEDIA = "show_media"
        const val KEY_TEXT_SP = "favourite_text_sp"
        const val KEY_ALIGN_END = "align_end"
        const val KEY_SWIPE_DOWN = "swipe_down"
        const val KEY_SWIPE_UP = "swipe_up"
        const val KEY_DRAWER_ATTACHED = "drawer_attached"
        const val KEY_DRAWER_OPACITY = "drawer_opacity"
        const val KEY_DASHBOARD_OPACITY = "dashboard_opacity"
        const val KEY_ICON_PACK = "icon_pack"
        const val KEY_FOLDERS = "folders"
        const val KEY_SHOW_CALENDAR = "show_calendar"
        const val KEY_SHOW_WEATHER = "show_weather"
        const val KEY_HEADER_LAYOUT = "header_layout"
        const val KEY_HEADER_DATE = "header_date"
        const val KEY_HEADER_BATTERY = "header_battery"
        const val KEY_CLOCK_SIZE = "clock_size"
        const val KEY_HEADER_ORDER = "header_order"
        const val KEY_TIME_FORMAT = "time_format"
        const val KEY_DATE_FORMAT = "date_format"
        const val KEY_HEADER_TEXT = "header_text_sp"
        const val KEY_CLOCK_WEIGHT = "clock_weight"
        const val KEY_DRAWER_LAYOUT = "drawer_layout"
        const val KEY_DRAWER_ICON = "drawer_icon_dp"
        const val KEY_DRAWER_COLUMNS = "drawer_columns"
        const val KEY_DRAWER_LABELS = "drawer_labels"
        const val KEY_WEATHER_CITY = "weather_city"
        const val KEY_WEATHER_F = "weather_fahrenheit"
        const val KEY_WEB_SEARCH = "web_search"
        const val KEY_SHOW_RECENTS = "show_recents"
        const val KEY_SHOW_HOME_RECENTS = "show_home_recents"
        const val KEY_HOME_RECENTS_COUNT = "home_recents_count"
        const val KEY_HOME_RECENTS_ALIGN = "home_recents_align"
        const val KEY_HOME_RECENTS_ICON = "home_recents_icon_dp"
        const val KEY_HOME_RECENTS_TWO_ROWS = "home_recents_two_rows"
        const val KEY_HOME_RECENTS_GAP = "home_recents_gap_dp"
        const val KEY_HOME_RECENTS_BOTTOM = "home_recents_bottom_dp"
        const val KEY_RECENTS = "recents"
        const val KEY_WIDGETS = "widgets"
        const val KEY_UPDATE_TOKEN = "update_token"
        const val KEY_ADAPTIVE_TEXT = "adaptive_text"
        const val KEY_TWO_COLUMNS = "two_columns_landscape"
        const val KEY_HOME_SCROLL_ONE = "home_scroll_one"
        const val KEY_FAV_LAYOUT = "fav_layout"
        const val KEY_FAV_ICON = "fav_icon_dp"
        const val KEY_FAV_COLUMNS = "fav_columns"
        const val KEY_FAV_LABELS = "fav_labels"
        const val KEY_MEDIA_ART = "media_art"
        const val KEY_MEDIA_ARTIST = "media_artist"
        const val KEY_MEDIA_SKIP = "media_skip"
        const val KEY_MEDIA_EXTRAS = "media_extras"
        const val KEY_MEDIA_SEEK = "media_seek"
        const val KEY_MEDIA_LARGE = "media_large"
        const val KEY_MEDIA_OPACITY = "media_opacity"
        const val KEY_MEDIA_HIDE_PAUSED = "media_hide_paused"
        const val KEY_CAR_MEDIA_ART = "car_media_art"
        const val KEY_CAR_MEDIA_ARTIST = "car_media_artist"
        const val KEY_CAR_MEDIA_EXTRAS = "car_media_extras"
        const val KEY_CAR_MEDIA_SEEK = "car_media_seek"
        const val KEY_CAR_NOTIFS = "car_notifications"
        const val KEY_FOLDER_POPUP = "folder_popup_on_home"
        const val KEY_SWIPE_LEFT = "swipe_left_app"
        const val KEY_SWIPE_RIGHT = "swipe_right_app"
        const val KEY_GLUCOSE_ON = "glucose_enabled"
        const val KEY_GLUCOSE_EMAIL = "glucose_email"
        const val KEY_GLUCOSE_PASSWORD = "glucose_password"
        const val KEY_GLUCOSE_MMOL = "glucose_mmol"
        const val KEY_GLUCOSE_LOW = "glucose_low"
        const val KEY_GLUCOSE_HIGH = "glucose_high"
        const val KEY_SHOW_NAVIGATION = "show_navigation"
        const val KEY_GLUCOSE_APP = "glucose_app"
        const val KEY_AUTO_CHECK = "auto_check_updates"
        const val KEY_UPDATE_MINUTES = "update_check_minutes"
        const val KEY_LAUNCH_ANIMATION = "launch_animation"
        const val KEY_DASH_HIDDEN = "dashboard_hidden"
        const val KEY_DASH_HALF = "dashboard_half"
        const val KEY_DASH_ORDER = "dashboard_order"
        const val KEY_KEEP_SCREEN_ON = "keep_screen_on"
        const val KEY_ACCENT = "accent_color"
        const val KEY_FONT = "font_family"
        const val KEY_SEARCH_CALC = "search_calculator"
        const val KEY_SEARCH_CONVERT = "search_conversions"
        const val KEY_SEARCH_SYSTEM = "search_system_screens"
        const val KEY_SEARCH_CONTACTS = "search_contacts"
        const val KEY_AUTO_BACKUP = "auto_backup"
        const val KEY_AUTO_BACKUP_FOLDER = "auto_backup_folder"
        const val KEY_AUTO_BACKUP_DAYS = "auto_backup_days"
        const val KEY_LAST_BACKUP = "last_backup_ms"
        const val KEY_FOCUS_APPS = "focus_apps"
        const val KEY_FOCUS_MANUAL = "focus_manual"
        const val KEY_FOCUS_SCHEDULE = "focus_schedule"
        const val KEY_FOCUS_DAYS = "focus_days"
        const val KEY_FOCUS_START = "focus_start_min"
        const val KEY_FOCUS_END = "focus_end_min"
        const val KEY_FOCUS_SNOOZE = "focus_snooze_until"
        const val KEY_CAR_AUTO_BT = "car_auto_bluetooth"
        const val KEY_CAR_BT_DEVICES = "car_bluetooth_devices"
        const val KEY_CAR_AUTO_CHARGE = "car_auto_charging"
        const val KEY_CAR_CHARGE_LANDSCAPE = "car_charging_landscape"
        const val KEY_CAR_AUTO_CLOSE = "car_auto_close"
        const val KEY_CAR_DND = "car_dnd"
        const val KEY_CALL_CONTACTS = "call_contacts"
        const val KEY_CALL_DIRECT = "call_direct"
        const val KEY_ONBOARDING_DONE = "onboarding_done"
        const val KEY_USAGE = "app_usage"
        const val KEY_POPUP_GRID = "folder_popup_grid"
        const val KEY_POPUP_COLUMNS = "folder_popup_columns"
        const val KEY_POPUP_OPACITY = "folder_popup_opacity"
        const val KEY_POPUP_TEXT_SP = "folder_popup_text_sp"
        const val KEY_POPUP_DIM = "folder_popup_dim"
        const val KEY_DPOPUP_GRID = "drawer_popup_grid"
        const val KEY_DPOPUP_COLUMNS = "drawer_popup_columns"
        const val KEY_DPOPUP_OPACITY = "drawer_popup_opacity"
        const val KEY_DPOPUP_TEXT_SP = "drawer_popup_text_sp"
        const val KEY_DPOPUP_DIM = "drawer_popup_dim"
        const val KEY_CAR_APPS = "car_apps"
        const val KEY_REMEMBER_CAR = "remember_car_mode"
        const val KEY_CAR_WAS_OPEN = "car_mode_was_open"
        const val KEY_CAR_UP = "car_swipe_up"
        const val KEY_CAR_DOWN = "car_swipe_down"
        const val KEY_CAR_LEFT = "car_swipe_left"
        const val KEY_CAR_RIGHT = "car_swipe_right"
        const val KEY_DESTINATIONS = "destinations"
        const val KEY_HOME_ADDRESS = "home_address"
        const val KEY_WORK_ADDRESS = "work_address"
        const val KEY_AUTO_DOWNLOAD = "auto_download_updates"
        const val KEY_DISMISSED_UPDATE = "dismissed_update"
        /** Credentials, places and live state: never written to a backup file, and a restore leaves them alone. */
        private val SECRET_CREDENTIAL_KEYS = listOf(KEY_UPDATE_TOKEN, KEY_GLUCOSE_EMAIL, KEY_GLUCOSE_PASSWORD)
        val SECRET_KEYS = setOf(KEY_UPDATE_TOKEN, KEY_GLUCOSE_EMAIL, KEY_GLUCOSE_PASSWORD, KEY_DESTINATIONS, KEY_HOME_ADDRESS, KEY_WORK_ADDRESS, KEY_CAR_WAS_OPEN, KEY_CALL_CONTACTS, KEY_CAR_BT_DEVICES, KEY_AUTO_BACKUP_FOLDER, KEY_LAST_BACKUP, KEY_FOCUS_SNOOZE)
        const val KEY_ANCHOR_BOTTOM = "anchor_bottom"
        const val KEY_SHOW_ICONS = "show_icons"
        const val KEY_SHOW_SEARCH_BUTTON = "show_search_button"
    }
}

/**
 * One [LauncherPrefs] per process. The activity that confirms a pinned shortcut and the main screen both write
 * favourites; two separate copies would each overwrite the other's changes with their own stale state.
 */
object LauncherPrefsHolder {
    @Volatile
    private var instance: LauncherPrefs? = null

    fun get(context: Context): LauncherPrefs =
        instance ?: synchronized(this) {
            instance ?: LauncherPrefs(context.applicationContext).also { instance = it }
        }
}

/** Where the recent-apps icons sit along the bottom of the home screen. */
object HomeRecentsAlign {
    const val SPREAD = "spread"
    const val START = "start"
    const val CENTER = "center"
    const val END = "end"
}
