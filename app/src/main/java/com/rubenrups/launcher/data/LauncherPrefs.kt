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

data class LauncherSettings(
    val favourites: List<String> = emptyList(),
    val hidden: Set<String> = emptySet(),
    val showClock: Boolean = true,
    val showNotifications: Boolean = true,
    val showMedia: Boolean = true,
    val favouriteTextSp: Int = 22,
    /** Right-aligned lists sit at the bottom (thumb reach); left-aligned lists flow from the top, like Niagara. */
    val alignEnd: Boolean = false,
    val showIcons: Boolean = true,
    val showSearchButton: Boolean = true,
    val swipeDown: SwipeAction = SwipeAction.NOTIFICATIONS,
    val iconPack: String? = null,
    val folders: List<AppFolder> = emptyList(),
)

/** How often each app was launched, plus the most recent one; feeds the search suggestions. */
data class AppUsage(val counts: Map<String, Int> = emptyMap(), val last: String? = null)

/** Persists favourites, hidden apps and user settings in SharedPreferences. */
class LauncherPrefs(context: Context) {

    private val prefs = context.getSharedPreferences("launcher", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<LauncherSettings> = _settings.asStateFlow()

    private val _usage = MutableStateFlow(loadUsage())
    val usage: StateFlow<AppUsage> = _usage.asStateFlow()

    fun recordLaunch(key: String) {
        val current = _usage.value
        val next = AppUsage(current.counts + (key to ((current.counts[key] ?: 0) + 1)), key)
        _usage.value = next
        prefs.edit()
            .putString(KEY_USAGE, JSONObject(next.counts).toString())
            .putString(KEY_LAST_LAUNCHED, key)
            .apply()
    }

    private fun loadUsage(): AppUsage {
        val counts = runCatching {
            val obj = JSONObject(prefs.getString(KEY_USAGE, null) ?: "{}")
            obj.keys().asSequence().associateWith { obj.getInt(it) }
        }.getOrDefault(emptyMap())
        return AppUsage(counts, prefs.getString(KEY_LAST_LAUNCHED, null))
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
        showIcons = prefs.getBoolean(KEY_SHOW_ICONS, true),
        showSearchButton = prefs.getBoolean(KEY_SHOW_SEARCH_BUTTON, true),
        swipeDown = runCatching { SwipeAction.valueOf(prefs.getString(KEY_SWIPE_DOWN, "").orEmpty()) }
            .getOrDefault(SwipeAction.NOTIFICATIONS),
        iconPack = prefs.getString(KEY_ICON_PACK, null),
        folders = decodeFolders(prefs.getString(KEY_FOLDERS, null)),
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
            .putBoolean(KEY_SHOW_ICONS, s.showIcons)
            .putBoolean(KEY_SHOW_SEARCH_BUTTON, s.showSearchButton)
            .putString(KEY_SWIPE_DOWN, s.swipeDown.name)
            .putString(KEY_ICON_PACK, s.iconPack)
            .putString(KEY_FOLDERS, encodeFolders(s.folders))
            .apply()
    }

    private fun encodeFolders(folders: List<AppFolder>): String =
        JSONArray().apply {
            folders.forEach { f ->
                put(JSONObject().put("id", f.id).put("name", f.name).put("apps", JSONArray(f.appKeys)))
            }
        }.toString()

    private fun decodeFolders(json: String?): List<AppFolder> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(json)
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                val apps = o.getJSONArray("apps")
                AppFolder(o.getString("id"), o.getString("name"), (0 until apps.length()).map { apps.getString(it) })
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
        const val KEY_SHOW_ICONS = "show_icons"
        const val KEY_SHOW_SEARCH_BUTTON = "show_search_button"
        const val KEY_USAGE = "usage_counts"
        const val KEY_LAST_LAUNCHED = "last_launched"
    }
}
