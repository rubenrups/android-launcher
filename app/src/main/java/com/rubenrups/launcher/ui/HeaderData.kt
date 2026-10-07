package com.rubenrups.launcher.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import com.rubenrups.launcher.data.CalendarReader
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
