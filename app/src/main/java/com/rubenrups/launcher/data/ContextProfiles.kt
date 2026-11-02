package com.rubenrups.launcher.data

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

enum class ProfileTrigger(val label: String) {
    MANUAL("Only when I switch to it"),
    TIME("At certain times"),
    CHARGING("While charging"),
    BLUETOOTH("While a Bluetooth device is connected"),
}

/**
 * A context the home screen can switch into: its own favourites, apps hidden for a while, a few parts of the
 * home screen switched off, and optionally car mode. What switches it on is [trigger]; a profile chosen by
 * hand ([LauncherSettings.profileManual]) always wins over the automatic ones.
 */
data class ContextProfile(
    val id: String,
    val name: String,
    val trigger: ProfileTrigger = ProfileTrigger.MANUAL,
    val days: Set<Int> = setOf(Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY),
    val startMin: Int = 9 * 60,
    val endMin: Int = 17 * 60,
    val bluetoothAddress: String = "",
    val bluetoothName: String = "",
    /** When on, [favourites] replaces the normal favourites while this profile is active (and is what you edit then). */
    val ownFavourites: Boolean = true,
    val favourites: List<String> = emptyList(),
    val hiddenApps: Set<String> = emptySet(),
    val hideClock: Boolean = false,
    val hideNotifications: Boolean = false,
    val hideMedia: Boolean = false,
    val hideRecents: Boolean = false,
    val openCar: Boolean = false,
)

/** Whether [now] falls inside the days and hours, which may run past midnight (22:00-06:00 belongs to the day it started). */
fun scheduleActive(days: Set<Int>, startMin: Int, endMin: Int, now: Calendar = Calendar.getInstance()): Boolean {
    val day = now.get(Calendar.DAY_OF_WEEK)
    val previousDay = if (day == Calendar.SUNDAY) Calendar.SATURDAY else day - 1
    val minute = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
    return if (startMin <= endMin) {
        day in days && minute in startMin until endMin
    } else {
        (day in days && minute >= startMin) || (previousDay in days && minute < endMin)
    }
}

/** The profile on right now: the one picked by hand, else the first whose trigger holds. */
fun LauncherSettings.activeProfile(
    charging: Boolean,
    connectedDevices: Set<String>,
    now: Calendar = Calendar.getInstance(),
): ContextProfile? {
    profiles.firstOrNull { it.id == profileManual }?.let { return it }
    return profiles.firstOrNull { p ->
        when (p.trigger) {
            ProfileTrigger.MANUAL -> false
            ProfileTrigger.TIME -> scheduleActive(p.days, p.startMin, p.endMin, now)
            ProfileTrigger.CHARGING -> charging
            ProfileTrigger.BLUETOOTH -> p.bluetoothAddress.isNotEmpty() && p.bluetoothAddress in connectedDevices
        }
    }
}

/** The settings the home screen should use while [profile] is on; null (or no profile) leaves them as they are. */
fun LauncherSettings.withProfile(profile: ContextProfile?): LauncherSettings {
    if (profile == null) return this
    return copy(
        favourites = if (profile.ownFavourites) profile.favourites else favourites,
        hidden = hidden + profile.hiddenApps,
        showClock = showClock && !profile.hideClock,
        showNotifications = showNotifications && !profile.hideNotifications,
        showMedia = showMedia && !profile.hideMedia,
        showHomeRecents = showHomeRecents && !profile.hideRecents,
    )
}

/** What the profile triggers need to know about the phone: is it charging, and which Bluetooth devices are connected. */
object ProfileSignals {
    private const val FILE = "profile_signals"
    private const val KEY_DEVICES = "bluetooth_connected"
    private const val KEY_BOOT = "boot_count"

    private val _charging = MutableStateFlow(false)
    val charging: StateFlow<Boolean> = _charging.asStateFlow()
    private val _devices = MutableStateFlow<Set<String>>(emptySet())
    val devices: StateFlow<Set<String>> = _devices.asStateFlow()

    @Volatile
    private var started = false

    private fun store(context: Context) = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private fun bootCount(context: Context): Int =
        runCatching { Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT) }.getOrDefault(0)

    /** Reads the charger state, and the devices that were connected when we last heard (forgotten after a reboot). */
    @Synchronized
    fun start(context: Context) {
        if (started) return
        started = true
        val battery = context.applicationContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        _charging.value = (battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
        val saved = store(context)
        _devices.value = if (saved.getInt(KEY_BOOT, -1) == bootCount(context)) {
            saved.getStringSet(KEY_DEVICES, emptySet()).orEmpty().toSet()
        } else {
            emptySet()
        }
    }

    fun onPower(context: Context, plugged: Boolean) {
        start(context)
        _charging.value = plugged
    }

    fun onBluetooth(context: Context, address: String, connected: Boolean) {
        start(context)
        val next = if (connected) _devices.value + address else _devices.value - address
        _devices.value = next
        store(context).edit().putStringSet(KEY_DEVICES, next).putInt(KEY_BOOT, bootCount(context)).apply()
    }
}

object ProfileCodec {
    fun encode(profiles: List<ContextProfile>): String = JSONArray().apply {
        profiles.forEach { p ->
            put(
                JSONObject()
                    .put("id", p.id).put("name", p.name).put("trigger", p.trigger.name)
                    .put("days", JSONArray(p.days.toList())).put("start", p.startMin).put("end", p.endMin)
                    .put("btAddress", p.bluetoothAddress).put("btName", p.bluetoothName)
                    .put("ownFavourites", p.ownFavourites).put("favourites", JSONArray(p.favourites))
                    .put("hidden", JSONArray(p.hiddenApps.toList()))
                    .put("hideClock", p.hideClock).put("hideNotifications", p.hideNotifications)
                    .put("hideMedia", p.hideMedia).put("hideRecents", p.hideRecents).put("openCar", p.openCar),
            )
        }
    }.toString()

    fun decode(json: String?): List<ContextProfile> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(json)
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                fun strings(name: String) = o.optJSONArray(name)?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty()
                ContextProfile(
                    id = o.getString("id"),
                    name = o.optString("name"),
                    trigger = runCatching { ProfileTrigger.valueOf(o.optString("trigger")) }.getOrDefault(ProfileTrigger.MANUAL),
                    days = o.optJSONArray("days")?.let { a -> (0 until a.length()).map { a.getInt(it) }.toSet() } ?: ContextProfile("", "").days,
                    startMin = o.optInt("start", 9 * 60),
                    endMin = o.optInt("end", 17 * 60),
                    bluetoothAddress = o.optString("btAddress"),
                    bluetoothName = o.optString("btName"),
                    ownFavourites = o.optBoolean("ownFavourites", true),
                    favourites = strings("favourites"),
                    hiddenApps = strings("hidden").toSet(),
                    hideClock = o.optBoolean("hideClock"),
                    hideNotifications = o.optBoolean("hideNotifications"),
                    hideMedia = o.optBoolean("hideMedia"),
                    hideRecents = o.optBoolean("hideRecents"),
                    openCar = o.optBoolean("openCar"),
                )
            }
        }.getOrDefault(emptyList())
    }
}
