package com.rubenrups.launcher.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Persists the ordered favourites list (as component keys). */
class LauncherPrefs(context: Context) {

    private val prefs = context.getSharedPreferences("launcher", Context.MODE_PRIVATE)

    private val _favourites = MutableStateFlow(load())
    val favourites: StateFlow<List<String>> = _favourites.asStateFlow()

    fun add(key: String) {
        if (key in _favourites.value) return
        save(_favourites.value + key)
    }

    fun remove(key: String) = save(_favourites.value - key)

    private fun load(): List<String> =
        prefs.getString(KEY_FAVOURITES, "").orEmpty().split(SEPARATOR).filter { it.isNotBlank() }

    private fun save(keys: List<String>) {
        _favourites.value = keys
        prefs.edit().putString(KEY_FAVOURITES, keys.joinToString(SEPARATOR)).apply()
    }

    private companion object {
        const val KEY_FAVOURITES = "favourites"
        const val SEPARATOR = "|"
    }
}
