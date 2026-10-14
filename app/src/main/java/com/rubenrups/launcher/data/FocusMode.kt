package com.rubenrups.launcher.data

import java.util.Calendar

/**
 * Whether focus mode is on right now: paused? on by hand? or inside the schedule (days and hours, which may
 * run past midnight, e.g. 22:00–06:00, in which case the early-morning part belongs to the previous day's run).
 */
fun LauncherSettings.focusActive(now: Calendar = Calendar.getInstance()): Boolean {
    if (focusApps.isEmpty()) return false
    if (System.currentTimeMillis() < focusSnoozeUntil) return false
    if (focusManual) return true
    if (!focusScheduleOn) return false
    val day = now.get(Calendar.DAY_OF_WEEK)
    val previousDay = if (day == Calendar.SUNDAY) Calendar.SATURDAY else day - 1
    val minute = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
    return if (focusStartMin <= focusEndMin) {
        day in focusDays && minute in focusStartMin until focusEndMin
    } else {
        (day in focusDays && minute >= focusStartMin) || (previousDay in focusDays && minute < focusEndMin)
    }
}

/** "09:30" for 570. */
fun formatMinutes(minutes: Int): String = "%02d:%02d".format(minutes / 60 % 24, minutes % 60)
