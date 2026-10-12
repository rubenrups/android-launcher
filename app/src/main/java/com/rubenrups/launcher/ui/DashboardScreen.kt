package com.rubenrups.launcher.ui

import android.content.Context
import android.content.Intent
import android.os.Environment
import android.os.StatFs
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rubenrups.launcher.LauncherViewModel
import com.rubenrups.launcher.notifications.NavigationState
import com.rubenrups.launcher.notifications.NotificationStore
import java.util.Locale

private val CardShape = RoundedCornerShape(16.dp)

/** The second screen: glucose, navigation, media and a few quick stats, over a dimmed wallpaper. */
@Composable
fun DashboardScreen(viewModel: LauncherViewModel, onClose: () -> Unit, onOpenSettings: () -> Unit) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val glucose by viewModel.glucose.collectAsStateWithLifecycle()
    val media by NotificationStore.media.collectAsStateWithLifecycle()
    val navigation by NotificationStore.navigation.collectAsStateWithLifecycle()
    val battery = rememberBatteryPercent()
    val weather = rememberWeather(settings.showWeather, settings.weatherCity, settings.weatherFahrenheit)
    val nextEvent = rememberNextEvent(settings.showCalendar)
    val wide = LocalConfiguration.current.screenWidthDp >= 600

    val glucoseCard: @Composable () -> Unit = { GlucoseCard(viewModel, onOpenSettings) }
    val otherCards: @Composable () -> Unit = {
        navigation?.let { NavigationCard(it) }
        if (media != null) {
            MediaCard(media!!)
        } else {
            Card { Text("Nothing is playing", color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp) }
        }
        StatTiles(battery, weather, nextEvent?.text)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .windowInsetsPadding(WindowInsets.systemBars)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Dashboard", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Light, modifier = Modifier.weight(1f))
            Text("✕", color = Color.White, fontSize = 22.sp, modifier = Modifier.clickable(onClick = onClose).padding(12.dp))
        }
        if (wide) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) { glucoseCard() }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) { otherCards() }
            }
        } else {
            glucoseCard()
            otherCards()
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Card(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.45f), CardShape)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(16.dp),
    ) { content() }
}

@Composable
private fun GlucoseCard(viewModel: LauncherViewModel, onOpenSettings: () -> Unit) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val state by viewModel.glucose.collectAsStateWithLifecycle()

    if (!settings.glucoseEnabled) {
        Card(onClick = onOpenSettings) {
            Text("Glucose", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
            Text("Set up LibreLinkUp in Settings →", color = Color.White, fontSize = 16.sp, modifier = Modifier.padding(top = 4.dp))
        }
        return
    }
    val latest = state.snapshot?.latest
    val age = rememberAgeMs(latest?.timeMs)
    val stale = latest != null && age > GLUCOSE_STALE_MS
    Card {
        Text("Glucose · ${glucoseUnit(settings.glucoseMmol)}", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
        if (latest == null) {
            Text(if (state.error == null) "Loading…" else "No reading", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Light)
        } else {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    formatGlucose(latest.mgdl, settings.glucoseMmol) + " " + trendArrow(latest.trend),
                    color = if (stale) Color.White.copy(alpha = 0.5f) else glucoseColor(latest.mgdl, settings),
                    fontSize = 56.sp,
                    fontWeight = FontWeight.Light,
                )
                Text(
                    (if (stale) "⚠ " else "") + agoText(age),
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
            }
        }
        state.error?.let {
            Text(it, color = Color(0xFFFFB74D), fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
        }
        state.snapshot?.let { snapshot ->
            Spacer(Modifier.height(8.dp))
            GlucoseGraph(snapshot.history, settings, Modifier.fillMaxWidth().height(140.dp))
            val values = snapshot.history.map { it.mgdl }
            if (values.isNotEmpty()) {
                val inRange = values.count { it in settings.glucoseLow..settings.glucoseHigh } * 100 / values.size
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Stat("In range", "$inRange%")
                    Stat("Average", formatGlucose(values.average().toInt(), settings.glucoseMmol))
                    Stat("Low", formatGlucose(values.min(), settings.glucoseMmol))
                    Stat("High", formatGlucose(values.max(), settings.glucoseMmol))
                }
            }
        }
        Text(
            "Display only. Not for treatment decisions: check your meter or the Libre app.",
            color = Color.White.copy(alpha = 0.4f),
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        Text(label, color = Color.White.copy(alpha = 0.55f), fontSize = 11.sp)
    }
}

/** Next turn and trip summary from the navigation app's notification. Tap returns to the app. */
@Composable
internal fun NavigationCard(nav: NavigationState, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Card(
        modifier,
        onClick = {
            val opened = nav.contentIntent?.sendAllowingLaunch(context) == true
            if (!opened) {
                context.packageManager.getLaunchIntentForPackage(nav.packageName)?.let {
                    runCatching { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }
            }
        },
    ) {
        Text("🧭 Navigation", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
        val headline = listOf(nav.title, nav.text).filter { it.isNotBlank() }.joinToString(" · ")
        Text(headline, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (nav.summary.isNotBlank()) {
            Text(nav.summary, color = Color.White.copy(alpha = 0.75f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun StatTiles(battery: Int, weather: String?, event: String?) {
    val context = LocalContext.current
    val free = remember { freeStorage(context) }
    Card {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            if (battery >= 0) Stat("Battery", "$battery%")
            weather?.let { Stat("Weather", it) }
            free?.let { Stat("Free space", it) }
        }
        event?.let {
            Text("📅 $it", color = Color.White, fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun freeStorage(context: Context): String? = runCatching {
    val stat = StatFs(Environment.getDataDirectory().path)
    String.format(Locale.US, "%.0f GB", stat.availableBytes / 1_000_000_000.0)
}.getOrNull()
