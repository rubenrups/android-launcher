package com.rubenrups.launcher.ui

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rubenrups.launcher.LauncherViewModel
import com.rubenrups.launcher.data.AppEntry
import com.rubenrups.launcher.data.AppInfo
import com.rubenrups.launcher.data.Entry
import com.rubenrups.launcher.data.FolderEntry
import com.rubenrups.launcher.data.LauncherSettings
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

/** Soft shadow so white text stays readable on bright wallpapers. */
private val HomeTextStyle = TextStyle(
    shadow = Shadow(color = Color.Black.copy(alpha = 0.55f), offset = Offset(0f, 2f), blurRadius = 8f),
)

@Composable
fun HomeScreen(
    viewModel: LauncherViewModel,
    onOpenDrawer: (focusSearch: Boolean) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val favourites by viewModel.favourites.collectAsStateWithLifecycle()
    val notifications by NotificationStore.notifications.collectAsStateWithLifecycle()
    val media by NotificationStore.media.collectAsStateWithLifecycle()
    val listenerConnected by NotificationStore.listenerConnected.collectAsStateWithLifecycle()
    val badgeCounts = remember(notifications) { notifications.groupingBy { it.packageName }.eachCount() }
    var editMode by remember { mutableStateOf(false) }
    // Where a long-press landed; non-null while the home-screen menu is showing.
    var homeMenuAt by remember { mutableStateOf<Offset?>(null) }
    val listScroll = rememberScrollState()
    val favouriteKeys = remember(favourites) { favourites.mapTo(HashSet()) { it.key } }

    val currentSettings by rememberUpdatedState(settings)
    val currentOpenDrawer by rememberUpdatedState(onOpenDrawer)

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
                // Swipe up -> drawer, swipe down -> configurable action.
                .pointerInput(editMode) {
                    if (editMode) return@pointerInput
                    var dx = 0f
                    var dy = 0f
                    detectDragGestures(
                        onDragStart = { dx = 0f; dy = 0f },
                        onDrag = { _, amount -> dx += amount.x; dy += amount.y },
                        onDragEnd = {
                            if (abs(dy) > abs(dx) * 1.2f) {
                                if (dy < -SWIPE_THRESHOLD_PX) {
                                    currentOpenDrawer(false)
                                } else if (dy > SWIPE_THRESHOLD_PX) {
                                    when (currentSettings.swipeDown) {
                                        SwipeAction.NOTIFICATIONS -> expandNotificationShade(context)
                                        SwipeAction.SEARCH -> currentOpenDrawer(true)
                                        SwipeAction.NONE -> Unit
                                    }
                                }
                            }
                        },
                    )
                }
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = if (settings.alignEnd) Alignment.End else Alignment.Start,
        ) {
            if (settings.showClock) {
                Clock(settings.alignEnd)
                Spacer(Modifier.height(16.dp))
            }
            if (settings.showNotifications) {
                if (!listenerConnected) EnableNotificationsPrompt()
                NotificationList(notifications.take(3))
            }
            if (settings.showMedia) media?.let { MediaCard(it) }

            // Top/bottom placement is its own setting, independent of left/right alignment.
            val favouritesContent: @Composable () -> Unit = {
                if (favourites.isEmpty()) {
                    Text(
                        "Swipe up and long-press an app\nto add it to your favourites",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 16.sp,
                        style = HomeTextStyle,
                        textAlign = if (settings.alignEnd) TextAlign.End else TextAlign.Start,
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
private fun rememberBatteryPercent(): Int {
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
private fun Clock(alignEnd: Boolean) {
    val now by produceState(Date()) {
        while (true) {
            value = Date()
            delay(15_000)
        }
    }
    val battery = rememberBatteryPercent()
    val dateFormat = remember { SimpleDateFormat("EEE, MMM d", Locale.getDefault()) }
    val timeFormat = remember { DateFormat.getTimeInstance(DateFormat.SHORT) }
    val dateLine = buildString {
        append(dateFormat.format(now))
        if (battery >= 0) append("  $battery%")
    }
    Column(horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start) {
        Text(timeFormat.format(now), color = Color.White, fontSize = 56.sp, fontWeight = FontWeight.Light, style = HomeTextStyle)
        Text(dateLine, color = Color.White.copy(alpha = 0.9f), fontSize = 16.sp, style = HomeTextStyle)
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
) {
    val iconSize = (settings.favouriteTextSp * 1.5f).dp
    // Fixed row height so a drag offset maps cleanly onto "how many rows have I moved".
    val rowHeight = iconSize + 14.dp
    val rowPx = with(LocalDensity.current) { rowHeight.toPx() }
    val currentFavourites by rememberUpdatedState(favourites)
    var dragKey by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var expandedKeys by remember { mutableStateOf(setOf<String>()) }
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
                modifier = Modifier
                    .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                    .clickable { onEditModeChange(false) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
            Spacer(Modifier.height(8.dp))
        }
        favourites.forEach { entry ->
            // key() keeps the row (and its in-flight drag gesture) alive while it changes position.
            key(entry.key) {
                val dragging = dragKey == entry.key
                val expanded = !editMode && entry.key in expandedKeys
                val dragModifier = if (editMode) {
                    Modifier.pointerInput(entry.key) {
                        detectVerticalDragGestures(
                            onDragStart = { dragKey = entry.key; dragOffset = 0f },
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
                            expandedKeys = if (entry.key in expandedKeys) expandedKeys - entry.key else expandedKeys + entry.key
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
        Spacer(Modifier.height(bottomPadding))
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
    var menuOpen by remember { mutableStateOf(false) }
    val alignEnd = settings.alignEnd
    val icon: @Composable () -> Unit = {
        when (entry) {
            is AppEntry -> AppIcon(entry.app, viewModel, iconSize)
            is FolderEntry -> FolderGlyph(entry.apps.size, entry.folder.icon, size = iconSize)
        }
    }
    val handle: @Composable () -> Unit = {
        Text("≡", color = Color.White.copy(alpha = 0.7f), fontSize = 24.sp, style = HomeTextStyle)
    }

    Box(modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .height(rowHeight)
                .then(
                    if (editMode) Modifier else Modifier.combinedClickable(
                        onClick = {
                            when (entry) {
                                is AppEntry -> viewModel.launch(entry.app)
                                is FolderEntry -> onToggleFolder()
                            }
                        },
                        onLongClick = { menuOpen = true },
                    )
                ),
        ) {
            if (editMode && !alignEnd) handle()
            if (settings.showIcons && !alignEnd) icon()
            if (badge > 0 && alignEnd) Badge(badge)
            Column(horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start) {
                Text(
                    entry.label,
                    color = Color.White,
                    fontSize = settings.favouriteTextSp.sp,
                    fontWeight = FontWeight.Light,
                    style = HomeTextStyle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (entry is FolderEntry && entry.apps.isNotEmpty()) {
                    Text(
                        folderPreview(entry),
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = (settings.favouriteTextSp * 0.6f).sp,
                        style = HomeTextStyle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (badge > 0 && !alignEnd) Badge(badge)
            if (entry is FolderEntry && !editMode) {
                Text(if (expanded) "▾" else "▸", color = Color.White.copy(alpha = 0.8f), fontSize = 16.sp, style = HomeTextStyle)
            }
            if (settings.showIcons && alignEnd) icon()
            if (editMode && alignEnd) handle()
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
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
    var menuOpen by remember { mutableStateOf(false) }
    val indent = Modifier.padding(start = if (settings.alignEnd) 0.dp else 28.dp, end = if (settings.alignEnd) 28.dp else 0.dp)
    Box(indent) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .height(rowHeight * 0.85f)
                .combinedClickable(onClick = { viewModel.launch(app) }, onLongClick = { menuOpen = true }),
        ) {
            if (settings.showIcons && !settings.alignEnd) AppIcon(app, viewModel, iconSize * 0.8f)
            Text(
                app.label,
                color = Color.White,
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
private fun Badge(count: Int) {
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
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
            .padding(vertical = 4.dp),
    ) {
        items.forEach { item ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable { item.contentIntent?.sendSafely() }
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
    Spacer(Modifier.height(8.dp))
}

private fun PendingIntent.sendSafely() {
    try {
        send()
    } catch (_: PendingIntent.CanceledException) {
        // The owning app cancelled it (e.g. notification already dismissed); nothing to do.
    }
}

@Composable
private fun MediaCard(media: MediaState) {
    val controls = media.controller.transportControls
    Row(
        Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(media.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (media.artist.isNotBlank()) {
                Text(media.artist, color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        MediaButton("|◀") { controls.skipToPrevious() }
        MediaButton(if (media.isPlaying) "❚❚" else "▶") {
            if (media.isPlaying) controls.pause() else controls.play()
        }
        MediaButton("▶|") { controls.skipToNext() }
    }
    Spacer(Modifier.height(8.dp))
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
