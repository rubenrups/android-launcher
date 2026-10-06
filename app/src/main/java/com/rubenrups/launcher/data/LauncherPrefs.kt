package com.rubenrups.launcher.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

enum class SwipeAction(val label: String) {
    NOTIFICATIONS("Notification shade"),
    SEARCH("Search apps"),
    NONE("Nothing"),
}

enum class WebSearchEngine(val label: String, val urlTemplate: String?) {
    OFF("Off", null),
    SYSTEM("System default", null),
    GOOGLE("Google", "https://www.google.com/search?q=%s"),
    DUCKDUCKGO("DuckDuckGo", "https://duckduckgo.com/?q=%s"),
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
    val swipeDown: SwipeAction = SwipeAction.NOTIFICATIONS,
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
)

/** Persists favourites, hidden apps and user settings in SharedPreferences. */
class LauncherPrefs(context: Context) {

    private val prefs = context.getSharedPreferences("launcher", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<LauncherSettings> = _settings.asStateFlow()

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
        swipeDown = runCatching { SwipeAction.valueOf(prefs.getString(KEY_SWIPE_DOWN, "").orEmpty()) }
            .getOrDefault(SwipeAction.NOTIFICATIONS),
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
            .putString(KEY_SWIPE_DOWN, s.swipeDown.name)
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
            .apply()
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
        const val KEY_FAVOURITES = "favourites"
        const val KEY_HIDDEN = "hidden"
        const val KEY_SHOW_CLOCK = "show_clock"
        const val KEY_SHOW_NOTIFICATIONS = "show_notifications"
        const val KEY_SHOW_MEDIA = "show_media"
        const val KEY_TEXT_SP = "favourite_text_sp"
        const val KEY_ALIGN_END = "align_end"
        const val KEY_SWIPE_DOWN = "swipe_down"
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
        const val KEY_ANCHOR_BOTTOM = "anchor_bottom"
        const val KEY_SHOW_ICONS = "show_icons"
        const val KEY_SHOW_SEARCH_BUTTON = "show_search_button"
    }
}
