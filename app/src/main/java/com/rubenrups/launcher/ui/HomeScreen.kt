package com.rubenrups.launcher.ui

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.provider.Settings
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rubenrups.launcher.LauncherViewModel
import com.rubenrups.launcher.data.AppInfo
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

    val currentSettings by rememberUpdatedState(settings)
    val currentOpenDrawer by rememberUpdatedState(onOpenDrawer)

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars)
            // Long-press on empty space opens settings (child rows consume their own presses).
            .pointerInput(editMode) {
                if (editMode) return@pointerInput
                detectTapGestures(onLongPress = { onOpenSettings() })
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

        Spacer(Modifier.weight(1f))

        if (favourites.isEmpty()) {
            Text(
                "Swipe up and long-press an app\nto add it to your favourites",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 16.sp,
                textAlign = if (settings.alignEnd) TextAlign.End else TextAlign.Start,
            )
        } else {
            FavouritesList(
                favourites = favourites,
                badgeCounts = badgeCounts,
                settings = settings,
                viewModel = viewModel,
                editMode = editMode,
                onEditModeChange = { editMode = it },
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun Clock(alignEnd: Boolean) {
    val now by produceState(Date()) {
        while (true) {
            value = Date()
            delay(15_000)
        }
    }
    val dateFormat = remember { SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()) }
    val timeFormat = remember { DateFormat.getTimeInstance(DateFormat.SHORT) }
    Column(horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start) {
        Text(timeFormat.format(now), color = Color.White, fontSize = 56.sp, fontWeight = FontWeight.Light)
        Text(dateFormat.format(now), color = Color.White.copy(alpha = 0.8f), fontSize = 16.sp)
    }
}

@Composable
private fun FavouritesList(
    favourites: List<AppInfo>,
    badgeCounts: Map<String, Int>,
    settings: LauncherSettings,
    viewModel: LauncherViewModel,
    editMode: Boolean,
    onEditModeChange: (Boolean) -> Unit,
) {
    // Fixed row height so a drag offset maps cleanly onto "how many rows have I moved".
    val rowHeight = (settings.favouriteTextSp * 1.5f + 12f).dp
    val rowPx = with(LocalDensity.current) { rowHeight.toPx() }
    val currentFavourites by rememberUpdatedState(favourites)
    var dragKey by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }

    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (settings.alignEnd) Alignment.End else Alignment.Start) {
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
        favourites.forEach { app ->
            // key() keeps the row (and its in-flight drag gesture) alive while it changes position.
            key(app.key) {
                val dragging = dragKey == app.key
                val dragModifier = if (editMode) {
                    Modifier.pointerInput(app.key) {
                        detectVerticalDragGestures(
                            onDragStart = { dragKey = app.key; dragOffset = 0f },
                            onDragEnd = { dragKey = null; dragOffset = 0f },
                            onDragCancel = { dragKey = null; dragOffset = 0f },
                            onVerticalDrag = { change, delta ->
                                change.consume()
                                dragOffset += delta
                                val list = currentFavourites
                                val from = list.indexOfFirst { it.key == app.key }
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
                FavouriteRow(
                    app = app,
                    badge = badgeCounts[app.packageName] ?: 0,
                    textSp = settings.favouriteTextSp,
                    alignEnd = settings.alignEnd,
                    rowHeight = rowHeight,
                    editMode = editMode,
                    modifier = dragModifier
                        .zIndex(if (dragging) 1f else 0f)
                        .graphicsLayer { translationY = if (dragging) dragOffset else 0f }
                        .alpha(if (dragging) 0.8f else 1f),
                    viewModel = viewModel,
                    onReorder = { onEditModeChange(true) },
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FavouriteRow(
    app: AppInfo,
    badge: Int,
    textSp: Int,
    alignEnd: Boolean,
    rowHeight: androidx.compose.ui.unit.Dp,
    editMode: Boolean,
    modifier: Modifier,
    viewModel: LauncherViewModel,
    onReorder: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

    Box(modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .height(rowHeight)
                .then(
                    if (editMode) Modifier else Modifier.combinedClickable(
                        onClick = { viewModel.launch(app) },
                        onLongClick = { menuOpen = true },
                    )
                ),
        ) {
            if (editMode && !alignEnd) Text("≡", color = Color.White.copy(alpha = 0.6f), fontSize = 24.sp)
            if (badge > 0 && alignEnd) Badge(badge)
            Text(
                app.label,
                color = Color.White,
                fontSize = textSp.sp,
                fontWeight = FontWeight.Light,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (badge > 0 && !alignEnd) Badge(badge)
            if (editMode && alignEnd) Text("≡", color = Color.White.copy(alpha = 0.6f), fontSize = 24.sp)
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(text = { Text("Reorder") }, onClick = {
                menuOpen = false
                onReorder()
            })
            DropdownMenuItem(text = { Text("Remove from favourites") }, onClick = {
                menuOpen = false
                viewModel.removeFavourite(app)
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
