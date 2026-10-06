package com.rubenrups.launcher

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rubenrups.launcher.data.AppInfo
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

class LauncherViewModel(application: Application) : AndroidViewModel(application) {

    val repository = AppRepository(application)
    private val prefs = LauncherPrefs(application)

    val settings: StateFlow<LauncherSettings> = prefs.settings
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

    /** Favourites in the user's order, silently dropping apps that were uninstalled. */
    val favourites: StateFlow<List<AppInfo>> =
        combine(repository.apps, settings.map { it.favourites }.distinctUntilChanged()) { apps, keys ->
            val byKey = apps.associateBy { it.key }
            keys.mapNotNull { byKey[it] }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _drawerOpen = MutableStateFlow(false)
    val drawerOpen: StateFlow<Boolean> = _drawerOpen.asStateFlow()

    private val _focusSearch = MutableStateFlow(false)
    val focusSearch: StateFlow<Boolean> = _focusSearch.asStateFlow()

    private val _settingsOpen = MutableStateFlow(false)
    val settingsOpen: StateFlow<Boolean> = _settingsOpen.asStateFlow()

    init {
        viewModelScope.launch {
            settings.map { it.iconPack }.distinctUntilChanged().collect { repository.setIconPack(it) }
        }
    }

    fun openDrawer(focusSearch: Boolean = false) {
        _focusSearch.value = focusSearch
        _drawerOpen.value = true
    }

    fun closeDrawer() {
        _drawerOpen.value = false
        _focusSearch.value = false
    }

    fun setSettingsOpen(open: Boolean) { _settingsOpen.value = open }

    /** Back to the plain home screen (Home button pressed while we are already on top). */
    fun closeAll() {
        closeDrawer()
        _settingsOpen.value = false
    }

    fun update(transform: (LauncherSettings) -> LauncherSettings) = prefs.update(transform)

    fun addFavourite(app: AppInfo) = update { s ->
        if (app.key in s.favourites) s else s.copy(favourites = s.favourites + app.key)
    }

    fun removeFavourite(app: AppInfo) = update { it.copy(favourites = it.favourites - app.key) }

    fun moveFavourite(from: Int, to: Int) = update { s ->
        if (from !in s.favourites.indices || to !in s.favourites.indices || from == to) {
            s
        } else {
            s.copy(favourites = s.favourites.toMutableList().apply { add(to, removeAt(from)) })
        }
    }

    /** Hiding also drops the app from favourites, so a hidden app can't linger on the home screen. */
    fun hide(app: AppInfo) = update { it.copy(hidden = it.hidden + app.key, favourites = it.favourites - app.key) }

    fun unhide(app: AppInfo) = update { it.copy(hidden = it.hidden - app.key) }

    fun installedIconPacks(): List<IconPack> = repository.iconPacks.installedPacks()

    fun launch(app: AppInfo) {
        repository.launch(app)
        closeDrawer()
    }
}
