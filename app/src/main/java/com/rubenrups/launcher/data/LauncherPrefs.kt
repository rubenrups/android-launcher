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

/** A saved place for the car-mode navigation buttons. */
data class Destination(val name: String, val address: String, val icon: String = "📍")

/** The cards of the car-mode screen: each can be switched off and reordered. */
object DashboardModules {
    const val GLUCOSE = "glucose"
    const val NAVIGATION = "navigation"
    const val MEDIA = "media"
    const val WEATHER = "weather"
    const val QUICK = "quick"
    const val STATS = "stats"

    /** Default order, and the names shown in settings. */
    val all: List<Pair<String, String>> = listOf(
        GLUCOSE to "Glucose",
        MEDIA to "Media player",
        NAVIGATION to "Navigation",
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
    val weatherCity: String = "",
    val weatherFahrenheit: Boolean = false,
    val webSearch: WebSearchEngine = WebSearchEngine.SYSTEM,
    val showRecents: Boolean = true,
    /** Most recently launched app keys, newest first. */
    val recents: List<String> = emptyList(),
    val widgetIds: List<Int> = emptyList(),
    /** Optional read-only GitHub token so the update check can see a private repository. */
    val updateToken: String = "",
    /** Dark text when the wallpaper is light. Off by default: white text with a shadow suits most wallpapers. */
    val adaptiveText: Boolean = false,
    val twoColumnsLandscape: Boolean = true,
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
    /** The order of the car-mode cards ([DashboardModules] keys); missing ones are added at the end. */
    val dashboardOrder: List<String> = DashboardModules.all.map { it.first },
    /** Keep the screen on while car mode is open. */
    val keepScreenOn: Boolean = true,
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
        weatherCity = prefs.getString(KEY_WEATHER_CITY, "").orEmpty(),
        weatherFahrenheit = prefs.getBoolean(KEY_WEATHER_F, false),
        webSearch = runCatching { WebSearchEngine.valueOf(prefs.getString(KEY_WEB_SEARCH, "").orEmpty()) }
            .getOrDefault(WebSearchEngine.SYSTEM),
        showRecents = prefs.getBoolean(KEY_SHOW_RECENTS, true),
        recents = prefs.getString(KEY_RECENTS, "").orEmpty().split(SEPARATOR).filter { it.isNotBlank() },
        widgetIds = prefs.getString(KEY_WIDGETS, "").orEmpty().split(SEPARATOR).mapNotNull { it.toIntOrNull() },
        updateToken = prefs.getString(KEY_UPDATE_TOKEN, "").orEmpty(),
        adaptiveText = prefs.getBoolean(KEY_ADAPTIVE_TEXT, false),
        twoColumnsLandscape = prefs.getBoolean(KEY_TWO_COLUMNS, true),
        folderPopupOnHome = prefs.getBoolean(KEY_FOLDER_POPUP, true),
        swipeLeftApp = prefs.getString(KEY_SWIPE_LEFT, "").orEmpty(),
        swipeRightApp = prefs.getString(KEY_SWIPE_RIGHT, "").orEmpty(),
        glucoseEnabled = prefs.getBoolean(KEY_GLUCOSE_ON, false),
        glucoseEmail = prefs.getString(KEY_GLUCOSE_EMAIL, "").orEmpty(),
        glucosePassword = prefs.getString(KEY_GLUCOSE_PASSWORD, "").orEmpty(),
        glucoseMmol = prefs.getBoolean(KEY_GLUCOSE_MMOL, false),
        glucoseLow = prefs.getInt(KEY_GLUCOSE_LOW, 70),
        glucoseHigh = prefs.getInt(KEY_GLUCOSE_HIGH, 180),
        showNavigation = prefs.getBoolean(KEY_SHOW_NAVIGATION, true),
        glucoseApp = prefs.getString(KEY_GLUCOSE_APP, "").orEmpty(),
        drawerAttached = prefs.getBoolean(KEY_DRAWER_ATTACHED, false),
        drawerOpacity = prefs.getInt(KEY_DRAWER_OPACITY, 90),
        dashboardOpacity = prefs.getInt(KEY_DASHBOARD_OPACITY, 100),
        launchAnimation = prefs.getBoolean(KEY_LAUNCH_ANIMATION, true),
        dashboardHidden = prefs.getString(KEY_DASH_HIDDEN, "").orEmpty().split(SEPARATOR).filter { it.isNotBlank() }.toSet(),
        dashboardOrder = prefs.getString(KEY_DASH_ORDER, "").orEmpty().split(SEPARATOR).filter { it.isNotBlank() }
            .let { saved -> saved.filter { k -> DashboardModules.all.any { it.first == k } } + DashboardModules.all.map { it.first }.filter { it !in saved } },
        keepScreenOn = prefs.getBoolean(KEY_KEEP_SCREEN_ON, true),
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
            .putString(KEY_WEATHER_CITY, s.weatherCity)
            .putBoolean(KEY_WEATHER_F, s.weatherFahrenheit)
            .putString(KEY_WEB_SEARCH, s.webSearch.name)
            .putBoolean(KEY_SHOW_RECENTS, s.showRecents)
            .putString(KEY_RECENTS, s.recents.joinToString(SEPARATOR))
            .putString(KEY_WIDGETS, s.widgetIds.joinToString(SEPARATOR))
            .putString(KEY_UPDATE_TOKEN, s.updateToken)
            .putBoolean(KEY_ADAPTIVE_TEXT, s.adaptiveText)
            .putBoolean(KEY_TWO_COLUMNS, s.twoColumnsLandscape)
            .putBoolean(KEY_FOLDER_POPUP, s.folderPopupOnHome)
            .putString(KEY_SWIPE_LEFT, s.swipeLeftApp)
            .putString(KEY_SWIPE_RIGHT, s.swipeRightApp)
            .putBoolean(KEY_GLUCOSE_ON, s.glucoseEnabled)
            .putString(KEY_GLUCOSE_EMAIL, s.glucoseEmail)
            .putString(KEY_GLUCOSE_PASSWORD, s.glucosePassword)
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
            .putString(KEY_DASH_ORDER, s.dashboardOrder.joinToString(SEPARATOR))
            .putBoolean(KEY_KEEP_SCREEN_ON, s.keepScreenOn)
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
        const val KEY_WEATHER_CITY = "weather_city"
        const val KEY_WEATHER_F = "weather_fahrenheit"
        const val KEY_WEB_SEARCH = "web_search"
        const val KEY_SHOW_RECENTS = "show_recents"
        const val KEY_RECENTS = "recents"
        const val KEY_WIDGETS = "widgets"
        const val KEY_UPDATE_TOKEN = "update_token"
        const val KEY_ADAPTIVE_TEXT = "adaptive_text"
        const val KEY_TWO_COLUMNS = "two_columns_landscape"
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
        const val KEY_DASH_ORDER = "dashboard_order"
        const val KEY_KEEP_SCREEN_ON = "keep_screen_on"
        const val KEY_CAR_UP = "car_swipe_up"
        const val KEY_CAR_DOWN = "car_swipe_down"
        const val KEY_CAR_LEFT = "car_swipe_left"
        const val KEY_CAR_RIGHT = "car_swipe_right"
        const val KEY_DESTINATIONS = "destinations"
        const val KEY_HOME_ADDRESS = "home_address"
        const val KEY_WORK_ADDRESS = "work_address"
        const val KEY_AUTO_DOWNLOAD = "auto_download_updates"
        const val KEY_DISMISSED_UPDATE = "dismissed_update"
        /** Credentials: never written to a backup file, and a restore leaves them alone. */
        val SECRET_KEYS = setOf(KEY_UPDATE_TOKEN, KEY_GLUCOSE_EMAIL, KEY_GLUCOSE_PASSWORD, KEY_DESTINATIONS, KEY_HOME_ADDRESS, KEY_WORK_ADDRESS)
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
