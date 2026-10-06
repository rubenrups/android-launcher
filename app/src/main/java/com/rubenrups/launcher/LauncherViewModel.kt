package com.rubenrups.launcher

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rubenrups.launcher.data.AppEntry
import com.rubenrups.launcher.data.AppFolder
import com.rubenrups.launcher.data.AppUsage
import com.rubenrups.launcher.data.AppInfo
import com.rubenrups.launcher.data.Entry
import com.rubenrups.launcher.data.FolderEntry
import com.rubenrups.launcher.data.AppRepository
import com.rubenrups.launcher.data.IconPack
import com.rubenrups.launcher.data.LauncherPrefs
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

class LauncherViewModel(application: Application) : AndroidViewModel(application) {

    val repository = AppRepository(application)
    private val prefs = LauncherPrefs(application)

    val settings: StateFlow<LauncherSettings> = prefs.settings
    val usage: StateFlow<AppUsage> = prefs.usage
    val iconVersion: StateFlow<Int> = repository.iconVersion

    /** Apps the user hasn't hidden: what the drawer and search show. */
    val visibleApps: StateFlow<List<AppInfo>> =
        combine(repository.apps, settings.map { it.hidden }.distinctUntilChanged()) { apps, hidden ->
            apps.filter { it.key !in hidden }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val hiddenApps: StateFlow<List<AppInfo>> =
        combine(repository.apps, settings.map { it.hidden }.distinctUntilChanged()) { apps, hidden ->
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
                val app = byKey[key]
                if (folder != null) {
                    result.add(FolderEntry(folder, folder.appKeys.mapNotNull { byKey[it] }))
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
            folders.forEach { f -> all.add(FolderEntry(f, f.appKeys.mapNotNull { byKey[it] })) }
            apps.filter { it.key !in inFolder }.forEach { all.add(AppEntry(it)) }
            all.sortedWith(compareBy<Entry>({ it.letter }, { it.label.lowercase() }))
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _drawerOpen = MutableStateFlow(false)
    val drawerOpen: StateFlow<Boolean> = _drawerOpen.asStateFlow()

    private val _searchOpen = MutableStateFlow(false)
    val searchOpen: StateFlow<Boolean> = _searchOpen.asStateFlow()

    private val _settingsOpen = MutableStateFlow(false)
    val settingsOpen: StateFlow<Boolean> = _settingsOpen.asStateFlow()

    init {
        viewModelScope.launch {
            settings.map { it.iconPack }.distinctUntilChanged().collect { repository.setIconPack(it) }
        }
    }

    fun openDrawer() { _drawerOpen.value = true }

    fun closeDrawer() { _drawerOpen.value = false }

    fun openSearch() { _searchOpen.value = true }

    fun closeSearch() { _searchOpen.value = false }

    fun setSettingsOpen(open: Boolean) { _settingsOpen.value = open }

    /** Back to the plain home screen (Home button pressed while we are already on top). */
    fun closeAll() {
        closeDrawer()
        closeSearch()
        _settingsOpen.value = false
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
    fun createFolder(name: String, firstApp: AppInfo? = null) = update { s ->
        val cleaned = s.folders.map { f -> f.copy(appKeys = f.appKeys - firstApp?.key.orEmpty()) }
        s.copy(folders = cleaned + AppFolder(UUID.randomUUID().toString(), name, listOfNotNull(firstApp?.key)))
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

    fun launch(app: AppInfo) {
        prefs.recordLaunch(app.key)
        repository.launch(app)
        closeDrawer()
        closeSearch()
    }
}
