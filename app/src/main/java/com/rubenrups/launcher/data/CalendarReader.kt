package com.rubenrups.launcher.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import java.text.DateFormat
import java.util.Date

object CalendarReader {

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    /**
     * The next (or current) timed event within 24 hours as "Title · 10:00–17:30", or null.
     * All-day events are skipped: they would sit on the home screen all day. Blocking query.
     */
    fun nextEvent(context: Context): String? {
        if (!hasPermission(context)) return null
        val now = System.currentTimeMillis()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
            .also {
                ContentUris.appendId(it, now)
                ContentUris.appendId(it, now + DAY_MS)
            }
            .build()
        val projection = arrayOf(
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
        )
        var result: String? = null
        runCatching {
            context.contentResolver.query(uri, projection, null, null, "${CalendarContract.Instances.BEGIN} ASC")?.use { c ->
                while (result == null && c.moveToNext()) {
                    val allDay = c.getInt(3) == 1
                    val end = c.getLong(2)
                    if (allDay || end <= now) continue
                    val format = DateFormat.getTimeInstance(DateFormat.SHORT)
                    val title = c.getString(0)?.takeIf { it.isNotBlank() } ?: "(No title)"
                    result = "$title · ${format.format(Date(c.getLong(1)))}–${format.format(Date(end))}"
                }
            }
        }
        return result
    }

    private const val DAY_MS = 24 * 60 * 60 * 1000L
}
