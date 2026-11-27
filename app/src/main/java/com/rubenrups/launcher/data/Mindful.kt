package com.rubenrups.launcher.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * A pause before an app opens, set per app: wait [pauseSeconds] before "Open" is possible, and optionally mention a
 * limit of [dailyLimit] opens per day (0 = no limit). With [strict] the app stays shut once the limit is reached.
 */
data class MindfulRule(val pauseSeconds: Int = 5, val dailyLimit: Int = 0, val strict: Boolean = false) {
    fun limitReached(opensToday: Int): Boolean = dailyLimit > 0 && opensToday >= dailyLimit

    /** The prompt may be passed ("Open anyway") unless the limit is reached and the rule is strict. */
    fun canOpen(opensToday: Int): Boolean = !(strict && limitReached(opensToday))
}

/** How often [usage] was launched on the day containing [nowMs] (needs the per-day counts kept since the usage window). */
fun AppUsage?.opensOnDayOf(nowMs: Long): Int = this?.days?.get(UsageWindow.day(nowMs)) ?: 0

object MindfulCodec {
    fun encode(rules: Map<String, MindfulRule>): String = JSONObject().apply {
        rules.forEach { (key, r) -> put(key, JSONArray().put(r.pauseSeconds).put(r.dailyLimit).put(if (r.strict) 1 else 0)) }
    }.toString()

    fun decode(json: String?): Map<String, MindfulRule> {
        if (json.isNullOrBlank()) return emptyMap()
        return runCatching {
            val o = JSONObject(json)
            o.keys().asSequence().associateWith { key ->
                val a = o.getJSONArray(key)
                MindfulRule(a.getInt(0).coerceIn(0, 60), a.getInt(1).coerceIn(0, 99), a.optInt(2, 0) == 1)
            }
        }.getOrDefault(emptyMap())
    }
}
