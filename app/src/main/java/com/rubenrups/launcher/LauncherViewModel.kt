package com.rubenrups.launcher

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rubenrups.launcher.data.AppEntry
import com.rubenrups.launcher.data.AppFolder
import com.rubenrups.launcher.data.AppInfo
import com.rubenrups.launcher.data.Entry
import com.rubenrups.launcher.data.FolderEntry
import com.rubenrups.launcher.data.FolderSort
import com.rubenrups.launcher.data.resolve
import com.rubenrups.launcher.widgets.WidgetHostManager
import android.app.Activity
import android.appwidget.AppWidgetProviderInfo
import com.rubenrups.launcher.data.AppRepository
import com.rubenrups.launcher.data.IconPack
import com.rubenrups.launcher.data.LauncherPrefsHolder
import com.rubenrups.launcher.data.Destination
import com.rubenrups.launcher.data.GestureActions
import com.rubenrups.launcher.data.GlucoseState
import com.rubenrups.launcher.data.UpdateManager
import com.rubenrups.launcher.data.VirtualApps
import com.rubenrups.launcher.data.UpdateState
import com.rubenrups.launcher.data.GlucoseStore
import com.rubenrups.launcher.data.LauncherSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

/** Which screen edge the dashboard slides in from (and back out to); NONE = fade and scale. */
enum class DashboardEdge { NONE, LEFT, RIGHT, TOP, BOTTOM }

class LauncherViewModel(application: Application) : AndroidViewModel(application) {

    val repository = AppRepository(application)
    val widgets = WidgetHostManager(application)
    private val prefs = LauncherPrefsHolder.get(application)

    val settings: StateFlow<LauncherSettings> = prefs.settings
    val iconVersion: StateFlow<Int> = repository.iconVersion

    /** Installed apps plus the launcher's own buttons (App drawer, Dashboard), which behave like apps in lists. */
    private val appsWithButtons: StateFlow<List<AppInfo>> =
        repository.apps.map { it + VirtualApps.all }.stateIn(viewModelScope, SharingStarted.Eagerly, VirtualApps.all)

    /** Apps the user hasn't hidden: what the drawer and search show. */
    val visibleApps: StateFlow<List<AppInfo>> =
        combine(appsWithButtons, settings.map { it.hidden }.distinctUntilChanged()) { apps, hidden ->
            apps.filter { it.key !in hidden }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val hiddenApps: StateFlow<List<AppInfo>> =
        combine(appsWithButtons, settings.map { it.hidden }.distinctUntilChanged()) { apps, hidden ->
            apps.filter { it.key in hidden }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Favourites (apps and folders) in the user's order, silently dropping uninstalled apps. */
    val favourites: StateFlow<List<Entry>> =
        combine(repository.apps, settings) { apps, s ->
            val byKey = apps.associateBy { it.key }
            val folders = s.folders.associateBy { it.key }
            val result = ArrayList<Entry>()
            for (key in s.favourites) {
                val folder = folders[key]
                val app = byKey[key] ?: VirtualApps.byKey(key)
                if (folder != null) {
                    result.add(folder.resolve(byKey))
                } else if (app != null) {
                    result.add(AppEntry(app))
                }
            }
            result
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Top-level drawer rows: folders, plus every visible app that isn't inside a folder. */
    val drawerEntries: StateFlow<List<Entry>> =
        combine(visibleApps, settings.map { it.folders }.distinctUntilChanged()) { apps, folders ->
            val byKey = apps.associateBy { it.key }
            val inFolder = folders.flatMapTo(HashSet()) { it.appKeys }
            val all = ArrayList<Entry>()
            folders.forEach { f -> all.add(f.resolve(byKey)) }
            // The App drawer button can sit in a folder, but listing it loose inside the drawer itself would be silly.
            apps.filter { it.key !in inFolder && it.key != VirtualApps.drawer.key }.forEach { all.add(AppEntry(it)) }
            // Folders pin to the top (by name); loose apps follow in A-Z order.
            val sortedFolders = all.filterIsInstance<FolderEntry>().sortedBy { it.label.lowercase() }
            val sortedApps = all.filterIsInstance<AppEntry>().sortedWith(compareBy({ it.letter }, { it.label.lowercase() }))
            sortedFolders + sortedApps
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _drawerOpen = MutableStateFlow(false)
    val drawerOpen: StateFlow<Boolean> = _drawerOpen.asStateFlow()

    /** True when the drawer should open with the search field focused (search button, swipe-down action). */
    private val _focusSearch = MutableStateFlow(false)
    val focusSearch: StateFlow<Boolean> = _focusSearch.asStateFlow()

    /** Text typed on a hardware keyboard from the home screen, handed to the drawer's search field. */
    private val _initialQuery = MutableStateFlow("")
    val initialQuery: StateFlow<String> = _initialQuery.asStateFlow()

    private val _settingsOpen = MutableStateFlow(false)
    val settingsOpen: StateFlow<Boolean> = _settingsOpen.asStateFlow()

    // Car mode comes back after the launcher restarts, if it was open and remembering is on.
    private val _dashboardOpen = MutableStateFlow(settings.value.rememberCarMode && settings.value.carModeWasOpen)
    val dashboardOpen: StateFlow<Boolean> = _dashboardOpen.asStateFlow()

    /** Counts up each time a real app is launched; the UI plays its launch animation on every change. */
    private val _launchPulse = MutableStateFlow(0)
    val launchPulse: StateFlow<Int> = _launchPulse.asStateFlow()

    private val _dashboardEdge = MutableStateFlow(DashboardEdge.NONE)
    val dashboardEdge: StateFlow<DashboardEdge> = _dashboardEdge.asStateFlow()

    val updateState: StateFlow<UpdateState> = UpdateManager.state

    private val glucoseStore = GlucoseStore()
    val glucose: StateFlow<GlucoseState> = glucoseStore.state

    init {
        viewModelScope.launch {
            settings.map { it.iconPack }.distinctUntilChanged().collect { repository.setIconPack(it) }
        }
    }

    fun openDrawer(focusSearch: Boolean = false, initialQuery: String = "") {
        _focusSearch.value = focusSearch
        _initialQuery.value = initialQuery
        _drawerOpen.value = true
    }

    fun closeDrawer() {
        _drawerOpen.value = false
        _focusSearch.value = false
        _initialQuery.value = ""
    }

    /** True while nothing is covering the home screen, so a typed character should start a search. */
    fun canTypeToSearch() = !_drawerOpen.value && !_settingsOpen.value && !_dashboardOpen.value

    /**
     * Opens the Libre app: the one chosen in settings, else the first installed app that looks like LibreLink
     * (its package is com.freestylelibre… and varies by country; "LibreLinkUp" is the follower app, not it).
     * With none found, falls back to the dashboard so the tap still does something.
     */
    fun openGlucoseApp() {
        val apps = repository.apps.value
        val chosen = settings.value.glucoseApp
        val app = apps.firstOrNull { chosen.isNotBlank() && it.key == chosen }
            ?: apps.firstOrNull { it.packageName.startsWith("com.freestylelibre") }
            ?: apps.firstOrNull { it.label.contains("librelink", ignoreCase = true) && !it.label.contains("up", ignoreCase = true) }
        if (app != null) launch(app) else setDashboardOpen(true)
    }

    /** Background checks respect the on/off setting and the chosen interval; [force] is the user's own "check now". */
    fun checkForUpdate(force: Boolean = false) {
        val s = settings.value
        if (!force && !s.autoCheckUpdates) return
        viewModelScope.launch { UpdateManager.check(getApplication(), s.updateToken, force, s.autoDownloadUpdates, s.updateCheckMinutes * 60_000L) }
    }

    fun installUpdate() {
        viewModelScope.launch { UpdateManager.installNow(getApplication(), settings.value.updateToken) }
    }

    fun addCarApp(key: String) = update { s ->
        if (key.isBlank() || key in s.carApps) s else s.copy(carApps = s.carApps + key)
    }

    fun removeCarApp(index: Int) = update { s -> s.copy(carApps = s.carApps.filterIndexed { i, _ -> i != index }) }

    fun moveCarApp(index: Int, delta: Int) = update { s ->
        val to = index + delta
        if (index !in s.carApps.indices || to !in s.carApps.indices) {
            s
        } else {
            s.copy(carApps = s.carApps.toMutableList().apply { add(to, removeAt(index)) })
        }
    }

    fun addDestination() = update { it.copy(destinations = it.destinations + Destination("", "")) }

    fun updateDestination(index: Int, transform: (Destination) -> Destination) = update { s ->
        s.copy(destinations = s.destinations.mapIndexed { i, d -> if (i == index) transform(d) else d })
    }

    fun deleteDestination(index: Int) = update { s -> s.copy(destinations = s.destinations.filterIndexed { i, _ -> i != index }) }

    fun moveDestination(index: Int, delta: Int) = update { s ->
        val to = index + delta
        if (index !in s.destinations.indices || to !in s.destinations.indices) {
            s
        } else {
            s.copy(destinations = s.destinations.toMutableList().apply { add(to, removeAt(index)) })
        }
    }

    /** Swaps two car-mode cards in the stored order. */
    fun swapDashboardCards(a: String, b: String) = update { s ->
        val i = s.dashboardOrder.indexOf(a)
        val j = s.dashboardOrder.indexOf(b)
        if (i < 0 || j < 0) s else s.copy(dashboardOrder = s.dashboardOrder.toMutableList().apply { this[i] = b; this[j] = a })
    }

    fun setDashboardCardHidden(key: String, hidden: Boolean) = update { s ->
        s.copy(dashboardHidden = if (hidden) s.dashboardHidden + key else s.dashboardHidden - key)
    }

    fun dismissUpdate(build: Int) = update { it.copy(dismissedUpdate = build) }

    /** Short text for what a swipe will do, shown while swiping; null when the swipe is set to do nothing. */
    fun actionHint(key: String): String? {
        if (key.isBlank()) return null
        GestureActions.builtInLabel(key)?.let { return it }
        return repository.apps.value.firstOrNull { it.key == key }?.let { "Open ${it.label}" }
    }

    fun launchByKey(key: String, from: DashboardEdge = DashboardEdge.NONE) {
        if (key == ACTION_DASHBOARD) {
            setDashboardOpen(true, from)
            return
        }
        repository.apps.value.firstOrNull { it.key == key }?.let { launch(it) }
    }

    fun backupJson(): String = prefs.exportJson()

    fun restoreBackup(json: String): Boolean = prefs.importJson(json)

    fun setSettingsOpen(open: Boolean) { _settingsOpen.value = open }

    fun setDashboardOpen(open: Boolean, from: DashboardEdge = DashboardEdge.NONE) {
        // Closing keeps the last edge, so the dashboard leaves the way it came.
        if (open) {
            closeDrawer()
            _dashboardEdge.value = from
        }
        _dashboardOpen.value = open
        update { it.copy(carModeWasOpen = open) }
    }

    /** One poll of LibreLinkUp; the caller decides the rhythm (see LauncherApp). No-op while the feature is off. */
    suspend fun refreshGlucose() {
        val s = settings.value
        if (!s.glucoseEnabled || s.glucoseEmail.isBlank() || s.glucosePassword.isBlank()) {
            glucoseStore.clear()
            return
        }
        glucoseStore.refresh(s.glucoseEmail.trim(), s.glucosePassword)
    }

    /** Back to the plain home screen (Home pressed). [fromAnotherApp]: the launcher was in the background. */
    fun closeAll(fromAnotherApp: Boolean = false) {
        closeDrawer()
        _settingsOpen.value = false
        // Coming back from another app, car mode stays up (if you asked for that); pressing Home on the launcher itself leaves it.
        if (!(fromAnotherApp && settings.value.rememberCarMode)) setDashboardOpen(false)
    }

    fun update(transform: (LauncherSettings) -> LauncherSettings) = prefs.update(transform)

    fun addFavouriteKey(key: String) = update { s ->
        if (key in s.favourites) s else s.copy(favourites = s.favourites + key)
    }

    fun removeFavouriteKey(key: String) = update { it.copy(favourites = it.favourites - key) }

    fun addFavourite(app: AppInfo) = addFavouriteKey(app.key)

    fun removeFavourite(app: AppInfo) = removeFavouriteKey(app.key)

    fun moveFavourite(from: Int, to: Int) = update { s ->
        if (from !in s.favourites.indices || to !in s.favourites.indices || from == to) {
            s
        } else {
            s.copy(favourites = s.favourites.toMutableList().apply { add(to, removeAt(from)) })
        }
    }

    /** Hiding also drops the app from favourites and folders, so it can't linger anywhere visible. */
    fun hide(app: AppInfo) = update { s ->
        s.copy(
            hidden = s.hidden + app.key,
            favourites = s.favourites - app.key,
            folders = s.folders.map { it.copy(appKeys = it.appKeys - app.key) },
        )
    }

    fun unhide(app: AppInfo) = update { it.copy(hidden = it.hidden - app.key) }

    /** Creates a folder, optionally seeded with [firstApp]. An app lives in at most one folder. */
    fun createFolder(name: String, firstApp: AppInfo? = null): String {
        val id = UUID.randomUUID().toString()
        update { s ->
            val cleaned = s.folders.map { f -> f.copy(appKeys = f.appKeys - firstApp?.key.orEmpty()) }
            s.copy(folders = cleaned + AppFolder(id, name, listOfNotNull(firstApp?.key)))
        }
        return id
    }

    fun addToFolder(app: AppInfo, folderId: String) = update { s ->
        s.copy(folders = s.folders.map { f ->
            when {
                f.id == folderId -> f.copy(appKeys = (f.appKeys - app.key) + app.key)
                else -> f.copy(appKeys = f.appKeys - app.key)
            }
        })
    }

    fun removeFromFolder(app: AppInfo) = update { s ->
        s.copy(folders = s.folders.map { it.copy(appKeys = it.appKeys - app.key) })
    }

    /**
     * Switching to Manual freezes the order you currently see, so nothing jumps around;
     * from there [swapInFolder] moves apps one step at a time.
     */
    fun setFolderSort(entry: FolderEntry, sort: FolderSort) = update { s ->
        s.copy(folders = s.folders.map { f ->
            when {
                f.id != entry.folder.id -> f
                sort == FolderSort.MANUAL -> {
                    val shown = entry.apps.map { it.key }
                    f.copy(sort = sort, appKeys = shown + (f.appKeys - shown.toSet()))
                }
                else -> f.copy(sort = sort)
            }
        })
    }

    fun setFolderIcon(folderId: String, icon: String?) = update { s ->
        s.copy(folders = s.folders.map { if (it.id == folderId) it.copy(icon = icon) else it })
    }

    fun swapInFolder(folderId: String, keyA: String, keyB: String) = update { s ->
        s.copy(folders = s.folders.map { f ->
            val i = f.appKeys.indexOf(keyA)
            val j = f.appKeys.indexOf(keyB)
            if (f.id != folderId || i < 0 || j < 0) {
                f
            } else {
                f.copy(appKeys = f.appKeys.toMutableList().apply { this[i] = keyB; this[j] = keyA })
            }
        })
    }

    fun renameFolder(folderId: String, name: String) = update { s ->
        s.copy(folders = s.folders.map { if (it.id == folderId) it.copy(name = name) else it })
    }

    /** Deleting a folder returns its apps to the top-level list; nothing is uninstalled. */
    fun deleteFolder(folder: AppFolder) = update { s ->
        s.copy(
            folders = s.folders.filter { it.id != folder.id },
            favourites = s.favourites - folder.key,
        )
    }

    fun installedIconPacks(): List<IconPack> = repository.iconPacks.installedPacks()

    fun beginAddWidget(activity: Activity, provider: AppWidgetProviderInfo) {
        widgets.beginAdd(activity, provider) { id -> update { it.copy(widgetIds = it.widgetIds + id) } }
    }

    /** Moves a home-screen widget one step up (-1) or down (+1) in the list. */
    fun moveWidget(id: Int, delta: Int) = update { s ->
        val from = s.widgetIds.indexOf(id)
        val to = from + delta
        if (from < 0 || to !in s.widgetIds.indices) {
            s
        } else {
            s.copy(widgetIds = s.widgetIds.toMutableList().apply { add(to, removeAt(from)) })
        }
    }

    fun removeWidget(id: Int) {
        widgets.delete(id)
        update { it.copy(widgetIds = it.widgetIds - id) }
    }

    fun webSearch(query: String) {
        repository.webSearch(query, settings.value.webSearch)
        closeDrawer()
    }

    fun launch(app: AppInfo) {
        if (VirtualApps.isVirtual(app)) {
            // A launcher function in the favourites list rather than a real app: run it, record nothing.
            if (app.key == VirtualApps.drawer.key) openDrawer(false) else setDashboardOpen(true)
            return
        }
        update { s -> s.copy(recents = (listOf(app.key) + (s.recents - app.key)).take(MAX_RECENTS)) }
        if (settings.value.launchAnimation) _launchPulse.value += 1
        repository.launch(app)
        closeDrawer()
    }

    companion object {
        /** Swipe-gesture target that opens the dashboard instead of an app. */
        const val ACTION_DASHBOARD = GestureActions.DASHBOARD
        private const val MAX_RECENTS = 8
    }
}
