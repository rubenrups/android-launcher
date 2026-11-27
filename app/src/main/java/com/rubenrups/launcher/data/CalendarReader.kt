package com.rubenrups.launcher.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import java.text.DateFormat
import java.util.Date

/** What the header shows, plus enough to open the event itself. */
data class NextEvent(val text: String, val eventId: Long, val begin: Long, val end: Long)

/** One line of the agenda: a timed event, or an all-day one ([allDay]) that overlaps today. */
data class AgendaEvent(val title: String, val begin: Long, val end: Long, val eventId: Long, val allDay: Boolean)

object CalendarReader {

    /** The intent that opens an event in the calendar app. */
    fun viewIntent(eventId: Long, begin: Long, end: Long): android.content.Intent =
        android.content.Intent(android.content.Intent.ACTION_VIEW)
            .setData(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId))
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, end)

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    /**
     * The next (or current) timed event within 24 hours as "Title · 10:00–17:30", or null.
     * All-day events are skipped: they would sit on the home screen all day. Blocking query.
     */
    fun nextEvent(context: Context): NextEvent? {
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
            CalendarContract.Instances.EVENT_ID,
        )
        var result: NextEvent? = null
        runCatching {
            context.contentResolver.query(uri, projection, null, null, "${CalendarContract.Instances.BEGIN} ASC")?.use { c ->
                while (result == null && c.moveToNext()) {
                    val allDay = c.getInt(3) == 1
                    val end = c.getLong(2)
                    if (allDay || end <= now) continue
                    val format = DateFormat.getTimeInstance(DateFormat.SHORT)
                    val title = c.getString(0)?.takeIf { it.isNotBlank() } ?: "(No title)"
                    val begin = c.getLong(1)
                    result = NextEvent(
                        text = "$title · ${format.format(Date(begin))}–${format.format(Date(end))}",
                        eventId = c.getLong(4),
                        begin = begin,
                        end = end,
                    )
                }
            }
        }
        return result
    }

    /**
     * The next [count] events within the next 36 hours, soonest first: events still running count, finished ones do not.
     * All-day events are only listed when [includeAllDay] is on (they sit first, since they started earlier). Blocking query.
     */
    fun upcoming(context: Context, count: Int, includeAllDay: Boolean): List<AgendaEvent> {
        if (!hasPermission(context)) return emptyList()
        val now = System.currentTimeMillis()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
            .also {
                ContentUris.appendId(it, now)
                ContentUris.appendId(it, now + 36 * 60 * 60 * 1000L)
            }
            .build()
        val projection = arrayOf(
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.EVENT_ID,
        )
        val events = ArrayList<AgendaEvent>()
        runCatching {
            context.contentResolver.query(uri, projection, null, null, "${CalendarContract.Instances.BEGIN} ASC")?.use { c ->
                while (events.size < count && c.moveToNext()) {
                    val allDay = c.getInt(3) == 1
                    val end = c.getLong(2)
                    if (end <= now || (allDay && !includeAllDay)) continue
                    events.add(
                        AgendaEvent(
                            title = c.getString(0)?.takeIf { it.isNotBlank() } ?: "(No title)",
                            begin = c.getLong(1),
                            end = end,
                            eventId = c.getLong(4),
                            allDay = allDay,
                        ),
                    )
                }
            }
        }
        return events
    }

    private const val DAY_MS = 24 * 60 * 60 * 1000L
}
