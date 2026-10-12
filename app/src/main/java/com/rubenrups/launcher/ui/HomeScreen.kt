package com.rubenrups.launcher.ui

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.net.Uri
import android.media.session.MediaController
import android.os.BatteryManager
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.Settings
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rubenrups.launcher.DashboardEdge
import com.rubenrups.launcher.LauncherViewModel
import com.rubenrups.launcher.data.AppEntry
import com.rubenrups.launcher.data.AppInfo
import com.rubenrups.launcher.data.Entry
import com.rubenrups.launcher.data.FolderEntry
import com.rubenrups.launcher.data.LauncherSettings
import com.rubenrups.launcher.data.NextEvent
import com.rubenrups.launcher.data.SwipeAction
import com.rubenrups.launcher.expandNotificationShade
import com.rubenrups.launcher.notifications.MediaState
import com.rubenrups.launcher.notifications.NotificationItem
import com.rubenrups.launcher.notifications.NotificationStore
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private const val SWIPE_THRESHOLD_PX = 120f

/** The most the home content leans during a swipe, in pixels. */
private const val PULL_MAX_PX = 160f

/**
 * The screen has no side margin of its own, so favourite rows can be tappable edge to edge.
 * Everything else (clock, cards, hints) applies this margin itself.
 */
private val SidePadding = Modifier.padding(horizontal = 24.dp)

/** The colour of text drawn straight on the wallpaper: white, or near-black on a light wallpaper (optional). */
internal val LocalHomeInk = compositionLocalOf { Color.White }
private val DarkInk = Color(0xFF14181B)

/** Soft shadow so white text stays readable on bright wallpapers. */
private val HomeTextStyle = TextStyle(
    shadow = Shadow(color = Color.Black.copy(alpha = 0.55f), offset = Offset(0f, 2f), blurRadius = 8f),
)

@Composable
fun HomeScreen(
    viewModel: LauncherViewModel,
    onOpenDrawer: (focusSearch: Boolean) -> Unit,
    onDrawerDrag: (upPx: Float) -> Unit,
    onDrawerRelease: (open: Boolean) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val drawerOpen by viewModel.drawerOpen.collectAsStateWithLifecycle()
    val settingsOpen by viewModel.settingsOpen.collectAsStateWithLifecycle()
    val dashboardOpen by viewModel.dashboardOpen.collectAsStateWithLifecycle()
    // Dark ink only while the bare wallpaper is showing; the drawer, settings and dashboard are dark overlays.
    val darkInk = rememberLightWallpaper(settings.adaptiveText) && !drawerOpen && !settingsOpen && !dashboardOpen
    val view = LocalView.current
    SideEffect {
        // Status and navigation bar icons are drawn by the system: flip them with the text.
        view.context.findActivity()?.window?.let { window ->
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = darkInk
            controller.isAppearanceLightNavigationBars = darkInk
        }
    }
    CompositionLocalProvider(LocalHomeInk provides if (darkInk) DarkInk else Color.White) {
        HomeContent(viewModel, onOpenDrawer, onDrawerDrag, onDrawerRelease, onOpenSettings)
    }
}

@Composable
private fun HomeContent(
    viewModel: LauncherViewModel,
    onOpenDrawer: (focusSearch: Boolean) -> Unit,
    onDrawerDrag: (upPx: Float) -> Unit,
    onDrawerRelease: (open: Boolean) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val favourites by viewModel.favourites.collectAsStateWithLifecycle()
    val notifications by NotificationStore.notifications.collectAsStateWithLifecycle()
    val media by NotificationStore.media.collectAsStateWithLifecycle()
    val listenerConnected by NotificationStore.listenerConnected.collectAsStateWithLifecycle()
    val navigation by NotificationStore.navigation.collectAsStateWithLifecycle()
    val glucose by viewModel.glucose.collectAsStateWithLifecycle()
    val badgeCounts = remember(notifications) { notifications.groupingBy { it.packageName }.eachCount() }
    var editMode by remember { mutableStateOf(false) }
    // Where a long-press landed; non-null while the home-screen menu is showing.
    var homeMenuAt by remember { mutableStateOf<Offset?>(null) }
    val listScroll = rememberScrollState()
    val favouriteKeys = remember(favourites) { favourites.mapTo(HashSet()) { it.key } }
    val weather = rememberWeather(settings.showWeather, settings.weatherCity, settings.weatherFahrenheit)
    val nextEvent = rememberNextEvent(settings.showCalendar)
    var showWidgetPicker by remember { mutableStateOf(false) }

    val haptics = LocalHapticFeedback.current
    // The content leans after the finger during a configured swipe and springs back, so a swipe visibly "does something".
    var pullX by remember { mutableFloatStateOf(0f) }
    var pullY by remember { mutableFloatStateOf(0f) }
    var pulling by remember { mutableStateOf(false) }
    val shownPullX by animateFloatAsState(pullX, if (pulling) snap() else spring(dampingRatio = 0.55f, stiffness = 300f), label = "pullX")
    val shownPullY by animateFloatAsState(pullY, if (pulling) snap() else spring(dampingRatio = 0.55f, stiffness = 300f), label = "pullY")

    val currentSettings by rememberUpdatedState(settings)
    val currentOpenDrawer by rememberUpdatedState(onOpenDrawer)
    val currentDrawerDrag by rememberUpdatedState(onDrawerDrag)
    val currentDrawerRelease by rememberUpdatedState(onDrawerRelease)

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                // Long-press on empty space opens settings (child rows consume their own presses).
                .pointerInput(editMode) {
                    if (editMode) return@pointerInput
                    detectTapGestures(onLongPress = { homeMenuAt = it })
                }
                // Swipe up pulls the drawer up under the finger; swipe down and sideways run your settings.
                .pointerInput(editMode) {
                    if (editMode) return@pointerInput
                    var dx = 0f
                    var dy = 0f
                    var crossed = false
                    fun settle() {
                        pulling = false
                        pullX = 0f
                        pullY = 0f
                        crossed = false
                    }
                    detectDragGestures(
                        onDragStart = { dx = 0f; dy = 0f; crossed = false; pulling = true },
                        onDrag = { _, amount ->
                            dx += amount.x
                            dy += amount.y
                            if (dy < 0f && abs(dy) > abs(dx)) currentDrawerDrag(-dy)
                            val s = currentSettings
                            val horizontalKey = if (dx < 0f) s.swipeLeftApp else s.swipeRightApp
                            val wantsX = abs(dx) > abs(dy) && horizontalKey.isNotBlank()
                            val wantsY = dy > 0f && abs(dy) > abs(dx) && s.swipeDown != SwipeAction.NONE
                            pullX = if (wantsX) (dx * 0.35f).coerceIn(-PULL_MAX_PX, PULL_MAX_PX) else 0f
                            pullY = if (wantsY) (dy * 0.25f).coerceIn(0f, PULL_MAX_PX) else 0f
                            // A tick the moment the swipe is long enough to count.
                            val past = (wantsX && abs(dx) > SWIPE_THRESHOLD_PX) || (wantsY && dy > SWIPE_THRESHOLD_PX)
                            if (past && !crossed) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            crossed = past
                        },
                        onDragEnd = {
                            settle()
                            val vertical = abs(dy) > abs(dx) * 1.2f
                            val horizontal = abs(dx) > abs(dy) * 1.2f
                            if (vertical && dy < -SWIPE_THRESHOLD_PX) {
                                currentDrawerRelease(true)
                            } else {
                                currentDrawerRelease(false)
                                if (vertical && dy > SWIPE_THRESHOLD_PX) {
                                    when (currentSettings.swipeDown) {
                                        SwipeAction.NOTIFICATIONS -> expandNotificationShade(context)
                                        SwipeAction.SEARCH -> currentOpenDrawer(true)
                                        SwipeAction.DASHBOARD -> viewModel.setDashboardOpen(true, DashboardEdge.TOP)
                                        SwipeAction.NONE -> Unit
                                    }
                                } else if (horizontal && abs(dx) > SWIPE_THRESHOLD_PX) {
                                    val key = if (dx < 0f) currentSettings.swipeLeftApp else currentSettings.swipeRightApp
                                    if (key.isNotBlank()) viewModel.launchByKey(key, if (dx < 0f) DashboardEdge.RIGHT else DashboardEdge.LEFT)
                                }
                            }
                        },
                        onDragCancel = { settle(); currentDrawerRelease(false) },
                    )
                }
                .graphicsLayer {
                    translationX = shownPullX
                    translationY = shownPullY
                    alpha = 1f - (abs(shownPullX) / (PULL_MAX_PX * 4f)).coerceIn(0f, 0.4f)
                }
                .padding(vertical = 16.dp),
            horizontalAlignment = if (settings.alignEnd) Alignment.End else Alignment.Start,
        ) {
            // In landscape there is room beside the clock, so the media card moves up next to it.
            val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
            val mediaState = if (settings.showMedia) media else null
            val glucoseLine: @Composable () -> Unit = {
                if (settings.glucoseEnabled) {
                    GlucoseChip(glucose, settings, LocalHomeInk.current) { viewModel.openGlucoseApp() }
                }
            }
            val mediaBesideClock = landscape && settings.showClock && mediaState != null
            if (settings.showClock) {
                if (mediaBesideClock) {
                    Row(
                        SidePadding.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                    ) {
                        val clock: @Composable () -> Unit = {
                            Clock(settings.alignEnd, weather, settings.weatherCity, nextEvent, glucoseLine)
                        }
                        val card: @Composable () -> Unit = {
                            mediaState?.let { MediaCard(it, Modifier.weight(1f)) }
                        }
                        // Keep the clock on the side the list is aligned to.
                        if (settings.alignEnd) { card(); clock() } else { clock(); card() }
                    }
                } else {
                    Box(SidePadding) { Clock(settings.alignEnd, weather, settings.weatherCity, nextEvent, glucoseLine) }
                }
                Spacer(Modifier.height(16.dp))
            }
            if (settings.showNavigation) {
                navigation?.let { Box(SidePadding.fillMaxWidth()) { NavigationCard(it) }; Spacer(Modifier.height(8.dp)) }
            }
            if (settings.showNotifications) {
                if (!listenerConnected) Box(SidePadding) { EnableNotificationsPrompt() }
                Box(SidePadding.fillMaxWidth()) { NotificationList(notifications.take(3)) }
            }
            if (!mediaBesideClock) {
                mediaState?.let {
                    Box(SidePadding.fillMaxWidth()) { MediaCard(it) }
                    Spacer(Modifier.height(8.dp))
                }
            }
            settings.widgetIds.forEach { id ->
                key(id) { WidgetView(id, viewModel.widgets, Modifier.padding(horizontal = 24.dp, vertical = 4.dp)) }
            }

            // Top/bottom placement is its own setting, independent of left/right alignment.
            val favouritesContent: @Composable () -> Unit = {
                if (favourites.isEmpty()) {
                    Text(
                        "Swipe up and long-press an app\nto add it to your favourites",
                        color = LocalHomeInk.current.copy(alpha = 0.7f),
                        fontSize = 16.sp,
                        style = HomeTextStyle,
                        textAlign = if (settings.alignEnd) TextAlign.End else TextAlign.Start,
                        modifier = SidePadding,
                    )
                } else {
                    FavouritesList(
                        favourites = favourites,
                        badgeCounts = badgeCounts,
                        settings = settings,
                        favouriteKeys = favouriteKeys,
                        viewModel = viewModel,
                        editMode = editMode,
                        onEditModeChange = { editMode = it },
                        // A top-anchored list can run under the floating search button; keep the last rows clear of it.
                        bottomPadding = if (!settings.anchorBottom && settings.showSearchButton) 88.dp else 0.dp,
                        twoColumns = landscape && settings.twoColumnsLandscape,
                    )
                }
            }
            // Only intercept drags when the list actually overflows, so swipe-up still reaches the drawer.
            val scrollModifier = Modifier.verticalScroll(listScroll, enabled = !editMode && listScroll.maxValue > 0)

            if (settings.anchorBottom) {
                Box(
                    Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = if (settings.alignEnd) Alignment.BottomEnd else Alignment.BottomStart,
                ) {
                    Box(scrollModifier) { favouritesContent() }
                }
                Spacer(Modifier.height(8.dp))
            } else {
                Box(Modifier.weight(1f, fill = false).then(scrollModifier)) { favouritesContent() }
            }
        }

        if (showWidgetPicker) WidgetPickerDialog(viewModel, onDismiss = { showWidgetPicker = false })

        homeMenuAt?.let { at ->
            // A zero-size anchor at the finger, so the menu pops up where you pressed.
            Box(Modifier.offset { IntOffset(at.x.roundToInt(), at.y.roundToInt()) }) {
                DropdownMenu(expanded = true, onDismissRequest = { homeMenuAt = null }) {
                    DropdownMenuItem(text = { Text("Settings") }, onClick = {
                        homeMenuAt = null
                        onOpenSettings()
                    })
                    if (favourites.isNotEmpty()) {
                        DropdownMenuItem(text = { Text("Reorder favourites") }, onClick = {
                            homeMenuAt = null
                            editMode = true
                        })
                    }
                    DropdownMenuItem(text = { Text("Dashboard") }, onClick = {
                        homeMenuAt = null
                        viewModel.setDashboardOpen(true)
                    })
                    DropdownMenuItem(text = { Text("Add widget") }, onClick = {
                        homeMenuAt = null
                        showWidgetPicker = true
                    })
                    DropdownMenuItem(text = { Text("Change wallpaper") }, onClick = {
                        homeMenuAt = null
                        runCatching {
                            context.startActivity(
                                Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), "Wallpaper")
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    })
                }
            }
        }

        if (settings.showSearchButton && !editMode) {
            SearchButton(
                onClick = { onOpenDrawer(true) },
                modifier = Modifier
                    .align(if (settings.alignEnd) Alignment.BottomStart else Alignment.BottomEnd)
                    .windowInsetsPadding(WindowInsets.systemBars)
                    .padding(24.dp),
            )
        }
    }
}

/** Round floating button with a hand-drawn magnifier, so we don't need the icons library. */
@Composable
private fun SearchButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(56.dp)
            .background(Color.Black.copy(alpha = 0.35f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(22.dp)) {
            val stroke = 2.5.dp.toPx()
            val radius = size.minDimension * 0.32f
            val centre = Offset(size.width * 0.42f, size.height * 0.42f)
            drawCircle(Color.White, radius = radius, center = centre, style = Stroke(width = stroke))
            val handleStart = Offset(centre.x + radius * 0.71f, centre.y + radius * 0.71f)
            drawLine(
                Color.White,
                start = handleStart,
                end = Offset(size.width * 0.92f, size.height * 0.92f),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
internal fun rememberBatteryPercent(): Int {
    val context = LocalContext.current
    var percent by remember { mutableIntStateOf(-1) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                percent = if (level >= 0 && scale > 0) level * 100 / scale else -1
            }
        }
        // ACTION_BATTERY_CHANGED is sticky, so this also delivers the current value immediately.
        ContextCompat.registerReceiver(
            context, receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose { context.unregisterReceiver(receiver) }
    }
    return percent
}

@Composable
private fun Clock(alignEnd: Boolean, weather: String?, city: String, event: NextEvent?, glucoseLine: @Composable () -> Unit) {
    val ink = LocalHomeInk.current
    val context = LocalContext.current
    val now by produceState(Date()) {
        while (true) {
            value = Date()
            delay(15_000)
        }
    }
    val battery = rememberBatteryPercent()
    val dateFormat = remember { SimpleDateFormat("EEE, MMM d", Locale.getDefault()) }
    val timeFormat = remember { DateFormat.getTimeInstance(DateFormat.SHORT) }
    // Each piece is its own tap target: time -> clock app, date -> calendar, battery -> battery usage,
    // weather -> forecast, event -> that event.
    Column(horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start) {
        Text(
            timeFormat.format(now),
            color = ink,
            fontSize = 56.sp,
            fontWeight = FontWeight.Light,
            style = HomeTextStyle,
            modifier = Modifier.clickable { startSafely(context, Intent(AlarmClock.ACTION_SHOW_ALARMS)) },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            HeaderText(dateFormat.format(now)) {
                startSafely(context, Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR))
            }
            if (battery >= 0) {
                HeaderText("$battery%") { startSafely(context, Intent(Intent.ACTION_POWER_USAGE_SUMMARY)) }
            }
            if (weather != null) {
                HeaderText(weather) {
                    startSafely(
                        context,
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode("weather $city"))),
                    )
                }
            }
        }
        glucoseLine()
        if (event != null) {
            HeaderText(event.text) {
                startSafely(
                    context,
                    Intent(Intent.ACTION_VIEW)
                        .setData(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, event.eventId))
                        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, event.begin)
                        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, event.end),
                )
            }
        }
    }
}

@Composable
private fun FavouritesList(
    favourites: List<Entry>,
    badgeCounts: Map<String, Int>,
    settings: LauncherSettings,
    favouriteKeys: Set<String>,
    viewModel: LauncherViewModel,
    editMode: Boolean,
    onEditModeChange: (Boolean) -> Unit,
    bottomPadding: androidx.compose.ui.unit.Dp,
    twoColumns: Boolean,
) {
    val haptics = LocalHapticFeedback.current
    val iconSize = (settings.favouriteTextSp * 1.5f).dp
    // Fixed row height so a drag offset maps cleanly onto "how many rows have I moved".
    val rowHeight = iconSize + 14.dp
    val rowPx = with(LocalDensity.current) { rowHeight.toPx() }
    val currentFavourites by rememberUpdatedState(favourites)
    var dragKey by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var expandedKeys by remember { mutableStateOf(setOf<String>()) }
    var popupFolderKey by remember { mutableStateOf<String?>(null) }
    var renameFolderKey by remember { mutableStateOf<String?>(null) }
    var addAppsFolderId by remember { mutableStateOf<String?>(null) }
    var sortFolderKey by remember { mutableStateOf<String?>(null) }
    var iconFolderKey by remember { mutableStateOf<String?>(null) }
    val alignment = if (settings.alignEnd) Alignment.End else Alignment.Start

    Column(Modifier.fillMaxWidth(), horizontalAlignment = alignment) {
        if (editMode) {
            Text(
                "Drag to reorder  ·  Done",
                color = Color.White,
                fontSize = 14.sp,
                modifier = SidePadding
                    .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                    .clickable { onEditModeChange(false) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
            Spacer(Modifier.height(8.dp))
        }
        val renderEntry: @Composable (Entry) -> Unit = { entry ->
            // key() keeps the row (and its in-flight drag gesture) alive while it changes position.
            key(entry.key) {
                val dragging = dragKey == entry.key
                val expanded = !editMode && entry.key in expandedKeys
                val dragModifier = if (editMode) {
                    Modifier.pointerInput(entry.key) {
                        detectVerticalDragGestures(
                            onDragStart = {
                                dragKey = entry.key
                                dragOffset = 0f
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            onDragEnd = { dragKey = null; dragOffset = 0f },
                            onDragCancel = { dragKey = null; dragOffset = 0f },
                            onVerticalDrag = { change, delta ->
                                change.consume()
                                dragOffset += delta
                                val list = currentFavourites
                                val from = list.indexOfFirst { it.key == entry.key }
                                val to = (from + (dragOffset / rowPx).roundToInt()).coerceIn(0, list.lastIndex)
                                if (from >= 0 && to != from) {
                                    viewModel.moveFavourite(from, to)
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    dragOffset -= (to - from) * rowPx
                                }
                            },
                        )
                    }
                } else {
                    Modifier
                }
                Column(horizontalAlignment = alignment) {
                    FavouriteRow(
                        entry = entry,
                        badge = when (entry) {
                            is AppEntry -> badgeCounts[entry.app.packageName] ?: 0
                            is FolderEntry -> entry.apps.sumOf { badgeCounts[it.packageName] ?: 0 }
                        },
                        settings = settings,
                        iconSize = iconSize,
                        rowHeight = rowHeight,
                        editMode = editMode,
                        expanded = expanded,
                        modifier = dragModifier
                            .zIndex(if (dragging) 1f else 0f)
                            .graphicsLayer { translationY = if (dragging) dragOffset else 0f }
                            .alpha(if (dragging) 0.8f else 1f),
                        viewModel = viewModel,
                        onReorder = { onEditModeChange(true) },
                        onToggleFolder = {
                            if (settings.folderPopupOnHome) {
                                popupFolderKey = entry.key
                            } else {
                                expandedKeys = if (entry.key in expandedKeys) expandedKeys - entry.key else expandedKeys + entry.key
                            }
                        },
                        onRenameFolder = { renameFolderKey = entry.key },
                        onAddApps = { (entry as? FolderEntry)?.let { addAppsFolderId = it.folder.id } },
                        onSortApps = { sortFolderKey = entry.key },
                        onChangeIcon = { iconFolderKey = entry.key },
                    )
                    if (entry is FolderEntry && expanded) {
                        entry.apps.forEach { app ->
                            FolderChildRow(app, app.key in favouriteKeys, settings, iconSize, rowHeight, viewModel)
                        }
                    }
                }
            }
        }
        // Two columns only while browsing: reordering needs one straight list to drag along.
        if (twoColumns && !editMode && favourites.size > 1) {
            val split = (favourites.size + 1) / 2
            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) { favourites.take(split).forEach { renderEntry(it) } }
                Column(Modifier.weight(1f)) { favourites.drop(split).forEach { renderEntry(it) } }
            }
        } else {
            favourites.forEach { renderEntry(it) }
        }
        Spacer(Modifier.height(bottomPadding))
    }

    favourites.filterIsInstance<FolderEntry>().firstOrNull { it.key == popupFolderKey }?.let { folder ->
        FolderDialog(folder, viewModel, onDismiss = { popupFolderKey = null }, translucent = true)
    }
    favourites.filterIsInstance<FolderEntry>().firstOrNull { it.key == iconFolderKey }?.let { folder ->
        FolderIconDialog(folder, viewModel, onDismiss = { iconFolderKey = null })
    }
    favourites.filterIsInstance<FolderEntry>().firstOrNull { it.key == sortFolderKey }?.let { folder ->
        FolderSortDialog(folder, viewModel, onDismiss = { sortFolderKey = null })
    }
    addAppsFolderId?.let { id ->
        AppPickerDialog(id, viewModel, onDismiss = { addAppsFolderId = null })
    }
    favourites.filterIsInstance<FolderEntry>().firstOrNull { it.key == renameFolderKey }?.let { folder ->
        NameDialog(
            title = "Rename folder",
            initial = folder.label,
            confirmLabel = "Save",
            onConfirm = {
                viewModel.renameFolder(folder.folder.id, it)
                renameFolderKey = null
            },
            onDismiss = { renameFolderKey = null },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FavouriteRow(
    entry: Entry,
    badge: Int,
    settings: LauncherSettings,
    iconSize: androidx.compose.ui.unit.Dp,
    rowHeight: androidx.compose.ui.unit.Dp,
    editMode: Boolean,
    expanded: Boolean,
    modifier: Modifier,
    viewModel: LauncherViewModel,
    onReorder: () -> Unit,
    onToggleFolder: () -> Unit,
    onRenameFolder: () -> Unit,
    onAddApps: () -> Unit,
    onSortApps: () -> Unit,
    onChangeIcon: () -> Unit,
) {
    val ink = LocalHomeInk.current
    var menuOpen by remember { mutableStateOf(false) }
    val alignEnd = settings.alignEnd
    val icon: @Composable () -> Unit = {
        when (entry) {
            is AppEntry -> AppIcon(entry.app, viewModel, iconSize)
            is FolderEntry -> FolderGlyph(entry.apps.size, entry.folder.icon, size = iconSize)
        }
    }
    val handle: @Composable () -> Unit = {
        Text("≡", color = ink.copy(alpha = 0.7f), fontSize = 24.sp, style = HomeTextStyle)
    }

    Box(modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp, if (alignEnd) Alignment.End else Alignment.Start),
            // The whole row, margins included, is the touch target; the margin is padding *inside* it.
            modifier = Modifier
                .fillMaxWidth()
                .height(rowHeight)
                .then(
                    if (editMode) Modifier else Modifier.pressable(
                        onClick = {
                            when (entry) {
                                is AppEntry -> viewModel.launch(entry.app)
                                is FolderEntry -> onToggleFolder()
                            }
                        },
                        onLongClick = { menuOpen = true },
                    )
                )
                .padding(horizontal = 24.dp),
        ) {
            if (editMode && !alignEnd) handle()
            if (settings.showIcons && !alignEnd) icon()
            if (badge > 0 && alignEnd) Badge(badge)
            Column(horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start) {
                Text(
                    entry.label,
                    color = ink,
                    fontSize = settings.favouriteTextSp.sp,
                    fontWeight = FontWeight.Light,
                    style = HomeTextStyle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (entry is FolderEntry && entry.apps.isNotEmpty()) {
                    Text(
                        folderPreview(entry),
                        color = ink.copy(alpha = 0.8f),
                        fontSize = (settings.favouriteTextSp * 0.6f).sp,
                        style = HomeTextStyle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (badge > 0 && !alignEnd) Badge(badge)
            if (entry is FolderEntry && !editMode) {
                // Left-aligned rows span the screen, so park the arrow at the far edge.
                if (!alignEnd) Spacer(Modifier.weight(1f))
                Text(if (expanded) "▾" else "▸", color = ink.copy(alpha = 0.8f), fontSize = 16.sp, style = HomeTextStyle)
            }
            if (settings.showIcons && alignEnd) icon()
            if (editMode && alignEnd) handle()
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            if (entry is AppEntry) {
                AppShortcutItems(entry.app, viewModel) { menuOpen = false }
            }
            DropdownMenuItem(text = { Text("Reorder") }, onClick = {
                menuOpen = false
                onReorder()
            })
            DropdownMenuItem(text = { Text("Remove from favourites") }, onClick = {
                menuOpen = false
                viewModel.removeFavouriteKey(entry.key)
            })
            when (entry) {
                is AppEntry -> DropdownMenuItem(text = { Text("App info") }, onClick = {
                    menuOpen = false
                    viewModel.repository.openAppInfo(entry.app)
                })
                is FolderEntry -> {
                    DropdownMenuItem(text = { Text("Add apps") }, onClick = {
                        menuOpen = false
                        onAddApps()
                    })
                    DropdownMenuItem(text = { Text("Sort apps…") }, onClick = {
                        menuOpen = false
                        onSortApps()
                    })
                    DropdownMenuItem(text = { Text("Change icon…") }, onClick = {
                        menuOpen = false
                        onChangeIcon()
                    })
                    DropdownMenuItem(text = { Text("Rename") }, onClick = {
                        menuOpen = false
                        onRenameFolder()
                    })
                    DropdownMenuItem(text = { Text("Delete folder") }, onClick = {
                        menuOpen = false
                        viewModel.deleteFolder(entry.folder)
                    })
                }
            }
        }
    }
}

/** "WhatsApp, Telegram +1": the first two apps, then how many more. */
private fun folderPreview(entry: FolderEntry): String {
    val names = entry.apps.take(2).joinToString(", ") { it.label }
    val more = entry.apps.size - 2
    return if (more > 0) "$names +$more" else names
}

/** An app inside an expanded favourites folder; indented under its folder row. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderChildRow(
    app: AppInfo,
    isFavourite: Boolean,
    settings: LauncherSettings,
    iconSize: androidx.compose.ui.unit.Dp,
    rowHeight: androidx.compose.ui.unit.Dp,
    viewModel: LauncherViewModel,
) {
    val ink = LocalHomeInk.current
    var menuOpen by remember { mutableStateOf(false) }
    // Indented under its folder; like the folder row, the full width is tappable.
    val start = if (settings.alignEnd) 24.dp else 52.dp
    val end = if (settings.alignEnd) 52.dp else 24.dp
    Box(Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp, if (settings.alignEnd) Alignment.End else Alignment.Start),
            modifier = Modifier
                .fillMaxWidth()
                .height(rowHeight * 0.85f)
                .pressable(onClick = { viewModel.launch(app) }, onLongClick = { menuOpen = true })
                .padding(start = start, end = end),
        ) {
            if (settings.showIcons && !settings.alignEnd) AppIcon(app, viewModel, iconSize * 0.8f)
            Text(
                app.label,
                color = ink,
                fontSize = (settings.favouriteTextSp * 0.85f).sp,
                fontWeight = FontWeight.Light,
                style = HomeTextStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (settings.showIcons && settings.alignEnd) AppIcon(app, viewModel, iconSize * 0.8f)
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(if (isFavourite) "Remove from favourites" else "Add to favourites") },
                onClick = {
                    menuOpen = false
                    if (isFavourite) viewModel.removeFavourite(app) else viewModel.addFavourite(app)
                },
            )
            DropdownMenuItem(text = { Text("Remove from folder") }, onClick = {
                menuOpen = false
                viewModel.removeFromFolder(app)
            })
            DropdownMenuItem(text = { Text("App info") }, onClick = {
                menuOpen = false
                viewModel.repository.openAppInfo(app)
            })
        }
    }
}

@Composable
internal fun Badge(count: Int) {
    Box(
        Modifier
            .background(Color.White, RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(if (count > 9) "9+" else count.toString(), color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun EnableNotificationsPrompt() {
    val context = LocalContext.current
    Text(
        "Enable notification access →",
        color = Color.White,
        fontSize = 14.sp,
        modifier = Modifier
            .background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
            .clickable { openNotificationAccessSettings(context) }
            .padding(horizontal = 14.dp, vertical = 10.dp),
    )
    Spacer(Modifier.height(8.dp))
}

private fun openNotificationAccessSettings(context: Context) {
    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

@Composable
private fun NotificationList(items: List<NotificationItem>) {
    if (items.isEmpty()) return
    val context = LocalContext.current
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
            .padding(vertical = 4.dp),
    ) {
        items.forEach { item ->
            key(item.key) {
                SwipeAwayRow(onDismiss = { NotificationStore.dismiss(item.key) }) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable { openNotification(context, item) }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        if (item.title.isNotBlank()) {
                            Text(item.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (item.text.isNotBlank()) {
                            Text(item.text, color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
    Spacer(Modifier.height(8.dp))
}

/** Drag sideways past a threshold to dismiss; the row follows the finger and fades. Vertical drags pass through. */
@Composable
private fun SwipeAwayRow(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val currentDismiss by rememberUpdatedState(onDismiss)
    var offsetX by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    val shown by animateFloatAsState(offsetX, if (dragging) snap() else spring(), label = "swipeAway")
    val thresholdPx = with(LocalDensity.current) { 120.dp.toPx() }
    Box(
        Modifier
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { dragging = true },
                    onDragEnd = {
                        dragging = false
                        if (abs(offsetX) > thresholdPx) currentDismiss() else offsetX = 0f
                    },
                    onDragCancel = {
                        dragging = false
                        offsetX = 0f
                    },
                    onHorizontalDrag = { change, delta ->
                        change.consume()
                        offsetX += delta
                    },
                )
            }
            .graphicsLayer {
                translationX = shown
                alpha = 1f - (abs(shown) / (thresholdPx * 3f)).coerceIn(0f, 0.8f)
            },
    ) { content() }
}

@Composable
internal fun MediaCard(media: MediaState, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val controls = media.controller.transportControls
    Row(
        modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
            // Tapping the card opens the player; the transport buttons below handle their own taps.
            .clickable { openMediaApp(context, media.controller) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(media.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (media.artist.isNotBlank()) {
                Text(media.artist, color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        // The player's own extra buttons (Spotify: add to / remove from liked songs). Icons come from the player app.
        media.customActions.take(3).forEach { action ->
            CustomActionButton(media.controller.packageName, action) { controls.sendCustomAction(action, null) }
        }
        MediaButton("|◀") { controls.skipToPrevious() }
        MediaButton(if (media.isPlaying) "❚❚" else "▶") {
            if (media.isPlaying) controls.pause() else controls.play()
        }
        MediaButton("▶|") { controls.skipToNext() }
    }
}

/**
 * Opens the player behind [controller]: the screen the app itself registered for its session
 * (usually "now playing"), or failing that the app's normal launcher entry.
 */
private fun openMediaApp(context: Context, controller: MediaController) {
    val opened = controller.sessionActivity?.sendAllowingLaunch(context) == true
    if (!opened) {
        context.packageManager.getLaunchIntentForPackage(controller.packageName)?.let {
            runCatching { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
    }
}

/** One line of header text that opens something when tapped; the padding makes the target comfortable. */
@Composable
private fun HeaderText(text: String, onClick: () -> Unit) {
    val ink = LocalHomeInk.current
    Text(
        text,
        color = ink.copy(alpha = 0.9f),
        fontSize = 16.sp,
        style = HomeTextStyle,
        maxLines = 1,
        modifier = Modifier.clickable(onClick = onClick).padding(vertical = 4.dp),
    )
}

private fun startSafely(context: Context, intent: Intent) {
    // Not every phone has every app (no calendar, no battery screen…); a tap should just do nothing then.
    runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

@Composable
private fun MediaButton(label: String, onClick: () -> Unit) {
    Text(
        label,
        color = Color.White,
        fontSize = 18.sp,
        modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

/** A custom media action drawn with the icon its app supplied, or its name if that icon can't be loaded. */
@Composable
private fun CustomActionButton(packageName: String, action: android.media.session.PlaybackState.CustomAction, onClick: () -> Unit) {
    val context = LocalContext.current
    val icon = remember(packageName, action.icon) {
        runCatching {
            val resources = context.packageManager.getResourcesForApplication(packageName)
            androidx.core.content.res.ResourcesCompat.getDrawable(resources, action.icon, null)
                ?.toBitmap(width = 96, height = 96)?.asImageBitmap()
        }.getOrNull()
    }
    if (icon != null) {
        androidx.compose.foundation.Image(
            bitmap = icon,
            contentDescription = action.name.toString(),
            colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(Color.White),
            modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 4.dp).size(32.dp),
        )
    } else {
        MediaButton(action.name.toString().take(10), onClick)
    }
}
