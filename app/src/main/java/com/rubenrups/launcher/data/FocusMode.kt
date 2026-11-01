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
    return scheduleActive(focusDays, focusStartMin, focusEndMin, now)
}

/** "09:30" for 570. */
fun formatMinutes(minutes: Int): String = "%02d:%02d".format(minutes / 60 % 24, minutes % 60)
