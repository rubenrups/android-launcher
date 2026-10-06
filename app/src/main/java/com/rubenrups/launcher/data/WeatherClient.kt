package com.rubenrups.launcher.data

import android.net.Uri
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt

/**
 * Current temperature from Open-Meteo (free, no API key). The city is geocoded once and remembered;
 * results are cached for 30 minutes. Blocking: call from a background dispatcher, and treat any
 * exception as "no weather right now".
 */
object WeatherClient {
    private class Place(val lat: Double, val lon: Double)

    private val places = HashMap<String, Place>()
    private var cacheKey: String? = null
    private var cacheTime = 0L
    private var cacheText: String? = null

    @Synchronized
    fun fetch(city: String, fahrenheit: Boolean): String? {
        val key = "$city|$fahrenheit"
        if (key == cacheKey && System.currentTimeMillis() - cacheTime < CACHE_MS) return cacheText

        val place = places[city] ?: geocode(city)?.also { places[city] = it } ?: return null
        val unit = if (fahrenheit) "fahrenheit" else "celsius"
        val current = JSONObject(
            get(
                "https://api.open-meteo.com/v1/forecast?latitude=${place.lat}&longitude=${place.lon}" +
                    "&current=temperature_2m,weather_code&temperature_unit=$unit"
            )
        ).getJSONObject("current")
        val text = "${emoji(current.getInt("weather_code"))} ${current.getDouble("temperature_2m").roundToInt()}°"
        cacheKey = key
        cacheTime = System.currentTimeMillis()
        cacheText = text
        return text
    }

    private fun geocode(city: String): Place? {
        val results = JSONObject(
            get("https://geocoding-api.open-meteo.com/v1/search?count=1&name=" + Uri.encode(city))
        ).optJSONArray("results") ?: return null
        val first = results.optJSONObject(0) ?: return null
        return Place(first.getDouble("latitude"), first.getDouble("longitude"))
    }

    /** WMO weather interpretation codes, as documented by Open-Meteo. */
    private fun emoji(code: Int): String = when (code) {
        0 -> "☀️"
        1 -> "🌤️"
        2 -> "⛅"
        3 -> "☁️"
        45, 48 -> "🌫️"
        in 51..57 -> "🌦️"
        in 61..67, in 80..82 -> "🌧️"
        in 71..77, 85, 86 -> "❄️"
        in 95..99 -> "⛈️"
        else -> "🌡️"
    }

    private fun get(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = TIMEOUT_MS
        connection.readTimeout = TIMEOUT_MS
        try {
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private const val CACHE_MS = 30 * 60 * 1000L
    private const val TIMEOUT_MS = 8_000
}
