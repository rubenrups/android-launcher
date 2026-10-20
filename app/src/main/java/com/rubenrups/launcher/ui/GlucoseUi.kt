package com.rubenrups.launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rubenrups.launcher.data.GlucoseState
import com.rubenrups.launcher.data.GlucosePoint
import com.rubenrups.launcher.data.LauncherSettings
import kotlinx.coroutines.delay
import java.util.Locale

/** A reading older than this is shown greyed out: an old number looks exactly like a current one. */
internal const val GLUCOSE_STALE_MS = 10 * 60_000L
private const val MGDL_PER_MMOL = 18.016f

private val LowColor = Color(0xFFFF6B6B)
private val HighColor = Color(0xFFFFB74D)
private val InRangeColor = Color(0xFF7BE0A0)

internal fun formatGlucose(mgdl: Int, mmol: Boolean): String =
    if (mmol) String.format(Locale.US, "%.1f", mgdl / MGDL_PER_MMOL) else mgdl.toString()

internal fun glucoseUnit(mmol: Boolean) = if (mmol) "mmol/L" else "mg/dL"

/** LibreLinkUp trend codes: 1 falling fast … 5 rising fast. */
internal fun trendArrow(trend: Int): String = when (trend) {
    1 -> "↓"
    2 -> "↘"
    3 -> "→"
    4 -> "↗"
    5 -> "↑"
    else -> ""
}

internal fun glucoseColor(mgdl: Int, s: LauncherSettings): Color = when {
    mgdl < s.glucoseLow -> LowColor
    mgdl > s.glucoseHigh -> HighColor
    else -> InRangeColor
}

internal fun agoText(ms: Long): String {
    val minutes = (ms / 60_000L).coerceAtLeast(0)
    return when {
        minutes < 1 -> "now"
        minutes < 60 -> "$minutes min ago"
        else -> "${minutes / 60} h ago"
    }
}

/** Milliseconds since [timeMs], refreshed every 20 s so "2 min ago" and the stale greying keep up. */
@Composable
internal fun rememberAgeMs(timeMs: Long?): Long {
    val now by produceState(System.currentTimeMillis(), timeMs) {
        while (true) {
            value = System.currentTimeMillis()
            delay(20_000)
        }
    }
    return if (timeMs == null) 0 else now - timeMs
}

/** The always-visible line under the clock: "112 ↗ · 2 min ago". Tap opens the dashboard. */
@Composable
internal fun GlucoseChip(state: GlucoseState, settings: LauncherSettings, ink: Color, onClick: () -> Unit) {
    val latest = state.snapshot?.latest
    val age = rememberAgeMs(latest?.timeMs)
    val stale = latest != null && age > GLUCOSE_STALE_MS
    Row(
        Modifier.clickable(onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (latest == null) {
            Text(
                if (state.error != null) "🩸 no reading" else "🩸 …",
                color = ink.copy(alpha = 0.6f),
                fontSize = 16.sp,
            )
        } else {
            val tint = if (stale) ink.copy(alpha = 0.5f) else glucoseColor(latest.mgdl, settings)
            Text(
                formatGlucose(latest.mgdl, settings.glucoseMmol) + " " + trendArrow(latest.trend),
                color = tint,
                fontSize = 22.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                (if (stale) "⚠ " else "") + agoText(age),
                color = ink.copy(alpha = if (stale) 0.7f else 0.6f),
                fontSize = 13.sp,
            )
        }
    }
}

/** Line graph of the history with the target range shaded. [points] must be sorted by time. */
@Composable
internal fun GlucoseGraph(points: List<GlucosePoint>, settings: LauncherSettings, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        if (points.size < 2) return@Canvas
        val lo = minOf(points.minOf { it.mgdl }, settings.glucoseLow) - 10
        val hi = maxOf(points.maxOf { it.mgdl }, settings.glucoseHigh) + 10
        val t0 = points.first().timeMs
        val span = (points.last().timeMs - t0).coerceAtLeast(1L)
        fun x(t: Long) = (t - t0).toFloat() / span * size.width
        fun y(v: Int) = size.height - (v - lo).toFloat() / (hi - lo) * size.height

        drawRect(
            InRangeColor.copy(alpha = 0.12f),
            topLeft = Offset(0f, y(settings.glucoseHigh)),
            size = androidx.compose.ui.geometry.Size(size.width, y(settings.glucoseLow) - y(settings.glucoseHigh)),
        )
        for (i in 1 until points.size) {
            val a = points[i - 1]
            val b = points[i]
            drawLine(
                Color.White.copy(alpha = 0.45f),
                Offset(x(a.timeMs), y(a.mgdl)),
                Offset(x(b.timeMs), y(b.mgdl)),
                strokeWidth = 2.dp.toPx(),
            )
        }
        points.forEach { drawCircle(glucoseColor(it.mgdl, settings), 2.5.dp.toPx(), Offset(x(it.timeMs), y(it.mgdl))) }
    }
}
