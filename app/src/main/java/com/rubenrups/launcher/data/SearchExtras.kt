package com.rubenrups.launcher.data

import android.content.Context
import android.content.Intent
import android.provider.ContactsContract
import android.provider.Settings
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.abs
import kotlin.math.pow

/** What the drawer's search box can answer besides app names: sums, conversions, contacts and system screens. */

/** A small calculator: + - * / ^ ( ) and %, decimals, and the letter x as multiply. Returns null for anything else. */
object Calculator {
    fun evaluate(input: String): Double? {
        val text = input.lowercase(Locale.ROOT).replace("×", "*").replace("÷", "/").replace(',', '.').replace(" ", "")
            .replace(Regex("(?<=[0-9)])x(?=[0-9(])"), "*")
        // Needs a digit and a real operator (a leading sign alone doesn't count), so a plain number or a word is
        // left to the app search.
        if (text.none { it.isDigit() }) return null
        if (text.removePrefix("-").removePrefix("+").none { it in "+-*/^%" }) return null
        if (text.any { !(it.isDigit() || it in ".+-*/^%()") }) return null
        return runCatching {
            val parser = Parser(text)
            val value = parser.expression()
            if (parser.atEnd() && value.isFinite()) value else null
        }.getOrNull()
    }

    fun format(value: Double): String {
        if (abs(value) >= 1e15 || (value != 0.0 && abs(value) < 1e-9)) return String.format(Locale.US, "%.6e", value)
        val text = String.format(Locale.US, "%.10f", value).trimEnd('0').trimEnd('.')
        return if (text == "-0") "0" else text
    }

    private class Parser(private val s: String) {
        private var i = 0
        fun atEnd() = i >= s.length
        private fun peek() = if (i < s.length) s[i] else '\u0000'

        fun expression(): Double {
            var v = term()
            while (peek() == '+' || peek() == '-') {
                val op = s[i++]
                val r = term()
                v = if (op == '+') v + r else v - r
            }
            return v
        }

        private fun term(): Double {
            var v = power()
            while (peek() == '*' || peek() == '/') {
                val op = s[i++]
                val r = power()
                v = if (op == '*') v * r else v / r
            }
            return v
        }

        private fun power(): Double {
            val base = unary()
            return if (peek() == '^') { i++; base.pow(power()) } else base
        }

        private fun unary(): Double {
            if (peek() == '-') { i++; return -unary() }
            if (peek() == '+') { i++; return unary() }
            var v = primary()
            while (peek() == '%') { i++; v /= 100.0 }
            return v
        }

        private fun primary(): Double {
            if (peek() == '(') {
                i++
                val v = expression()
                if (peek() != ')') throw IllegalArgumentException("missing )")
                i++
                return v
            }
            val start = i
            while (peek().isDigit() || peek() == '.') i++
            if (start == i) throw IllegalArgumentException("number expected")
            return s.substring(start, i).toDouble()
        }
    }
}

/** "5 km to mi", "72 f in c", "3 lb to kg": length, mass, volume, speed and temperature. */
object UnitConverter {
    private class Unit(val group: String, val toBase: Double, val symbol: String)

    // Each group converts through a base unit: metre, gram, litre, metre per second.
    private val units: Map<String, Unit> = buildMap {
        fun add(group: String, toBase: Double, symbol: String, vararg names: String) {
            val unit = Unit(group, toBase, symbol)
            names.forEach { put(it, unit) }
        }
        add("length", 1.0, "m", "m", "meter", "meters", "metre", "metres")
        add("length", 1000.0, "km", "km", "kilometer", "kilometers", "kilometre", "kilometres")
        add("length", 0.01, "cm", "cm", "centimeter", "centimeters")
        add("length", 0.001, "mm", "mm", "millimeter", "millimeters")
        add("length", 1609.344, "mi", "mi", "mile", "miles")
        add("length", 0.3048, "ft", "ft", "foot", "feet")
        add("length", 0.0254, "in", "in", "inch", "inches")
        add("length", 0.9144, "yd", "yd", "yard", "yards")
        add("mass", 1.0, "g", "g", "gram", "grams")
        add("mass", 1000.0, "kg", "kg", "kilo", "kilos", "kilogram", "kilograms")
        add("mass", 453.59237, "lb", "lb", "lbs", "pound", "pounds")
        add("mass", 28.349523125, "oz", "oz", "ounce", "ounces")
        add("volume", 1.0, "l", "l", "liter", "liters", "litre", "litres")
        add("volume", 0.001, "ml", "ml", "milliliter", "milliliters")
        add("volume", 3.785411784, "gal", "gal", "gallon", "gallons")
        add("volume", 0.0295735295625, "fl oz", "floz")
        add("speed", 1.0, "m/s", "m/s", "ms")
        add("speed", 1 / 3.6, "km/h", "km/h", "kmh", "kph")
        add("speed", 0.44704, "mph", "mph")
        add("speed", 0.514444, "kn", "kn", "knot", "knots")
        add("temp", 0.0, "°C", "c", "°c", "celsius")
        add("temp", 0.0, "°F", "f", "°f", "fahrenheit")
        add("temp", 0.0, "K", "k", "kelvin")
    }

    private val pattern = Regex("""^\s*(-?[0-9]+(?:[.,][0-9]+)?)\s*([a-z°/ ]+?)\s+(?:to|in|=|->)\s+([a-z°/ ]+?)\s*$""", RegexOption.IGNORE_CASE)

    /** The converted amount with its unit ("3.107 mi"), or null if the text isn't a conversion we know. */
    fun convert(text: String): String? {
        val match = pattern.matchEntire(text) ?: return null
        val amount = match.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
        val from = units[match.groupValues[2].lowercase(Locale.ROOT).replace(" ", "")] ?: return null
        val to = units[match.groupValues[3].lowercase(Locale.ROOT).replace(" ", "")] ?: return null
        if (from.group != to.group) return null
        val result = if (from.group == "temp") {
            val celsius = when (from.symbol) { "°F" -> (amount - 32) * 5 / 9; "K" -> amount - 273.15; else -> amount }
            when (to.symbol) { "°F" -> celsius * 9 / 5 + 32; "K" -> celsius + 273.15; else -> celsius }
        } else {
            amount * from.toBase / to.toBase
        }
        return Calculator.format(Math.round(result * 1e4) / 1e4) + " " + to.symbol
    }
}

/** "100 usd to eur" through the free frankfurter.app rates (European Central Bank). Blocking. */
object CurrencyConverter {
    private val pattern = Regex("""^\s*([0-9]+(?:[.,][0-9]+)?)\s*([a-z]{3})\s+(?:to|in|=|->)\s+([a-z]{3})\s*$""", RegexOption.IGNORE_CASE)

    /** True when the text looks like a currency question, so the caller knows to go and fetch it. */
    fun looksLikeCurrency(text: String): Boolean = pattern.matches(text)

    fun convert(text: String): String? {
        val match = pattern.matchEntire(text) ?: return null
        val amount = match.groupValues[1].replace(',', '.')
        val from = match.groupValues[2].uppercase(Locale.ROOT)
        val to = match.groupValues[3].uppercase(Locale.ROOT)
        if (from == to) return null
        val connection = URL("https://api.frankfurter.app/latest?amount=$amount&from=$from&to=$to").openConnection() as HttpURLConnection
        connection.connectTimeout = 6_000
        connection.readTimeout = 6_000
        return try {
            if (connection.responseCode != 200) return null
            val rates = JSONObject(connection.inputStream.bufferedReader().use { it.readText() }).getJSONObject("rates")
            Calculator.format(Math.round(rates.getDouble(to) * 100) / 100.0) + " " + to
        } catch (e: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }
}

/** A system settings screen reachable from search. */
class SystemScreen(val title: String, val keywords: List<String>, val action: String)

object SystemScreens {
    val all = listOf(
        SystemScreen("Wi-Fi", listOf("wifi", "wi-fi", "wlan", "internet", "network"), Settings.ACTION_WIFI_SETTINGS),
        SystemScreen("Bluetooth", listOf("bluetooth"), Settings.ACTION_BLUETOOTH_SETTINGS),
        SystemScreen("Battery saver", listOf("battery", "power saver"), Settings.ACTION_BATTERY_SAVER_SETTINGS),
        SystemScreen("Display", listOf("display", "brightness", "dark mode", "screen"), Settings.ACTION_DISPLAY_SETTINGS),
        SystemScreen("Sound", listOf("sound", "volume", "ringtone"), Settings.ACTION_SOUND_SETTINGS),
        SystemScreen("Location", listOf("location", "gps"), Settings.ACTION_LOCATION_SOURCE_SETTINGS),
        SystemScreen("Apps", listOf("apps", "applications", "app settings"), Settings.ACTION_APPLICATION_SETTINGS),
        SystemScreen("Storage", listOf("storage", "space"), Settings.ACTION_INTERNAL_STORAGE_SETTINGS),
        SystemScreen("Date and time", listOf("date", "time", "clock", "timezone"), Settings.ACTION_DATE_SETTINGS),
        SystemScreen("Language", listOf("language", "locale"), Settings.ACTION_LOCALE_SETTINGS),
        SystemScreen("Accessibility", listOf("accessibility"), Settings.ACTION_ACCESSIBILITY_SETTINGS),
        SystemScreen("Airplane mode", listOf("airplane", "flight"), Settings.ACTION_AIRPLANE_MODE_SETTINGS),
        SystemScreen("NFC", listOf("nfc"), Settings.ACTION_NFC_SETTINGS),
        SystemScreen("VPN", listOf("vpn"), Settings.ACTION_VPN_SETTINGS),
        SystemScreen("Security", listOf("security", "lock screen"), Settings.ACTION_SECURITY_SETTINGS),
        SystemScreen("Do Not Disturb access", listOf("do not disturb", "dnd", "silent"), Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS),
    )

    /** Up to [limit] screens whose name or keywords start with (or contain) the query; short queries match nothing. */
    fun matching(query: String, limit: Int = 3): List<SystemScreen> {
        val q = query.trim().lowercase(Locale.ROOT)
        if (q.length < 3) return emptyList()
        return all.filter { screen -> screen.title.lowercase(Locale.ROOT).startsWith(q) || screen.keywords.any { it.startsWith(q) } }.take(limit)
    }
}

/** A contact found by name. */
class ContactHit(val name: String, val lookupUri: android.net.Uri)

object ContactSearch {
    /** Up to [limit] contacts whose name matches. Needs the contacts permission; returns nothing without it. */
    fun find(context: Context, query: String, limit: Int = 3): List<ContactHit> {
        if (query.trim().length < 2) return emptyList()
        if (context.checkSelfPermission(android.Manifest.permission.READ_CONTACTS) != android.content.pm.PackageManager.PERMISSION_GRANTED) return emptyList()
        val uri = android.net.Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_FILTER_URI, android.net.Uri.encode(query.trim()))
        val found = ArrayList<ContactHit>()
        runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.LOOKUP_KEY, ContactsContract.Contacts.DISPLAY_NAME),
                null, null, "${ContactsContract.Contacts.DISPLAY_NAME} ASC",
            )?.use { cursor ->
                while (cursor.moveToNext() && found.size < limit) {
                    val lookup = ContactsContract.Contacts.getLookupUri(cursor.getLong(0), cursor.getString(1))
                    found.add(ContactHit(cursor.getString(2).orEmpty(), lookup))
                }
            }
        }
        return found
    }

    fun open(context: Context, hit: ContactHit) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, hit.lookupUri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}
