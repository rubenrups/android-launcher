package com.rubenrups.launcher

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import com.rubenrups.launcher.data.LauncherPrefsHolder
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Everything that happens when car mode opens or closes outside the launcher's own screens: the Bluetooth and
 * charging triggers, and the do-not-disturb switch. Kept here so the receiver and the view model agree.
 */
object CarMode {
    const val EXTRA_OPEN = "com.rubenrups.launcher.OPEN_CAR_MODE"

    private const val FILE = "car_mode"
    private const val KEY_AUTO_OPENED = "auto_opened"
    private const val KEY_PREVIOUS_FILTER = "previous_filter"
    private const val NO_FILTER = -1

    private val _requests = MutableSharedFlow<Boolean>(extraBufferCapacity = 4)

    /** Open/close requests from the receivers, for a running launcher to follow. */
    val requests: SharedFlow<Boolean> = _requests.asSharedFlow()

    private fun state(context: Context) = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** A trigger (Bluetooth, charging) asks car mode to open. */
    fun autoOpen(context: Context) {
        state(context).edit().putBoolean(KEY_AUTO_OPENED, true).apply()
        applyDnd(context, true)
        LauncherPrefsHolder.get(context).update { it.copy(carModeWasOpen = true) }
        _requests.tryEmit(true)
        // Bringing the launcher forward from the background needs "display over other apps" (see settings).
        runCatching {
            context.startActivity(
                Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    .putExtra(EXTRA_OPEN, true)
            )
        }
    }

    /** A trigger went away: close car mode, but only if a trigger is what opened it. */
    fun autoClose(context: Context) {
        if (!state(context).getBoolean(KEY_AUTO_OPENED, false)) return
        state(context).edit().putBoolean(KEY_AUTO_OPENED, false).apply()
        applyDnd(context, false)
        LauncherPrefsHolder.get(context).update { it.copy(carModeWasOpen = false) }
        _requests.tryEmit(false)
    }

    /** The user opened or closed car mode inside the launcher. */
    fun noteManualChange(context: Context, open: Boolean) {
        if (!open) state(context).edit().putBoolean(KEY_AUTO_OPENED, false).apply()
        applyDnd(context, open)
    }

    /** Turns priority-only do-not-disturb on while car mode is open, and puts the old setting back afterwards. */
    private fun applyDnd(context: Context, open: Boolean) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (!nm.isNotificationPolicyAccessGranted) return
        val saved = state(context)
        if (open) {
            if (!LauncherPrefsHolder.get(context).settings.value.carDnd) return
            // Remember what it was, but only the first time (opening twice must not overwrite the real value).
            if (saved.getInt(KEY_PREVIOUS_FILTER, NO_FILTER) == NO_FILTER) {
                saved.edit().putInt(KEY_PREVIOUS_FILTER, nm.currentInterruptionFilter).apply()
            }
            runCatching { nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY) }
        } else {
            val previous = saved.getInt(KEY_PREVIOUS_FILTER, NO_FILTER)
            if (previous != NO_FILTER) {
                runCatching { nm.setInterruptionFilter(previous) }
                saved.edit().putInt(KEY_PREVIOUS_FILTER, NO_FILTER).apply()
            }
        }
    }
}
