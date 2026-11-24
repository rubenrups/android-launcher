package com.rubenrups.launcher.ui

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.content.ContentResolver
import android.database.ContentObserver
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.rubenrups.launcher.data.appSections
import com.rubenrups.launcher.data.CallContact
import com.rubenrups.launcher.data.topAppKeys
import com.rubenrups.launcher.data.DashboardModules
import com.rubenrups.launcher.data.LauncherSettings
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
import kotlin.math.roundToInt
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.produceState
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
import com.rubenrups.launcher.notifications.NotificationItem
import com.rubenrups.launcher.notifications.NotificationStore
import com.rubenrups.launcher.data.AppInfo
import java.util.Locale

private val CardShape = RoundedCornerShape(16.dp)

/** True while a card is drawn at half width, so it can shrink its text. */
private val LocalHalfCard = compositionLocalOf { false }

/** Rough relative heights, used to pack the cards into two columns on wide screens. */
private val CardWeight = mapOf(
    DashboardModules.GLUCOSE to 1.3f,
    DashboardModules.MEDIA to 2.2f,
    DashboardModules.VOLUME to 1.2f,
    DashboardModules.NAVIGATION to 1.1f,
    DashboardModules.APPS to 1.6f,
    DashboardModules.CALLS to 1.5f,
    DashboardModules.WEATHER to 1.5f,
    DashboardModules.QUICK to 1.4f,
    DashboardModules.STATS to 1.0f,
    DashboardModules.CLOCK to 1.0f,
    DashboardModules.RECENTS to 1.6f,
    DashboardModules.NOTIFICATIONS to 1.8f,
)

/**
 * Car mode: a big, glanceable screen with glucose, navigation, media and a few extras. Cards follow the order set
 * in settings (or by holding one); on wide screens they pack into two columns.
 */
@Composable
fun DashboardScreen(viewModel: LauncherViewModel, onClose: () -> Unit, onOpenSettings: () -> Unit) {
    // Car mode sees the settings with the active profile's gestures and card layout laid over them.
    val settings by viewModel.effectiveSettings.collectAsStateWithLifecycle()
    val media by NotificationStore.media.collectAsStateWithLifecycle()
    val notifications by NotificationStore.notifications.collectAsStateWithLifecycle()
    val listenerConnected by NotificationStore.listenerConnected.collectAsStateWithLifecycle()
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
    // Scrolled down and then left alone: after a while, glide back to the top so the cards you check most are in view
    // again. Touching or scrolling restarts the wait. The glide itself must not restart it: it makes the scroll state
    // "in progress" too, which used to cancel the glide after its first frame, so it never got anywhere.
    LaunchedEffect(settings.carAutoScrollTop, settings.carAutoScrollSeconds) {
        if (!settings.carAutoScrollTop) return@LaunchedEffect
        var timer: Job? = null
        var returning = false
        snapshotFlow { scroll.isScrollInProgress to scroll.value }.collect { (scrolling, position) ->
            if (returning) return@collect
            timer?.cancel()
            if (!scrolling && position > 0) {
                timer = launch {
                    delay(settings.carAutoScrollSeconds * 1000L)
                    returning = true
                    try {
                        scroll.animateScrollTo(0)
                    } finally {
                        returning = false
                    }
                }
            }
        }
    }
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val currentSettings by rememberUpdatedState(settings)

    // The cards that currently have something to show, in the chosen order.
    val visible = settings.dashboardOrder.filter { key ->
        key !in settings.dashboardHidden && when (key) {
            DashboardModules.WEATHER -> hours.isNotEmpty()
            DashboardModules.RECENTS -> settings.topAppKeys(settings.carRecentsSource).isNotEmpty()
            // Empty and connected: nothing to show, so the card stays out of the way.
            DashboardModules.NOTIFICATIONS -> notifications.isNotEmpty() || !listenerConnected
            else -> true
        }
    }
    // Taps on empty cards and the hold menu both land on the group of settings that configures that card.
    val openSettingsFor: (String) -> (() -> Unit) = { card ->
        {
            val (page, section) = settingsTargetFor(card)
            viewModel.openSettings(page, section)
        }
    }
    val content: @Composable (String) -> Unit = { key ->
        when (key) {
            DashboardModules.GLUCOSE -> GlucoseCard(viewModel, openSettingsFor(key))
            DashboardModules.MEDIA -> CarMediaCard(media, settings)
            DashboardModules.VOLUME -> VolumeCard(settings)
            // While a route is active: the next turn. Otherwise: one-tap destinations.
            DashboardModules.NAVIGATION ->
                navigation?.let { NavigationCard(it, big = true) } ?: NavigateCard(settings.destinations, openSettingsFor(key))
            DashboardModules.WEATHER -> HourlyCard(hours, settings.weatherCity)
            DashboardModules.APPS -> QuickAppsCard(viewModel, settings.carApps, openSettingsFor(key))
            DashboardModules.CALLS -> CallsCard(settings.callContacts, settings.callDirect, openSettingsFor(key))
            DashboardModules.QUICK -> QuickSettingsCard(viewModel)
            DashboardModules.NOTIFICATIONS -> NotificationsCard(notifications, listenerConnected, settings.carNotificationCount, openSettingsFor(key))
            DashboardModules.CLOCK -> ClockCard()
            DashboardModules.RECENTS -> RecentAppsCard(viewModel, settings)
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
            isHalf = key in settings.dashboardHalf,
            onToggleHalf = { viewModel.setDashboardCardHalf(key, key !in settings.dashboardHalf) },
            onOpenSettings = openSettingsFor(key),
        ) {
            CompositionLocalProvider(LocalHalfCard provides (key in settings.dashboardHalf)) { content(key) }
        }
    }
    // Half-width cards that follow each other form a group, and the group flows into two columns (each card goes under
    // whichever column is shorter) so a short card never leaves a hole beside a tall one. Everything else is a row of its
    // own. On wide screens the whole dashboard is already stacked into two columns, so there a group is just a pair.
    val units = ArrayList<List<String>>()
    run {
        var i = 0
        while (i < visible.size) {
            val key = visible[i]
            if (key in settings.dashboardHalf) {
                val group = ArrayList<String>()
                while (i < visible.size && visible[i] in settings.dashboardHalf && (!wide || group.size < 2)) {
                    group.add(visible[i]); i++
                }
                units.add(group)
            } else {
                units.add(listOf(key)); i += 1
            }
        }
    }
    val unit: @Composable (List<String>) -> Unit = { keys ->
        if (keys.size >= 2) {
            TwoColumnFlow(gap = 14.dp) { keys.forEach { k -> key(k) { Box { slot(k) } } } }
        } else if (keys[0] in settings.dashboardHalf) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.weight(1f)) { key(keys[0]) { slot(keys[0]) } }
                Spacer(Modifier.weight(1f))
            }
        } else {
            key(keys[0]) { slot(keys[0]) }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF0F1215).copy(alpha = settings.dashboardOpacity / 100f))
            // Tapping empty space closes it (cards swallow their own taps, see Card). A double or triple tap runs its
            // own action when one is set; the close then waits a moment to be sure it was a single tap.
            .multiTap(
                enabled = true,
                maxTaps = {
                    val s = currentSettings
                    if (s.carTripleTapAction.isNotBlank()) 3 else if (s.carDoubleTapAction.isNotBlank()) 2 else 1
                },
                onLongPress = {},
                onTaps = { count, _ ->
                    val s = currentSettings
                    when (count) {
                        1 -> onClose()
                        else -> {
                            val action = if (count == 2) s.carDoubleTapAction else s.carTripleTapAction
                            if (action.isNotBlank()) {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                runCarAction(action, viewModel, context)
                            }
                        }
                    }
                },
            )
            .windowInsetsPadding(launcherInsets())
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
            val left = ArrayList<List<String>>()
            val right = ArrayList<List<String>>()
            var leftHeight = 0f
            var rightHeight = 0f
            units.forEach { keys ->
                val weight = keys.maxOf { CardWeight[it] ?: 1f }
                if (leftHeight <= rightHeight) { left.add(keys); leftHeight += weight } else { right.add(keys); rightHeight += weight }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) { left.forEach { unit(it) } }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) { right.forEach { unit(it) } }
            }
        } else {
            units.forEach { unit(it) }
        }
        Text(
            "Swipe to control car mode · hold a card to move or hide it",
            color = Color.White.copy(alpha = 0.3f),
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 4.dp, bottom = 24.dp),
        )
    }
}

/** Where a card is configured: the settings page (a page name in the settings screen) and the group on it. */
private fun settingsTargetFor(card: String): Pair<String, String> = when (card) {
    DashboardModules.GLUCOSE -> "GLUCOSE" to "LibreLinkUp"
    // The weather city, battery and calendar toggles live with the home-screen header settings.
    DashboardModules.WEATHER, DashboardModules.STATS -> "HOME" to "Header"
    DashboardModules.MEDIA -> "DASHBOARD" to "Media card"
    DashboardModules.VOLUME -> "DASHBOARD" to "Volume card"
    DashboardModules.NAVIGATION -> "DASHBOARD" to "Navigation places"
    DashboardModules.APPS -> "DASHBOARD" to "Quick launch apps"
    DashboardModules.CALLS -> "DASHBOARD" to "Call buttons"
    DashboardModules.NOTIFICATIONS -> "DASHBOARD" to "Notifications card"
    else -> "DASHBOARD" to "Cards, in order"
}

/** Wraps a card so holding it opens Move earlier / Move later / Hide. */
@Composable
private fun CardSlot(
    hasEarlier: Boolean,
    hasLater: Boolean,
    onMoveEarlier: () -> Unit,
    onMoveLater: () -> Unit,
    onHide: () -> Unit,
    isHalf: Boolean,
    onToggleHalf: () -> Unit,
    onOpenSettings: () -> Unit,
    content: @Composable () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box(Modifier.onLongPressAbove { menuOpen = true }) {
        content()
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            if (hasEarlier) DropdownMenuItem(text = { Text("Move earlier") }, onClick = { menuOpen = false; onMoveEarlier() })
            if (hasLater) DropdownMenuItem(text = { Text("Move later") }, onClick = { menuOpen = false; onMoveLater() })
            DropdownMenuItem(
                text = { Text(if (isHalf) "Full width" else "Half width") },
                onClick = { menuOpen = false; onToggleHalf() },
            )
            DropdownMenuItem(text = { Text("Settings for this card") }, onClick = { menuOpen = false; onOpenSettings() })
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
            .padding(if (LocalHalfCard.current) 12.dp else 16.dp),
    ) { content() }
}

/**
 * Lays its children out in two equal columns, in order: each child goes under whichever column is currently shorter, using
 * the real measured heights. Half-width cards use it so the space beside a short card is filled by the next card instead of
 * staying empty.
 */
@Composable
private fun TwoColumnFlow(gap: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    androidx.compose.ui.layout.Layout(content = content, modifier = modifier) { measurables, constraints ->
        val gapPx = gap.roundToPx()
        val columnWidth = ((constraints.maxWidth - gapPx) / 2).coerceAtLeast(0)
        val childConstraints = androidx.compose.ui.unit.Constraints(minWidth = columnWidth, maxWidth = columnWidth)
        val placeables = measurables.map { it.measure(childConstraints) }
        val bottoms = intArrayOf(0, 0)
        val columns = IntArray(placeables.size)
        val tops = IntArray(placeables.size)
        placeables.forEachIndexed { index, placeable ->
            val column = if (bottoms[0] <= bottoms[1]) 0 else 1
            columns[index] = column
            tops[index] = bottoms[column]
            bottoms[column] += placeable.height + gapPx
        }
        val height = (maxOf(bottoms[0], bottoms[1]) - gapPx).coerceAtLeast(0)
        layout(constraints.maxWidth, height) {
            placeables.forEachIndexed { index, placeable ->
                placeable.placeRelative(columns[index] * (columnWidth + gapPx), tops[index])
            }
        }
    }
}

/** Tiles per row inside a card: fewer when the card is half width, so each tile keeps a usable size. */
@Composable
private fun tilesPerRow(): Int = if (LocalHalfCard.current) HALF_TILES_PER_ROW else NAV_TILES_PER_ROW

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
    // Just the value and arrow; tapping the card opens the Libre app.
    val half = LocalHalfCard.current
    Card(onClick = { viewModel.openGlucoseApp() }) {
        Text(
            "Glucose · ${glucoseUnit(settings.glucoseMmol)}",
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 13.sp,
        )
        if (latest == null) {
            Text(if (state.error == null) "Loading…" else "No reading", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Light)
        } else {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    formatGlucose(latest.mgdl, settings.glucoseMmol) + " " + trendArrow(latest.trend),
                    color = if (stale) Color.White.copy(alpha = 0.5f) else glucoseColor(latest.mgdl, settings),
                    fontSize = if (half) 34.sp else 44.sp,
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
    }
}

@Composable
private fun StatLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White.copy(alpha = 0.55f), fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(value, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Medium)
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
    val half = LocalHalfCard.current
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            // The turn arrow from the navigation app's own notification, when we managed to read it.
            nav.maneuver?.let { picture ->
                val image = remember(picture) { picture.asImageBitmap() }
                androidx.compose.foundation.Image(
                    bitmap = image,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 14.dp, top = 6.dp).size(if (!big) 48.dp else if (half) 56.dp else 88.dp),
                )
            }
            Column(Modifier.weight(1f)) {
                val headline = listOf(nav.title, nav.text).filter { it.isNotBlank() }.joinToString(" · ")
                Text(headline, color = Color.White, fontSize = if (!big) 17.sp else if (half) 18.sp else 26.sp, fontWeight = FontWeight.Medium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                if (nav.summary.isNotBlank()) {
                    Text(nav.summary, color = Color.White.copy(alpha = 0.75f), fontSize = if (!big) 14.sp else if (half) 14.sp else 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun StatTiles(battery: Int, weather: String?, event: String?) {
    val context = LocalContext.current
    val free = remember { freeStorage(context) }
    val half = LocalHalfCard.current
    Card {
        if (half) {
            // Too narrow for three columns: one line per fact, label left and value right.
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (battery >= 0) StatLine("Battery", "$battery%")
                weather?.let { StatLine("Weather", it) }
                free?.let { StatLine("Free space", it) }
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                if (battery >= 0) Stat("Battery", "$battery%", 24.sp)
                weather?.let { Stat("Weather", it, 24.sp) }
                free?.let { Stat("Free space", it, 24.sp) }
            }
        }
        event?.let {
            Text(
                "📅 $it", color = Color.White, fontSize = if (half) 15.sp else 18.sp,
                modifier = Modifier.padding(top = if (half) 8.dp else 12.dp), maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
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
        // Half width fits three hours across, so the twelve hours wrap onto a second line instead of being squeezed.
        val half = LocalHalfCard.current
        (if (half) hours.chunked(HALF_HOURS_PER_ROW) else listOf(hours)).forEachIndexed { index, rowHours ->
            if (index > 0) Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = if (half) Arrangement.SpaceEvenly else Arrangement.SpaceBetween) {
                rowHours.forEach { point ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(point.time, color = Color.White.copy(alpha = 0.6f), fontSize = if (half) 12.sp else 13.sp)
                        Text(point.emoji, fontSize = if (half) 22.sp else 26.sp)
                        Text("${point.temp}°", color = Color.White, fontSize = if (half) 17.sp else 20.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

/** Shortcuts to the phone's own quick settings. Android only lets apps flip the torch directly; the rest open the right screen. */
@Composable
private fun QuickSettingsCard(viewModel: LauncherViewModel) {
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

    // Lock and the system's quick-settings panel go through the gesture accessibility service; without it they
    // open Accessibility settings (see the Settings → Gestures page).
    val tiles = buildList<QuickTileSpec> {
        if (torchId != null) add(QuickTileSpec("🔦", "Torch", torchOn) { runCatching { camera.setTorchMode(torchId, !torchOn) } })
        add(QuickTileSpec("📶", "Internet") {
            open(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) Settings.Panel.ACTION_INTERNET_CONNECTIVITY else Settings.ACTION_WIFI_SETTINGS)
        })
        add(QuickTileSpec("🔵", "Bluetooth") { open(Settings.ACTION_BLUETOOTH_SETTINGS) })
        add(QuickTileSpec("🔔", "Sound") { open(Settings.ACTION_SOUND_SETTINGS) })
        add(QuickTileSpec("☀️", "Display") { open(Settings.ACTION_DISPLAY_SETTINGS) })
        add(QuickTileSpec("🔒", "Lock phone") { viewModel.launchByKey(GestureActions.LOCK) })
        add(QuickTileSpec("⚙️", "Quick panel") { viewModel.launchByKey(GestureActions.QUICK_SETTINGS) })
    }
    // Half width fits three tiles across; full width five.
    val perRow = if (LocalHalfCard.current) HALF_QUICK_TILES_PER_ROW else QUICK_TILES_PER_ROW
    Card {
        Text("Quick settings", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
        tiles.chunked(perRow).forEach { rowTiles ->
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowTiles.forEach { QuickTile(it.icon, it.label, it.active, it.onClick) }
                repeat(perRow - rowTiles.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** Media volume (and optionally the ringtone) with big minus and plus buttons, a slider, and a mute button. */
@Composable
private fun VolumeCard(settings: LauncherSettings) {
    val context = LocalContext.current
    val audio = remember { context.getSystemService(AudioManager::class.java) }
    Card {
        Text("Volume", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
        VolumeRow("Media", AudioManager.STREAM_MUSIC, audio, settings.carVolumeMute, context.contentResolver)
        if (settings.carVolumeRing) {
            Spacer(Modifier.height(4.dp))
            VolumeRow("Ring", AudioManager.STREAM_RING, audio, showMute = false, resolver = context.contentResolver)
        }
    }
}

@Composable
private fun VolumeRow(label: String, stream: Int, audio: AudioManager, showMute: Boolean, resolver: ContentResolver) {
    val half = LocalHalfCard.current
    val max = remember(stream) { audio.getStreamMaxVolume(stream).coerceAtLeast(1) }
    var level by remember { mutableIntStateOf(audio.getStreamVolume(stream)) }
    var muted by remember { mutableStateOf(audio.isStreamMute(stream)) }
    // The volume keys and other apps change it too: follow the system's volume settings.
    DisposableEffect(stream) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                level = audio.getStreamVolume(stream)
                muted = audio.isStreamMute(stream)
            }
        }
        resolver.registerContentObserver(android.provider.Settings.System.CONTENT_URI, true, observer)
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    fun set(value: Int) {
        // Refused (for example the ringtone while do-not-disturb is on and not allowed): leave the level as it is.
        runCatching { audio.setStreamVolume(stream, value.coerceIn(0, max), 0) }
        level = audio.getStreamVolume(stream)
        muted = audio.isStreamMute(stream)
    }
    // Half width: the name goes on its own line and the buttons and mute are smaller, so the slider keeps some length.
    if (half) Text(label, color = Color.White, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (!half) Text(label, color = Color.White, fontSize = 15.sp, modifier = Modifier.width(56.dp))
        Text("−", color = Color.White, fontSize = if (half) 24.sp else 30.sp, modifier = Modifier.accessibleButton("Lower $label volume") { set(level - 1) }.padding(horizontal = if (half) 8.dp else 14.dp, vertical = 6.dp))
        Slider(
            value = if (muted) 0f else level.toFloat(),
            onValueChange = { set(it.roundToInt()) },
            valueRange = 0f..max.toFloat(),
            colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent, inactiveTrackColor = Color.White.copy(alpha = 0.2f)),
            modifier = Modifier.weight(1f).semantics { contentDescription = "$label volume" },
        )
        Text("+", color = Color.White, fontSize = if (half) 24.sp else 30.sp, modifier = Modifier.accessibleButton("Raise $label volume") { set(level + 1) }.padding(horizontal = if (half) 8.dp else 14.dp, vertical = 6.dp))
        if (showMute) {
            Text(
                if (muted) "🔇" else "🔈",
                fontSize = if (half) 20.sp else 24.sp,
                modifier = Modifier
                    .accessibleButton(if (muted) "Unmute $label" else "Mute $label") {
                        runCatching { audio.adjustStreamVolume(stream, AudioManager.ADJUST_TOGGLE_MUTE, 0) }
                        muted = audio.isStreamMute(stream)
                        level = audio.getStreamVolume(stream)
                    }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
    }
}

private class QuickTileSpec(val icon: String, val label: String, val active: Boolean = false, val onClick: () -> Unit)

@Composable
private fun RowScope.QuickTile(icon: String, label: String, active: Boolean = false, onClick: () -> Unit) {
    val half = LocalHalfCard.current
    Column(
        Modifier
            .weight(1f)
            .background(if (active) Color.White.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp, vertical = if (half) 10.dp else 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(icon, fontSize = if (half) 22.sp else 28.sp)
        Text(label, color = Color.White.copy(alpha = 0.8f), fontSize = if (half) 11.sp else 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A big media card for the car: large album art, large title, and big previous / play-pause / next buttons. */
@Composable
private fun CarMediaCard(media: MediaState?, settings: LauncherSettings) {
    if (media == null) {
        Card { Text("Nothing is playing", color = Color.White.copy(alpha = 0.6f), fontSize = 20.sp) }
        return
    }
    val context = LocalContext.current
    val controls = media.controller.transportControls
    // Half width: a smaller picture and text and smaller transport buttons; every control stays reachable.
    val half = LocalHalfCard.current
    Card(onClick = { openMediaApp(context, media.controller) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            media.art?.takeIf { settings.carMediaShowArt }?.let { art ->
                val picture = remember(art) { art.asImageBitmap() }
                androidx.compose.foundation.Image(
                    bitmap = picture,
                    contentDescription = null,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.padding(end = if (half) 10.dp else 16.dp).size(if (half) 56.dp else 96.dp).clip(RoundedCornerShape(if (half) 8.dp else 12.dp)),
                )
            }
            Column(Modifier.weight(1f)) {
                Text(media.title, color = Color.White, fontSize = if (half) 17.sp else 24.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (settings.carMediaShowArtist && media.artist.isNotBlank()) {
                    Text(media.artist, color = Color.White.copy(alpha = 0.75f), fontSize = if (half) 13.sp else 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        if (settings.carMediaSeekBar) MediaSeekBar(media, big = !half)
        Spacer(Modifier.height(if (half) 8.dp else 14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            TransportButton(TransportKind.PREVIOUS, if (half) 44.dp else 72.dp, filled = false, description = "Previous track") { controls.skipToPrevious() }
            TransportButton(
                if (media.isPlaying) TransportKind.PAUSE else TransportKind.PLAY, if (half) 60.dp else 96.dp, filled = true,
                description = if (media.isPlaying) "Pause" else "Play",
            ) {
                if (media.isPlaying) controls.pause() else controls.play()
            }
            TransportButton(TransportKind.NEXT, if (half) 44.dp else 72.dp, filled = false, description = "Next track") { controls.skipToNext() }
        }
        if (settings.carMediaShowExtras && media.customActions.isNotEmpty()) {
            Spacer(Modifier.height(if (half) 6.dp else 10.dp))
            // The player's own extras (Spotify: shuffle, add to liked songs…), kept smaller than the main controls.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                media.customActions.take(if (half) 2 else 3).forEach { action ->
                    CustomActionButton(media.controller.packageName, action, size = if (half) 36.dp else 48.dp) { controls.sendCustomAction(action, null) }
                }
            }
        }
    }
}

private enum class TransportKind { PREVIOUS, PLAY, PAUSE, NEXT }

/** A round transport button with a drawn icon, so it looks the same on every phone. */
@Composable
private fun TransportButton(kind: TransportKind, size: androidx.compose.ui.unit.Dp, filled: Boolean, description: String, onClick: () -> Unit) {
    val ink = if (filled) Color(0xFF14181B) else Color.White
    Box(
        Modifier
            .size(size)
            .background(if (filled) Color.White else Color.White.copy(alpha = 0.12f), CircleShape)
            .accessibleButton(description, onClick = onClick),
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
        val perRow = tilesPerRow()
        tiles.chunked(perRow).forEach { rowTiles ->
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                rowTiles.forEach { (icon, label, action) -> NavTile(icon, label, action) }
                // Keep tiles in a short last row the same width as the others.
                repeat(perRow - rowTiles.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun RowScope.NavTile(icon: String, label: String, onClick: () -> Unit) {
    val half = LocalHalfCard.current
    Column(
        Modifier
            .weight(1f)
            .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = if (half) 12.dp else 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(icon, fontSize = if (half) 26.sp else 34.sp)
        Text(label, color = Color.White, fontSize = if (half) 15.sp else 18.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private const val NAV_TILES_PER_ROW = 3
private const val HALF_TILES_PER_ROW = 2
private const val HALF_HOURS_PER_ROW = 3
private const val QUICK_TILES_PER_ROW = 5
private const val HALF_QUICK_TILES_PER_ROW = 3
private const val MAPS_PACKAGE = "com.google.android.apps.maps"

/** Runs a car-mode swipe: close, a media key, a launcher function, or an app. */
private fun runCarAction(action: String, viewModel: LauncherViewModel, context: Context) {
    when (action) {
        GestureActions.CLOSE -> viewModel.setDashboardOpen(false)
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
                color = Accent,
                fontSize = 18.sp,
                modifier = Modifier.clickable(onClick = onOpenSettings).padding(vertical = 10.dp),
            )
        }
        AppTiles(viewModel, apps)
    }
}

/** The apps used most recently (newest first), as big tiles. */
@Composable
private fun RecentAppsCard(viewModel: LauncherViewModel, settings: LauncherSettings) {
    val installed by viewModel.visibleApps.collectAsStateWithLifecycle()
    val byKey = remember(installed) { installed.associateBy { it.key } }
    val sections = settings.appSections(settings.carRecentsSource, tilesPerRow() * 2) { it in byKey }
        .map { (title, keys) -> title to keys.mapNotNull { byKey[it] } }
    if (sections.isEmpty()) return
    Card {
        sections.forEachIndexed { index, (title, apps) ->
            if (index > 0) Spacer(Modifier.height(12.dp))
            Text(title, color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
            AppTiles(viewModel, apps)
        }
    }
}

@Composable
private fun AppTiles(viewModel: LauncherViewModel, apps: List<AppInfo>) {
    val half = LocalHalfCard.current
    val perRow = tilesPerRow()
    apps.chunked(perRow).forEach { rowApps ->
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            rowApps.forEach { app ->
                Column(
                    Modifier
                        .weight(1f)
                        .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
                        .clickable { viewModel.launch(app) }
                        .padding(horizontal = 4.dp, vertical = if (half) 10.dp else 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    AppIcon(app, viewModel, if (half) 44.dp else 56.dp)
                    Spacer(Modifier.height(6.dp))
                    Text(app.label, color = Color.White, fontSize = if (half) 13.sp else 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            repeat(perRow - rowApps.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

/** A big clock and date for the dashboard. */
@Composable
private fun ClockCard() {
    val now by produceState(java.util.Date()) {
        while (true) {
            value = java.util.Date()
            kotlinx.coroutines.delay(10_000)
        }
    }
    val timeFormat = remember { java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT) }
    val dateFormat = remember { java.text.SimpleDateFormat("EEEE, MMM d", Locale.getDefault()) }
    val half = LocalHalfCard.current
    Card {
        Text(timeFormat.format(now), color = Color.White, fontSize = if (half) 40.sp else 72.sp, fontWeight = FontWeight.Light)
        Text(dateFormat.format(now), color = Color.White.copy(alpha = 0.7f), fontSize = if (half) 15.sp else 20.sp, maxLines = 1)
    }
}

/** Big round buttons for the people you call from the car. One tap dials (or opens the dialer, see settings). */
@Composable
private fun CallsCard(contacts: List<CallContact>, direct: Boolean, onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    fun call(contact: CallContact) {
        val uri = Uri.parse("tel:" + Uri.encode(contact.number))
        val permitted = context.checkSelfPermission(android.Manifest.permission.CALL_PHONE) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val action = if (direct && permitted) Intent.ACTION_CALL else Intent.ACTION_DIAL
        runCatching { context.startActivity(Intent(action, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
    Card {
        Text("📞 Call", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
        if (contacts.isEmpty()) {
            Text(
                "＋ Choose people to call",
                color = Accent,
                fontSize = 18.sp,
                modifier = Modifier.clickable(onClick = onOpenSettings).padding(vertical = 14.dp),
            )
        }
        val half = LocalHalfCard.current
        val perRow = tilesPerRow()
        contacts.chunked(perRow).forEach { rowContacts ->
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                rowContacts.forEach { contact ->
                    Column(
                        Modifier
                            .weight(1f)
                            .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
                            .clickable { call(contact) }
                            .padding(horizontal = 4.dp, vertical = if (half) 10.dp else 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(Modifier.size(if (half) 44.dp else 56.dp).background(Color(0xFF2E7D4F), CircleShape), contentAlignment = Alignment.Center) {
                            Text(
                                contact.name.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "📞",
                                color = Color.White,
                                fontSize = if (half) 20.sp else 26.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(contact.name, color = Color.White, fontSize = if (half) 13.sp else 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                repeat(perRow - rowContacts.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}


/** The latest notifications, big enough to read at a glance: tap to open, ✕ to clear, "Clear all" for the lot. */
@Composable
private fun NotificationsCard(items: List<NotificationItem>, connected: Boolean, count: Int, onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    val half = LocalHalfCard.current
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (items.isEmpty()) "Notifications" else "Notifications · ${items.size}",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 13.sp,
                modifier = Modifier.weight(1f),
            )
            if (items.isNotEmpty()) {
                Text(
                    "Clear all",
                    color = Accent,
                    fontSize = if (half) 13.sp else 15.sp,
                    modifier = Modifier.clickable { items.forEach { NotificationStore.dismiss(it.key) } }.padding(8.dp),
                )
            }
        }
        if (!connected) {
            Text(
                "Turn on notification access →",
                color = Accent,
                fontSize = 18.sp,
                modifier = Modifier.clickable(onClick = onOpenSettings).padding(vertical = 10.dp),
            )
        } else if (items.isEmpty()) {
            Text("No notifications", color = Color.White.copy(alpha = 0.6f), fontSize = 18.sp, modifier = Modifier.padding(vertical = 10.dp))
        }
        items.take(count).forEach { item ->
            key(item.key) {
                Row(
                    Modifier.fillMaxWidth().clickable { openNotification(context, item) }.padding(start = 4.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        if (item.title.isNotBlank()) {
                            Text(item.title, color = Color.White, fontSize = if (half) 16.sp else 20.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (item.text.isNotBlank()) {
                            Text(item.text, color = Color.White.copy(alpha = 0.75f), fontSize = if (half) 13.sp else 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Text(
                        "✕",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = if (half) 16.sp else 20.sp,
                        modifier = Modifier.clickable { NotificationStore.dismiss(item.key) }.padding(if (half) 10.dp else 14.dp),
                    )
                }
            }
        }
    }
}
