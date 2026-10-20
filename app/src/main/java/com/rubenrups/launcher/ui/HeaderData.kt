package com.rubenrups.launcher.ui

import android.app.WallpaperColors
import android.app.WallpaperManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.graphics.ColorUtils
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import com.rubenrups.launcher.data.CalendarReader
import com.rubenrups.launcher.data.HourlyPoint
import com.rubenrups.launcher.data.NextEvent
import com.rubenrups.launcher.data.WeatherClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** "Office · 10:00–17:30", refreshed every few minutes while the home screen is up. */
@Composable
fun rememberNextEvent(enabled: Boolean): NextEvent? {
    val context = LocalContext.current
    val event by produceState<NextEvent?>(null, enabled) {
        value = null
        while (enabled) {
            value = withContext(Dispatchers.IO) { CalendarReader.nextEvent(context) }
            delay(5 * 60_000L)
        }
    }
    return event
}

/** "⛅ 22°". Typing a city restarts this effect on every keystroke, so the first delay doubles as a debounce. */
@Composable
fun rememberWeather(enabled: Boolean, city: String, fahrenheit: Boolean): String? {
    val weather by produceState<String?>(null, enabled, city, fahrenheit) {
        value = null
        if (!enabled || city.isBlank()) return@produceState
        delay(800)
        while (true) {
            value = withContext(Dispatchers.IO) { runCatching { WeatherClient.fetch(city.trim(), fahrenheit) }.getOrNull() }
                ?: value
            delay(30 * 60_000L)
        }
    }
    return weather
}

/** The next hours of weather for the dashboard; empty when weather is off, no city is set, or the lookup fails. */
@Composable
fun rememberHourlyWeather(enabled: Boolean, city: String, fahrenheit: Boolean): List<HourlyPoint> {
    val hours by produceState<List<HourlyPoint>>(emptyList(), enabled, city, fahrenheit) {
        value = emptyList()
        if (!enabled || city.isBlank()) return@produceState
        delay(800)
        while (true) {
            val fresh = withContext(Dispatchers.IO) {
                runCatching { WeatherClient.fetchHourly(city.trim(), fahrenheit) }.getOrNull()
            }
            if (!fresh.isNullOrEmpty()) value = fresh
            delay(30 * 60_000L)
        }
    }
    return hours
}

/**
 * True when the wallpaper is light enough that white text would be hard to read. Android 8.1+ tells us
 * the wallpaper's colours (and from Android 10 whether dark text suits it); older versions get false.
 */
@Composable
fun rememberLightWallpaper(enabled: Boolean): Boolean {
    val context = LocalContext.current
    var light by remember { mutableStateOf(false) }
    DisposableEffect(enabled) {
        if (!enabled || Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) {
            light = false
            return@DisposableEffect onDispose { }
        }
        val manager = WallpaperManager.getInstance(context)
        fun isLight(colors: WallpaperColors?): Boolean = when {
            colors == null -> false
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                colors.colorHints and WallpaperColors.HINT_SUPPORTS_DARK_TEXT != 0
            else -> ColorUtils.calculateLuminance(colors.primaryColor.toArgb()) > 0.5
        }
        light = isLight(runCatching { manager.getWallpaperColors(WallpaperManager.FLAG_SYSTEM) }.getOrNull())
        val listener = WallpaperManager.OnColorsChangedListener { colors, which ->
            if (which and WallpaperManager.FLAG_SYSTEM != 0) light = isLight(colors)
        }
        manager.addOnColorsChangedListener(listener, Handler(Looper.getMainLooper()))
        onDispose { manager.removeOnColorsChangedListener(listener) }
    }
    return light
}
