package com.rubenrups.launcher.data

import kotlin.math.pow

/** What "most used" counts: all launches ever, only the last few days, or launches that fade with age. */
object UsageWindow {
    const val ALL = "all"
    const val DAYS_7 = "7"
    const val DAYS_30 = "30"
    const val DAYS_90 = "90"
    const val FADE = "fade"

    /** Launches per day are kept this long (the longest window). */
    const val KEEP_DAYS = 90

    /** In fading mode a launch counts half as much for every this many days that pass. */
    const val HALF_LIFE_DAYS = 14.0

    val options = listOf(
        ALL to "All time",
        DAYS_7 to "Last 7 days",
        DAYS_30 to "Last 30 days",
        DAYS_90 to "Last 90 days",
        FADE to "Fading: recent habits count more",
    )

    fun day(ms: Long): Long = ms / 86_400_000L

    /**
     * How much [usage] counts as of [nowMs] under [window]. Launches from before days were kept have no date, so only
     * the all-time mode counts them; the windowed modes fill up from the day they were switched on.
     */
    fun score(usage: AppUsage, window: String, nowMs: Long): Double {
        val today = day(nowMs)
        return when (window) {
            ALL -> usage.count.toDouble()
            FADE -> usage.days.entries.sumOf { (d, n) -> n * 0.5.pow((today - d) / HALF_LIFE_DAYS) }
            else -> {
                val limit = window.toIntOrNull() ?: return usage.count.toDouble()
                usage.days.entries.filter { today - it.key < limit }.sumOf { it.value }.toDouble()
            }
        }
    }
}
