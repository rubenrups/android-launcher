package com.rubenrups.launcher

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rubenrups.launcher.data.AppInfo
import com.rubenrups.launcher.data.AppRepository
import com.rubenrups.launcher.data.LauncherPrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class LauncherViewModel(application: Application) : AndroidViewModel(application) {

    val repository = AppRepository(application)
    private val prefs = LauncherPrefs(application)

    val allApps: StateFlow<List<AppInfo>> = repository.apps

    /** Favourites in the user's order, silently dropping apps that were uninstalled. */
    val favourites: StateFlow<List<AppInfo>> =
        combine(repository.apps, prefs.favourites) { apps, keys ->
            val byKey = apps.associateBy { it.key }
            keys.mapNotNull { byKey[it] }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _drawerOpen = MutableStateFlow(false)
    val drawerOpen: StateFlow<Boolean> = _drawerOpen.asStateFlow()

    fun setDrawerOpen(open: Boolean) { _drawerOpen.value = open }

    fun addFavourite(app: AppInfo) = prefs.add(app.key)
    fun removeFavourite(app: AppInfo) = prefs.remove(app.key)

    fun launch(app: AppInfo) {
        repository.launch(app)
        setDrawerOpen(false)
    }
}
