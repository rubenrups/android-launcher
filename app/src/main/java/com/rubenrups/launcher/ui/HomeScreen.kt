package com.rubenrups.launcher.ui

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rubenrups.launcher.LauncherViewModel
import com.rubenrups.launcher.data.AppInfo
import com.rubenrups.launcher.notifications.MediaState
import com.rubenrups.launcher.notifications.NotificationItem
import com.rubenrups.launcher.notifications.NotificationStore
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val SWIPE_UP_THRESHOLD_PX = 120f

@Composable
fun HomeScreen(viewModel: LauncherViewModel, onOpenDrawer: () -> Unit) {
    val favourites by viewModel.favourites.collectAsStateWithLifecycle()
    val notifications by NotificationStore.notifications.collectAsStateWithLifecycle()
    val media by NotificationStore.media.collectAsStateWithLifecycle()
    val listenerConnected by NotificationStore.listenerConnected.collectAsStateWithLifecycle()
    val badgeCounts = remember(notifications) { notifications.groupingBy { it.packageName }.eachCount() }

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars)
            .pointerInput(Unit) {
                var total = 0f
                detectVerticalDragGestures(
                    onDragStart = { total = 0f },
                    onVerticalDrag = { _, delta -> total += delta },
                    onDragEnd = { if (total < -SWIPE_UP_THRESHOLD_PX) onOpenDrawer() },
                )
            }
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        Clock()
        Spacer(Modifier.padding(top = 16.dp))
        if (!listenerConnected) EnableNotificationsPrompt()
        media?.let { MediaCard(it) }
        NotificationList(notifications.take(3))

        Spacer(Modifier.weight(1f))

        if (favourites.isEmpty()) {
            Text(
                "Swipe up and long-press an app\nto add it to your favourites",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 16.sp,
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
                favourites.forEach { app ->
                    FavouriteRow(
                        app = app,
                        badge = badgeCounts[app.packageName] ?: 0,
                        viewModel = viewModel,
                    )
                }
            }
        }
        Spacer(Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun Clock() {
    val now by produceState(Date()) {
        while (true) {
            value = Date()
            delay(15_000)
        }
    }
    val dateFormat = remember { SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()) }
    val timeFormat = remember { DateFormat.getTimeInstance(DateFormat.SHORT) }
    Column {
        Text(timeFormat.format(now), color = Color.White, fontSize = 56.sp, fontWeight = FontWeight.Light)
        Text(dateFormat.format(now), color = Color.White.copy(alpha = 0.8f), fontSize = 16.sp)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FavouriteRow(app: AppInfo, badge: Int, viewModel: LauncherViewModel) {
    var menuOpen by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .combinedClickable(
                    onClick = { viewModel.launch(app) },
                    onLongClick = { menuOpen = true },
                )
                .padding(vertical = 8.dp),
        ) {
            if (badge > 0) Badge(badge)
            Text(
                app.label,
                color = Color.White,
                fontSize = 34.sp,
                fontWeight = FontWeight.Light,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
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
    Spacer(Modifier.padding(top = 8.dp))
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
    Spacer(Modifier.padding(top = 8.dp))
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
    Spacer(Modifier.padding(top = 8.dp))
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
