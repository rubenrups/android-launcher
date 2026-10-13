package com.rubenrups.launcher.ui

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.rubenrups.launcher.data.DashboardModules
import com.rubenrups.launcher.data.Destination
import com.rubenrups.launcher.data.HourlyPoint
import android.content.Intent
import android.os.Environment
import android.os.StatFs
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.platform.LocalHapticFeedback
import com.rubenrups.launcher.data.GestureActions
import com.rubenrups.launcher.expandNotificationShade
import kotlin.math.abs
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.key
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalView
import com.rubenrups.launcher.notifications.MediaState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
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

/** Rough relative heights, used to pack the cards into two columns on wide screens. */
private val CardWeight = mapOf(
    DashboardModules.GLUCOSE to 1.3f,
    DashboardModules.MEDIA to 2.2f,
    DashboardModules.NAVIGATION to 1.1f,
    DashboardModules.APPS to 1.6f,
    DashboardModules.WEATHER to 1.5f,
    DashboardModules.QUICK to 1.4f,
    DashboardModules.STATS to 1.0f,
)

/**
 * Car mode: a big, glanceable screen with glucose, navigation, media and a few extras. Cards follow the order set
 * in settings (or by holding one); on wide screens they pack into two columns.
 */
@Composable
fun DashboardScreen(viewModel: LauncherViewModel, onClose: () -> Unit, onOpenSettings: () -> Unit) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val media by NotificationStore.media.collectAsStateWithLifecycle()
    val navigation by NotificationStore.navigation.collectAsStateWithLifecycle()
    val battery = rememberBatteryPercent()
    val weather = rememberWeather(settings.showWeather, settings.weatherCity, settings.weatherFahrenheit)
    val nextEvent = rememberNextEvent(settings.showCalendar)
    val hours = rememberHourlyWeather(settings.showWeather, settings.weatherCity, settings.weatherFahrenheit)
    val wide = LocalConfiguration.current.screenWidthDp >= 600

    // Keep the screen on while car mode is up (it is meant to be glanced at, not tapped awake).
    val view = LocalView.current
    DisposableEffect(settings.keepScreenOn) {
        view.keepScreenOn = settings.keepScreenOn
        onDispose { view.keepScreenOn = false }
    }

    val scroll = rememberScrollState()
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val currentSettings by rememberUpdatedState(settings)

    // The cards that currently have something to show, in the chosen order.
    val visible = settings.dashboardOrder.filter { key ->
        key !in settings.dashboardHidden && when (key) {
            DashboardModules.WEATHER -> hours.isNotEmpty()
            else -> true
        }
    }
    val content: @Composable (String) -> Unit = { key ->
        when (key) {
            DashboardModules.GLUCOSE -> GlucoseCard(viewModel, onOpenSettings)
            DashboardModules.MEDIA -> CarMediaCard(media)
            // While a route is active: the next turn. Otherwise: one-tap destinations.
            DashboardModules.NAVIGATION ->
                navigation?.let { NavigationCard(it, big = true) } ?: NavigateCard(settings.destinations, onOpenSettings)
            DashboardModules.WEATHER -> HourlyCard(hours, settings.weatherCity)
            DashboardModules.APPS -> QuickAppsCard(viewModel, settings.carApps, onOpenSettings)
            DashboardModules.QUICK -> QuickSettingsCard()
            DashboardModules.STATS -> StatTiles(battery, weather, nextEvent?.text)
            else -> Unit
        }
    }
    val slot: @Composable (String) -> Unit = { key ->
        val index = visible.indexOf(key)
        CardSlot(
            hasEarlier = index > 0,
            hasLater = index < visible.lastIndex,
            onMoveEarlier = { viewModel.swapDashboardCards(key, visible[index - 1]) },
            onMoveLater = { viewModel.swapDashboardCards(key, visible[index + 1]) },
            onHide = { viewModel.setDashboardCardHidden(key, true) },
        ) { content(key) }
    }

    Column(
        Modifier
            .fillMaxSize()
            // Tapping empty space closes it (cards swallow their own taps, see Card).
            .background(Color(0xFF0F1215).copy(alpha = settings.dashboardOpacity / 100f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose)
            .windowInsetsPadding(WindowInsets.systemBars)
            // Swipes run the actions set in settings. Watched without consuming anything, so cards and scrolling still work.
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val startScroll = scroll.value
                    var dx = 0f
                    var dy = 0f
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        dx = change.position.x - down.position.x
                        dy = change.position.y - down.position.y
                        if (!change.pressed) break
                    }
                    val threshold = 100.dp.toPx()
                    val s = currentSettings
                    val action = when {
                        abs(dx) > abs(dy) * 1.5f && abs(dx) > threshold -> if (dx < 0f) s.carSwipeLeft else s.carSwipeRight
                        // A vertical swipe that also scrolled the list was a scroll, not a gesture.
                        abs(dy) > abs(dx) * 1.5f && abs(dy) > threshold && scroll.value == startScroll ->
                            if (dy < 0f) s.carSwipeUp else s.carSwipeDown
                        else -> GestureActions.NONE
                    }
                    if (action.isNotBlank()) {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        runCarAction(action, viewModel, context)
                    }
                }
            }
            .verticalScroll(scroll)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (wide) {
            // Auto-stack: each card goes into whichever column is currently shorter.
            val left = ArrayList<String>()
            val right = ArrayList<String>()
            var leftHeight = 0f
            var rightHeight = 0f
            visible.forEach { key ->
                val weight = CardWeight[key] ?: 1f
                if (leftHeight <= rightHeight) { left.add(key); leftHeight += weight } else { right.add(key); rightHeight += weight }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) { left.forEach { key(it) { slot(it) } } }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) { right.forEach { key(it) { slot(it) } } }
            }
        } else {
            visible.forEach { key(it) { slot(it) } }
        }
        Text(
            "Swipe to control car mode · hold a card to move or hide it",
            color = Color.White.copy(alpha = 0.3f),
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 4.dp, bottom = 24.dp),
        )
    }
}

/** Wraps a card so holding it opens Move earlier / Move later / Hide. */
@Composable
private fun CardSlot(
    hasEarlier: Boolean,
    hasLater: Boolean,
    onMoveEarlier: () -> Unit,
    onMoveLater: () -> Unit,
    onHide: () -> Unit,
    content: @Composable () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box(Modifier.onLongPressAbove { menuOpen = true }) {
        content()
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            if (hasEarlier) DropdownMenuItem(text = { Text("Move earlier") }, onClick = { menuOpen = false; onMoveEarlier() })
            if (hasLater) DropdownMenuItem(text = { Text("Move later") }, onClick = { menuOpen = false; onMoveLater() })
            DropdownMenuItem(text = { Text("Hide card") }, onClick = { menuOpen = false; onHide() })
        }
    }
}

@Composable
private fun Card(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.08f), CardShape)
            .let {
                if (onClick != null) it.clickable(onClick = onClick) else it.pointerInput(Unit) { detectTapGestures { } }
            }
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
    // Collapsed by default: the value and arrow are usually all you need. Tap the card for the graph and stats.
    var expanded by rememberSaveable { mutableStateOf(false) }
    Card(onClick = { expanded = !expanded }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Glucose · ${glucoseUnit(settings.glucoseMmol)}",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 13.sp,
                modifier = Modifier.weight(1f),
            )
            Text(if (expanded) "▴" else "▾", color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp)
        }
        if (latest == null) {
            Text(if (state.error == null) "Loading…" else "No reading", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Light)
        } else {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    formatGlucose(latest.mgdl, settings.glucoseMmol) + " " + trendArrow(latest.trend),
                    color = if (stale) Color.White.copy(alpha = 0.5f) else glucoseColor(latest.mgdl, settings),
                    fontSize = if (expanded) 56.sp else 44.sp,
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
        if (expanded) state.snapshot?.let { snapshot ->
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
        if (expanded) {
            Text(
                "Open the Libre app ›",
                color = Color(0xFF8AB4F8),
                fontSize = 14.sp,
                modifier = Modifier.clickable { viewModel.openGlucoseApp() }.padding(top = 10.dp, bottom = 2.dp),
            )
            Text(
                "Display only. Not for treatment decisions: check your meter or the Libre app.",
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun Stat(label: String, value: String, size: androidx.compose.ui.unit.TextUnit = 16.sp) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = Color.White, fontSize = size, fontWeight = FontWeight.Medium)
        Text(label, color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp)
    }
}

/** Next turn and trip summary from the navigation app's notification. Tap returns to the app. */
@Composable
internal fun NavigationCard(nav: NavigationState, modifier: Modifier = Modifier, big: Boolean = false) {
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
        Text(headline, color = Color.White, fontSize = if (big) 26.sp else 17.sp, fontWeight = FontWeight.Medium, maxLines = 3, overflow = TextOverflow.Ellipsis)
        if (nav.summary.isNotBlank()) {
            Text(nav.summary, color = Color.White.copy(alpha = 0.75f), fontSize = if (big) 18.sp else 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun StatTiles(battery: Int, weather: String?, event: String?) {
    val context = LocalContext.current
    val free = remember { freeStorage(context) }
    Card {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            if (battery >= 0) Stat("Battery", "$battery%", 24.sp)
            weather?.let { Stat("Weather", it, 24.sp) }
            free?.let { Stat("Free space", it, 24.sp) }
        }
        event?.let {
            Text("📅 $it", color = Color.White, fontSize = 18.sp, modifier = Modifier.padding(top = 12.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun freeStorage(context: Context): String? = runCatching {
    val stat = StatFs(Environment.getDataDirectory().path)
    String.format(Locale.US, "%.0f GB", stat.availableBytes / 1_000_000_000.0)
}.getOrNull()

/** The next 12 hours in two-hour steps: time, weather emoji, temperature. */
@Composable
private fun HourlyCard(hours: List<HourlyPoint>, city: String) {
    val context = LocalContext.current
    Card(
        onClick = {
            runCatching {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode("weather $city")))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        },
    ) {
        Text("Weather · $city", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            hours.forEach { point ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(point.time, color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
                    Text(point.emoji, fontSize = 26.sp)
                    Text("${point.temp}°", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

/** Shortcuts to the phone's own quick settings. Android only lets apps flip the torch directly; the rest open the right screen. */
@Composable
private fun QuickSettingsCard() {
    val context = LocalContext.current
    val camera = remember { context.getSystemService(CameraManager::class.java) }
    val torchId = remember {
        runCatching {
            camera.cameraIdList.firstOrNull { camera.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
        }.getOrNull()
    }
    var torchOn by remember { mutableStateOf(false) }
    DisposableEffect(camera) {
        val callback = object : CameraManager.TorchCallback() {
            override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                if (cameraId == torchId) torchOn = enabled
            }
        }
        runCatching { camera.registerTorchCallback(callback, null) }
        onDispose { runCatching { camera.unregisterTorchCallback(callback) } }
    }
    fun open(action: String) {
        runCatching { context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    Card {
        Text("Quick settings", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (torchId != null) {
                QuickTile("🔦", "Torch", active = torchOn) { runCatching { camera.setTorchMode(torchId, !torchOn) } }
            }
            QuickTile("📶", "Internet") {
                open(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) Settings.Panel.ACTION_INTERNET_CONNECTIVITY else Settings.ACTION_WIFI_SETTINGS)
            }
            QuickTile("🔵", "Bluetooth") { open(Settings.ACTION_BLUETOOTH_SETTINGS) }
            QuickTile("🔔", "Sound") { open(Settings.ACTION_SOUND_SETTINGS) }
            QuickTile("☀️", "Display") { open(Settings.ACTION_DISPLAY_SETTINGS) }
        }
    }
}

@Composable
private fun RowScope.QuickTile(icon: String, label: String, active: Boolean = false, onClick: () -> Unit) {
    Column(
        Modifier
            .weight(1f)
            .background(if (active) Color.White.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(icon, fontSize = 28.sp)
        Text(label, color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp, maxLines = 1)
    }
}

/** A big media card for the car: large album art, large title, and big previous / play-pause / next buttons. */
@Composable
private fun CarMediaCard(media: MediaState?) {
    if (media == null) {
        Card { Text("Nothing is playing", color = Color.White.copy(alpha = 0.6f), fontSize = 20.sp) }
        return
    }
    val context = LocalContext.current
    val controls = media.controller.transportControls
    Card(onClick = { openMediaApp(context, media.controller) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            media.art?.let { art ->
                val picture = remember(art) { art.asImageBitmap() }
                androidx.compose.foundation.Image(
                    bitmap = picture,
                    contentDescription = null,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.padding(end = 16.dp).size(96.dp).clip(RoundedCornerShape(12.dp)),
                )
            }
            Column(Modifier.weight(1f)) {
                Text(media.title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (media.artist.isNotBlank()) {
                    Text(media.artist, color = Color.White.copy(alpha = 0.75f), fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            TransportButton(TransportKind.PREVIOUS, 72.dp, filled = false) { controls.skipToPrevious() }
            TransportButton(if (media.isPlaying) TransportKind.PAUSE else TransportKind.PLAY, 96.dp, filled = true) {
                if (media.isPlaying) controls.pause() else controls.play()
            }
            TransportButton(TransportKind.NEXT, 72.dp, filled = false) { controls.skipToNext() }
        }
        if (media.customActions.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            // The player's own extras (Spotify: shuffle, add to liked songs…), kept smaller than the main controls.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                media.customActions.take(3).forEach { action ->
                    CustomActionButton(media.controller.packageName, action, size = 48.dp) { controls.sendCustomAction(action, null) }
                }
            }
        }
    }
}

private enum class TransportKind { PREVIOUS, PLAY, PAUSE, NEXT }

/** A round transport button with a drawn icon, so it looks the same on every phone. */
@Composable
private fun TransportButton(kind: TransportKind, size: androidx.compose.ui.unit.Dp, filled: Boolean, onClick: () -> Unit) {
    val ink = if (filled) Color(0xFF14181B) else Color.White
    Box(
        Modifier
            .size(size)
            .background(if (filled) Color.White else Color.White.copy(alpha = 0.12f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(size * 0.42f)) {
            val w = this.size.width
            val h = this.size.height
            when (kind) {
                TransportKind.PLAY -> drawPath(
                    Path().apply { moveTo(w * 0.15f, 0f); lineTo(w, h / 2); lineTo(w * 0.15f, h); close() },
                    ink,
                )
                TransportKind.PAUSE -> {
                    drawRect(ink, androidx.compose.ui.geometry.Offset(w * 0.1f, 0f), androidx.compose.ui.geometry.Size(w * 0.32f, h))
                    drawRect(ink, androidx.compose.ui.geometry.Offset(w * 0.58f, 0f), androidx.compose.ui.geometry.Size(w * 0.32f, h))
                }
                TransportKind.NEXT -> {
                    drawPath(Path().apply { moveTo(0f, 0f); lineTo(w * 0.8f, h / 2); lineTo(0f, h); close() }, ink)
                    drawRect(ink, androidx.compose.ui.geometry.Offset(w * 0.84f, 0f), androidx.compose.ui.geometry.Size(w * 0.16f, h))
                }
                TransportKind.PREVIOUS -> {
                    drawPath(Path().apply { moveTo(w, 0f); lineTo(w * 0.2f, h / 2); lineTo(w, h); close() }, ink)
                    drawRect(ink, androidx.compose.ui.geometry.Offset(0f, 0f), androidx.compose.ui.geometry.Size(w * 0.16f, h))
                }
            }
        }
    }
}

/** Shown instead of the route card when nothing is being navigated: your saved places and Maps, as big buttons. */
@Composable
private fun NavigateCard(destinations: List<Destination>, onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    fun navigateTo(address: String) {
        val uri = Uri.parse("google.navigation:q=" + Uri.encode(address) + "&mode=d")
        val viaMaps = Intent(Intent.ACTION_VIEW, uri).setPackage(MAPS_PACKAGE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        // Without the Maps app, let any navigation app that understands the link take it.
        if (runCatching { context.startActivity(viaMaps) }.isFailure) {
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
    }
    fun openMaps() {
        val launch = context.packageManager.getLaunchIntentForPackage(MAPS_PACKAGE)
            ?: Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0"))
        runCatching { context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    // Places with an address, then Maps; a hint tile to add places when there are none yet.
    val tiles = ArrayList<Triple<String, String, () -> Unit>>()
    destinations.filter { it.address.isNotBlank() }.forEach { place ->
        tiles.add(Triple(place.icon.ifBlank { "📍" }, place.name.ifBlank { place.address }, { navigateTo(place.address) }))
    }
    if (tiles.isEmpty()) tiles.add(Triple("＋", "Add places", onOpenSettings))
    tiles.add(Triple("🗺️", "Maps", { openMaps() }))

    Card {
        Text("🧭 Navigate", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
        tiles.chunked(NAV_TILES_PER_ROW).forEach { rowTiles ->
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                rowTiles.forEach { (icon, label, action) -> NavTile(icon, label, action) }
                // Keep tiles in a short last row the same width as the others.
                repeat(NAV_TILES_PER_ROW - rowTiles.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun RowScope.NavTile(icon: String, label: String, onClick: () -> Unit) {
    Column(
        Modifier
            .weight(1f)
            .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(icon, fontSize = 34.sp)
        Text(label, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private const val NAV_TILES_PER_ROW = 3
private const val MAPS_PACKAGE = "com.google.android.apps.maps"

/** Runs a car-mode swipe: close, a media key, a launcher function, or an app. */
private fun runCarAction(action: String, viewModel: LauncherViewModel, context: Context) {
    val controls = NotificationStore.media.value?.controller?.transportControls
    when (action) {
        GestureActions.CLOSE -> viewModel.setDashboardOpen(false)
        GestureActions.NEXT -> controls?.skipToNext()
        GestureActions.PREVIOUS -> controls?.skipToPrevious()
        GestureActions.PLAY_PAUSE -> {
            val media = NotificationStore.media.value
            if (media?.isPlaying == true) controls?.pause() else controls?.play()
        }
        GestureActions.DRAWER -> { viewModel.setDashboardOpen(false); viewModel.openDrawer(false) }
        GestureActions.SEARCH -> { viewModel.setDashboardOpen(false); viewModel.openDrawer(true) }
        GestureActions.NOTIFICATIONS -> expandNotificationShade(context)
        GestureActions.DASHBOARD -> Unit
        else -> viewModel.launchByKey(action)
    }
}

/** Big app buttons for the car: the apps chosen in settings (maps, music, phone…), three to a row. */
@Composable
private fun QuickAppsCard(viewModel: LauncherViewModel, keys: List<String>, onOpenSettings: () -> Unit) {
    val installed by viewModel.visibleApps.collectAsStateWithLifecycle()
    val byKey = remember(installed) { installed.associateBy { it.key } }
    val apps = keys.mapNotNull { byKey[it] }
    Card {
        Text("Apps", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
        if (apps.isEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text(
                "＋ Choose apps for quick launch",
                color = Color(0xFF8AB4F8),
                fontSize = 18.sp,
                modifier = Modifier.clickable(onClick = onOpenSettings).padding(vertical = 10.dp),
            )
        }
        apps.chunked(NAV_TILES_PER_ROW).forEach { rowApps ->
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                rowApps.forEach { app ->
                    Column(
                        Modifier
                            .weight(1f)
                            .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
                            .clickable { viewModel.launch(app) }
                            .padding(horizontal = 4.dp, vertical = 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        AppIcon(app, viewModel, 56.dp)
                        Spacer(Modifier.height(6.dp))
                        Text(app.label, color = Color.White, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                repeat(NAV_TILES_PER_ROW - rowApps.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
