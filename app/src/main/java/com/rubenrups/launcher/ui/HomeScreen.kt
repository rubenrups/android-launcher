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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.TextButton
import com.rubenrups.launcher.data.UpdateInfo
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rubenrups.launcher.DashboardEdge
import com.rubenrups.launcher.LauncherViewModel
import com.rubenrups.launcher.data.AppEntry
import com.rubenrups.launcher.data.AppInfo
import com.rubenrups.launcher.data.Entry
import com.rubenrups.launcher.data.FolderEntry
import com.rubenrups.launcher.data.LauncherSettings
import com.rubenrups.launcher.data.NextEvent
import com.rubenrups.launcher.data.GestureActions
import com.rubenrups.launcher.data.HomeRecentsAlign
import com.rubenrups.launcher.data.UpdateState
import com.rubenrups.launcher.data.VirtualApps
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

/** Rows of favourites that always stay visible, however much sits above them. */
private const val MIN_FAVOURITE_ROWS = 3

/** A swipe shorter than this shows no label yet, so ordinary taps and scrolls stay uncluttered. */
private const val HINT_START_PX = 24f

private enum class SwipeDir { UP, DOWN, LEFT, RIGHT }

/** The label shown while swiping: what will happen, how far along the swipe is, and whether releasing now triggers it. */
private data class SwipeHint(val text: String, val dir: SwipeDir, val progress: Float, val armed: Boolean)

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
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()
    val allApps by viewModel.visibleApps.collectAsStateWithLifecycle()
    val focusActive by viewModel.focusActive.collectAsStateWithLifecycle()
    val badgeCounts = remember(notifications) { notifications.groupingBy { it.packageName }.eachCount() }
    var editMode by remember { mutableStateOf(false) }
    // Where a long-press landed; non-null while the home-screen menu is showing.
    var homeMenuAt by remember { mutableStateOf<Offset?>(null) }
    val listScroll = rememberScrollState()
    val favouriteKeys = remember(favourites) { favourites.mapTo(HashSet()) { it.key } }
    val weather = rememberWeather(settings.showWeather, settings.weatherCity, settings.weatherFahrenheit)
    val nextEvent = rememberNextEvent(settings.showCalendar)
    var showWidgetPicker by remember { mutableStateOf(false) }
    var notesFor by remember { mutableStateOf<UpdateInfo?>(null) }

    val haptics = LocalHapticFeedback.current
    // The content leans after the finger during a configured swipe and springs back, so a swipe visibly "does something".
    var pullX by remember { mutableFloatStateOf(0f) }
    var pullY by remember { mutableFloatStateOf(0f) }
    var pulling by remember { mutableStateOf(false) }
    var hint by remember { mutableStateOf<SwipeHint?>(null) }
    val shownPullX by animateFloatAsState(pullX, if (pulling) snap() else spring(dampingRatio = 0.55f, stiffness = 300f), label = "pullX")
    val shownPullY by animateFloatAsState(pullY, if (pulling) snap() else spring(dampingRatio = 0.55f, stiffness = 300f), label = "pullY")

    val currentSettings by rememberUpdatedState(settings)
    val currentOpenDrawer by rememberUpdatedState(onOpenDrawer)
    val currentDrawerDrag by rememberUpdatedState(onDrawerDrag)
    val currentDrawerRelease by rememberUpdatedState(onDrawerRelease)

    val homeRecents = remember(allApps, settings.recents, settings.showHomeRecents, settings.homeRecentsCount) {
        if (!settings.showHomeRecents) emptyList() else {
            val byKey = allApps.associateBy { it.key }
            settings.recents.mapNotNull { byKey[it] }.take(settings.homeRecentsCount)
        }
    }
    // Room the icon row takes at the bottom, so the favourites and the clock section stop above it.
    val recentsReserve = if (homeRecents.isEmpty()) 0.dp else homeRecentsRowHeight(settings) * homeRecentsRowCount(settings, homeRecents.size) + settings.homeRecentsBottomDp.dp

    BoxWithConstraints(Modifier.fillMaxSize()) {
        // The favourites always keep room for a few rows; everything above them (clock, notifications, media,
        // widgets) gets the rest and scrolls if it is taller, instead of squeezing the favourites away.
        val rowHeightForMin = (settings.favouriteTextSp * 1.5f).dp + 14.dp
        val landscapeNow = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
        val favouriteRows = if (favourites.isEmpty()) 1 else {
            val rows = if (landscapeNow && settings.twoColumnsLandscape) (favourites.size + 1) / 2 else favourites.size
            rows.coerceIn(1, MIN_FAVOURITE_ROWS)
        }
        val bars = WindowInsets.systemBars.asPaddingValues()
        val topSectionMax = (maxHeight - bars.calculateTopPadding() - bars.calculateBottomPadding() - 32.dp -
            rowHeightForMin * favouriteRows - 16.dp - recentsReserve).coerceAtLeast(120.dp)
        val topScroll = rememberScrollState()
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                // Long-press on empty space opens settings (child rows consume their own presses).
                .pointerInput(editMode) {
                    if (editMode) return@pointerInput
                    detectTapGestures(onLongPress = { homeMenuAt = it })
                }
                // Each direction runs the action chosen in settings. When swipe-up is the drawer, it follows the finger.
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
                        hint = null
                    }
                    fun directionOf(horizontalBias: Float): SwipeDir =
                        if (abs(dy) > abs(dx) * horizontalBias) {
                            if (dy < 0f) SwipeDir.UP else SwipeDir.DOWN
                        } else {
                            if (dx < 0f) SwipeDir.LEFT else SwipeDir.RIGHT
                        }
                    fun actionFor(dir: SwipeDir): String = with(currentSettings) {
                        when (dir) {
                            SwipeDir.UP -> swipeUp
                            SwipeDir.DOWN -> swipeDown
                            SwipeDir.LEFT -> swipeLeftApp
                            SwipeDir.RIGHT -> swipeRightApp
                        }
                    }
                    fun distanceOf(dir: SwipeDir) = if (dir == SwipeDir.UP || dir == SwipeDir.DOWN) abs(dy) else abs(dx)
                    detectDragGestures(
                        onDragStart = { dx = 0f; dy = 0f; crossed = false; pulling = true },
                        onDrag = { _, amount ->
                            dx += amount.x
                            dy += amount.y
                            val dir = directionOf(1f)
                            val action = actionFor(dir)
                            val drawerFollowsFinger = dir == SwipeDir.UP && action == GestureActions.DRAWER
                            if (drawerFollowsFinger) currentDrawerDrag(-dy)
                            val active = action.isNotBlank() && !drawerFollowsFinger
                            val distance = distanceOf(dir)
                            val horizontal = dir == SwipeDir.LEFT || dir == SwipeDir.RIGHT
                            pullX = if (active && horizontal) (dx * 0.35f).coerceIn(-PULL_MAX_PX, PULL_MAX_PX) else 0f
                            pullY = if (active && !horizontal) (dy * 0.25f).coerceIn(-PULL_MAX_PX, PULL_MAX_PX) else 0f
                            // A tick, and a firmer label, the moment the swipe is long enough to count.
                            val past = active && distance > SWIPE_THRESHOLD_PX
                            if (past && !crossed) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            crossed = past
                            hint = if (active && distance > HINT_START_PX) {
                                viewModel.actionHint(action)?.let {
                                    SwipeHint(it, dir, (distance / SWIPE_THRESHOLD_PX).coerceIn(0f, 1f), past)
                                }
                            } else {
                                null
                            }
                        },
                        onDragEnd = {
                            settle()
                            val clearlyVertical = abs(dy) > abs(dx) * 1.2f
                            val clearlyHorizontal = abs(dx) > abs(dy) * 1.2f
                            if (!clearlyVertical && !clearlyHorizontal) {
                                currentDrawerRelease(false)
                                return@detectDragGestures
                            }
                            val dir = directionOf(1.2f)
                            val action = actionFor(dir)
                            val longEnough = distanceOf(dir) > SWIPE_THRESHOLD_PX
                            if (dir == SwipeDir.UP && action == GestureActions.DRAWER) {
                                currentDrawerRelease(longEnough)
                            } else {
                                currentDrawerRelease(false)
                                if (longEnough && action.isNotBlank()) {
                                    val edge = when (dir) {
                                        SwipeDir.LEFT -> DashboardEdge.RIGHT
                                        SwipeDir.RIGHT -> DashboardEdge.LEFT
                                        SwipeDir.DOWN -> DashboardEdge.TOP
                                        SwipeDir.UP -> DashboardEdge.BOTTOM
                                    }
                                    when (action) {
                                        GestureActions.DRAWER -> currentOpenDrawer(false)
                                        GestureActions.SEARCH -> currentOpenDrawer(true)
                                        GestureActions.NOTIFICATIONS -> expandNotificationShade(context)
                                        else -> viewModel.launchByKey(action, edge)
                                    }
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
                .padding(top = 16.dp, bottom = 16.dp + recentsReserve),
            horizontalAlignment = if (settings.alignEnd) Alignment.End else Alignment.Start,
        ) {
            // In landscape there is room beside the clock, so the media card moves up next to it.
            val landscape = landscapeNow
            Column(
                Modifier.heightIn(max = topSectionMax).verticalScroll(topScroll, enabled = topScroll.maxValue > 0),
                horizontalAlignment = if (settings.alignEnd) Alignment.End else Alignment.Start,
            ) {
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
                            Clock(settings, weather, nextEvent, glucoseLine)
                        }
                        val card: @Composable () -> Unit = {
                            mediaState?.let { MediaCard(it, Modifier.weight(1f)) }
                        }
                        // Keep the clock on the side the list is aligned to.
                        if (settings.alignEnd) { card(); clock() } else { clock(); card() }
                    }
                } else {
                    Box(SidePadding) { Clock(settings, weather, nextEvent, glucoseLine) }
                }
                Spacer(Modifier.height(16.dp))
            }
            if (settings.showNavigation) {
                navigation?.let { Box(SidePadding.fillMaxWidth()) { NavigationCard(it) }; Spacer(Modifier.height(8.dp)) }
            }
            if (focusActive) {
                Text(
                    "🎯 Focus mode is on · tap to pause for an hour",
                    color = Color.White,
                    fontSize = 14.sp,
                    modifier = SidePadding
                        .background(Color.White.copy(alpha = 0.18f), RoundedCornerShape(12.dp))
                        .clickable { viewModel.pauseFocus(60) }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                )
                Spacer(Modifier.height(8.dp))
            }
            UpdateChip(
                updateState,
                settings.dismissedUpdate,
                onInstall = viewModel::installUpdate,
                onDismiss = viewModel::dismissUpdate,
                onShowNotes = { notesFor = it },
            )
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
            settings.widgetIds.forEachIndexed { index, id ->
                key(id) {
                    // Hold a widget for Move up / Move down / Remove.
                    var menuOpen by remember { mutableStateOf(false) }
                    Box {
                        WidgetView(
                            id,
                            viewModel.widgets,
                            Modifier.padding(horizontal = 24.dp, vertical = 4.dp).onLongPressAbove { menuOpen = true },
                        )
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            if (index > 0) {
                                DropdownMenuItem(text = { Text("Move up") }, onClick = {
                                    menuOpen = false
                                    viewModel.moveWidget(id, -1)
                                })
                            }
                            if (index < settings.widgetIds.lastIndex) {
                                DropdownMenuItem(text = { Text("Move down") }, onClick = {
                                    menuOpen = false
                                    viewModel.moveWidget(id, 1)
                                })
                            }
                            DropdownMenuItem(text = { Text("Remove widget") }, onClick = {
                                menuOpen = false
                                viewModel.removeWidget(id)
                            })
                        }
                    }
                }
            }
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

        hint?.let { h ->
            val alignment = when (h.dir) {
                SwipeDir.LEFT -> Alignment.CenterEnd
                SwipeDir.RIGHT -> Alignment.CenterStart
                SwipeDir.UP -> Alignment.BottomCenter
                SwipeDir.DOWN -> Alignment.TopCenter
            }
            Text(
                h.text,
                color = if (h.armed) Color(0xFF14181B) else Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .align(alignment)
                    .windowInsetsPadding(WindowInsets.systemBars)
                    .padding(horizontal = 20.dp, vertical = if (h.dir == SwipeDir.UP) 96.dp else 24.dp)
                    .alpha(0.35f + 0.65f * h.progress)
                    .background(if (h.armed) Color.White else Color.Black.copy(alpha = 0.55f), RoundedCornerShape(50))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        notesFor?.let { info ->
            AlertDialog(
                onDismissRequest = { notesFor = null },
                title = { Text("What's new in build ${info.build}") },
                text = { Text(info.notes, modifier = Modifier.verticalScroll(rememberScrollState())) },
                confirmButton = { TextButton(onClick = { notesFor = null; viewModel.installUpdate() }) { Text("Install") } },
                dismissButton = { TextButton(onClick = { notesFor = null }) { Text("Later") } },
            )
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
                    if (settings.focusApps.isNotEmpty()) {
                        DropdownMenuItem(
                            text = { Text(if (focusActive) "Pause focus mode (1 hour)" else "Start focus mode") },
                            onClick = {
                                homeMenuAt = null
                                if (focusActive) viewModel.pauseFocus(60) else viewModel.setFocusManual(true)
                            },
                        )
                    }
                    DropdownMenuItem(text = { Text("Car mode") }, onClick = {
                        homeMenuAt = null
                        viewModel.setDashboardOpen(true)
                    })
                    VirtualApps.all.filter { it.key !in favouriteKeys }.forEach { button ->
                        DropdownMenuItem(text = { Text("Add ${button.label} button") }, onClick = {
                            homeMenuAt = null
                            viewModel.addFavourite(button)
                        })
                    }
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

        if (homeRecents.isNotEmpty() && !editMode) {
            HomeRecentsRow(
                apps = homeRecents,
                viewModel = viewModel,
                settings = settings,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.systemBars)
                    .padding(bottom = settings.homeRecentsBottomDp.dp),
            )
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

private const val SEARCH_BUTTON_CLEARANCE_DP = 88
private const val EDGE_PADDING_DP = 24

private fun homeRecentsRowHeight(settings: LauncherSettings) = (settings.homeRecentsIconDp + 24).dp

private fun homeRecentsRowCount(settings: LauncherSettings, apps: Int) = if (settings.homeRecentsTwoRows && apps > 5) 2 else 1

/** The last launched apps as bare icons along the bottom of the home screen. */
@Composable
private fun HomeRecentsRow(apps: List<AppInfo>, viewModel: LauncherViewModel, settings: LauncherSettings, modifier: Modifier = Modifier) {
    // Each icon fills its cell exactly, so the first one starts on the same left line as the favourites and the clock.
    val cell = settings.homeRecentsIconDp.dp
    // The search button floats in one bottom corner (the one opposite the favourites); keep the icons off it.
    val clear = if (settings.showSearchButton) SEARCH_BUTTON_CLEARANCE_DP.dp else EDGE_PADDING_DP.dp
    val edge = EDGE_PADDING_DP.dp
    val searchAtEnd = !settings.alignEnd
    val (startPad, endPad) = when (settings.homeRecentsAlign) {
        // Left-aligned and spread rows start on the same line as the favourites; only the search-button side is kept clear.
        HomeRecentsAlign.START, HomeRecentsAlign.SPREAD -> edge to (if (searchAtEnd) clear else edge)
        HomeRecentsAlign.END -> (if (searchAtEnd) edge else clear) to edge
        // Centred rows stay symmetric, so the clearance applies on both sides.
        else -> clear to clear
    }
    val arrangement = when (settings.homeRecentsAlign) {
        HomeRecentsAlign.START -> Arrangement.spacedBy(settings.homeRecentsGapDp.dp, Alignment.Start)
        HomeRecentsAlign.CENTER -> Arrangement.spacedBy(settings.homeRecentsGapDp.dp, Alignment.CenterHorizontally)
        HomeRecentsAlign.END -> Arrangement.spacedBy(settings.homeRecentsGapDp.dp, Alignment.End)
        else -> Arrangement.SpaceBetween
    }
    val rows = homeRecentsRowCount(settings, apps.size)
    val chunks = if (rows == 2) apps.chunked((apps.size + 1) / 2) else listOf(apps)
    Column(modifier.fillMaxWidth().padding(start = startPad, end = endPad)) {
        chunks.forEach { rowApps ->
            Row(
                Modifier.fillMaxWidth().height(homeRecentsRowHeight(settings)),
                horizontalArrangement = arrangement,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                rowApps.forEach { app ->
                    Box(
                        Modifier.size(cell).clickable { viewModel.launch(app) }.semantics { contentDescription = app.label },
                        contentAlignment = Alignment.Center,
                    ) { AppIcon(app, viewModel, settings.homeRecentsIconDp.dp) }
                }
                // Spread evenly: keep the icons where they are while fewer apps than the chosen number have been launched.
                if (settings.homeRecentsAlign == HomeRecentsAlign.SPREAD) {
                    val slots = if (rows == 2) chunks.first().size else settings.homeRecentsCount
                    repeat((slots - rowApps.size).coerceAtLeast(0)) { Spacer(Modifier.size(cell)) }
                }
            }
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Clock(
    settings: LauncherSettings,
    weather: String?,
    event: NextEvent?,
    glucoseLine: @Composable () -> Unit,
) {
    val alignEnd = settings.alignEnd
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
    val horizontal = if (alignEnd) Alignment.End else Alignment.Start
    // Each piece is its own tap target: time -> clock app, date -> calendar, battery -> battery usage,
    // weather -> forecast, event -> that event.
    val time: @Composable () -> Unit = {
        Text(
            timeFormat.format(now),
            color = ink,
            fontSize = settings.clockSizeSp.sp,
            fontWeight = FontWeight.Light,
            style = HomeTextStyle,
            modifier = Modifier.clickable { startSafely(context, Intent(AlarmClock.ACTION_SHOW_ALARMS)) },
        )
    }
    val date: @Composable () -> Unit = {
        if (settings.headerShowDate) {
            HeaderText(dateFormat.format(now)) {
                startSafely(context, Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR))
            }
        }
    }
    val batteryText: @Composable () -> Unit = {
        if (settings.headerShowBattery && battery >= 0) {
            HeaderText("$battery%") { startSafely(context, Intent(Intent.ACTION_POWER_USAGE_SUMMARY)) }
        }
    }
    val weatherText: @Composable () -> Unit = {
        if (weather != null) {
            HeaderText(weather) {
                startSafely(
                    context,
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode("weather ${settings.weatherCity}"))),
                )
            }
        }
    }
    val eventText: @Composable () -> Unit = {
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
    when (settings.headerLayout) {
        "side" -> Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val details: @Composable () -> Unit = {
                Column(horizontalAlignment = horizontal) {
                    date()
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        batteryText()
                        weatherText()
                    }
                    glucoseLine()
                    eventText()
                }
            }
            if (alignEnd) { details(); time() } else { time(); details() }
        }
        "inline" -> Column(horizontalAlignment = horizontal) {
            time()
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp, horizontal),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                date()
                batteryText()
                weatherText()
                glucoseLine()
                eventText()
            }
        }
        else -> Column(horizontalAlignment = horizontal) {
            time()
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                date()
                batteryText()
                weatherText()
            }
            glucoseLine()
            eventText()
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
                is AppEntry -> if (!VirtualApps.isVirtual(entry.app)) {
                    DropdownMenuItem(text = { Text("App info") }, onClick = {
                        menuOpen = false
                        viewModel.repository.openAppInfo(entry.app)
                    })
                }
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
            if (!VirtualApps.isVirtual(app)) {
                DropdownMenuItem(text = { Text("App info") }, onClick = {
                    menuOpen = false
                    viewModel.repository.openAppInfo(app)
                })
            }
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
internal fun MediaCard(media: MediaState, modifier: Modifier = Modifier, thumbSize: androidx.compose.ui.unit.Dp = 44.dp) {
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
        media.art?.let { art ->
            val picture = remember(art) { art.asImageBitmap() }
            androidx.compose.foundation.Image(
                bitmap = picture,
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.padding(end = 12.dp).size(thumbSize).clip(RoundedCornerShape(8.dp)),
            )
        }
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
internal fun openMediaApp(context: Context, controller: MediaController) {
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
internal fun CustomActionButton(
    packageName: String,
    action: android.media.session.PlaybackState.CustomAction,
    size: androidx.compose.ui.unit.Dp = 32.dp,
    onClick: () -> Unit,
) {
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
            modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 4.dp).size(size),
        )
    } else {
        MediaButton(action.name.toString().take(10), onClick)
    }
}

/** A small banner for a new build: "Build 27 ready · Install". Hidden when nothing is pending or it was dismissed. */
@Composable
private fun UpdateChip(
    state: UpdateState,
    dismissedBuild: Int,
    onInstall: () -> Unit,
    onDismiss: (Int) -> Unit,
    onShowNotes: (UpdateInfo) -> Unit,
) {
    val (text, build, action) = when (state) {
        is UpdateState.Ready -> Triple("Build ${state.info.build} is ready · Install", state.info.build, true)
        is UpdateState.Available -> Triple("Build ${state.info.build} is available · Install", state.info.build, true)
        is UpdateState.Downloading -> Triple("Downloading build ${state.info.build}… ${state.percent}%", state.info.build, false)
        is UpdateState.Installing -> Triple("Installing build ${state.info.build}…", state.info.build, false)
        is UpdateState.Failed -> if (state.info != null) Triple(state.reason, state.info.build, true) else return
        else -> return
    }
    if (build <= dismissedBuild && state !is UpdateState.Downloading && state !is UpdateState.Installing) return
    Row(
        SidePadding
            .background(Color.White.copy(alpha = 0.18f), RoundedCornerShape(12.dp))
            .padding(start = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "⬆ $text",
            color = Color.White,
            fontSize = 14.sp,
            maxLines = 2,
            modifier = Modifier.weight(1f, fill = false).clickable(enabled = action, onClick = onInstall).padding(vertical = 10.dp),
        )
        val notesInfo = (state as? UpdateState.Ready)?.info ?: (state as? UpdateState.Available)?.info
        if (notesInfo != null && notesInfo.notes.isNotBlank()) {
            Text("What's new", color = Accent, fontSize = 13.sp, modifier = Modifier.clickable { onShowNotes(notesInfo) }.padding(8.dp))
        }
        if (action) {
            Text("✕", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp, modifier = Modifier.clickable { onDismiss(build) }.padding(12.dp))
        } else {
            Spacer(Modifier.size(14.dp))
        }
    }
    Spacer(Modifier.height(8.dp))
}
