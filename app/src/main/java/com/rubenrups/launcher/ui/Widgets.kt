package com.rubenrups.launcher.ui

import android.view.View
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import com.rubenrups.launcher.LauncherViewModel
import com.rubenrups.launcher.widgets.WidgetHostManager

/** One hosted widget, as tall as the widget asks to be. */
@Composable
fun WidgetView(id: Int, manager: WidgetHostManager, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val info = remember(id) { manager.info(id) }
    // The provider can vanish (app uninstalled); then there is simply nothing to draw.
    if (info != null) {
        val height = with(density) { info.minHeight.toDp() }.coerceAtLeast(48.dp)
        AndroidView(
            factory = { context -> manager.createView(context, id) ?: View(context) },
            modifier = modifier.fillMaxWidth().height(height),
        )
    }
}

/** Lists every widget installed on the device; picking one starts the bind/configure flow. */
@Composable
fun WidgetPickerDialog(viewModel: LauncherViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val providers = remember { viewModel.widgets.installedProviders() }
    val packageManager = context.packageManager
    val listState = rememberLazyListState()
    var query by remember { mutableStateOf("") }
    // Each widget with its own name and its app's name, looked up once.
    val entries = remember(providers) {
        providers.map { provider ->
            val app = runCatching {
                val appInfo = packageManager.getApplicationInfo(provider.provider.packageName, 0)
                packageManager.getApplicationLabel(appInfo).toString()
            }.getOrDefault("")
            Triple(provider, provider.loadLabel(packageManager), app)
        }
    }
    val shown = entries.filter { (_, label, app) -> matchesQuery(query, label, app) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(24.dp), color = Color(0xF21A1A1A)) {
            Column(Modifier.heightIn(max = 560.dp).padding(top = 16.dp)) {
                Text(
                    "Add a widget",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
                if (providers.isEmpty()) {
                    Text(
                        "No widgets are available on this device.",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    )
                }
                if (providers.isNotEmpty()) PickerSearchField(query) { query = it }
                LazyColumn(state = listState, modifier = Modifier.weight(1f, fill = false).verticalScrollbar(listState)) {
                    items(shown, key = { it.first.provider.flattenToString() }) { (provider, label, app) ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    context.findActivity()?.let { viewModel.beginAddWidget(it, provider) }
                                    onDismiss()
                                }
                                .padding(horizontal = 24.dp, vertical = 10.dp),
                            horizontalAlignment = Alignment.Start,
                        ) {
                            Text(label, color = Color.White, fontSize = 16.sp, maxLines = 1)
                            if (app.isNotBlank()) {
                                Text(app, color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp, maxLines = 1)
                            }
                        }
                    }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End).padding(8.dp)) { Text("Cancel") }
            }
        }
    }
}

/**
 * Calls [onLongPress] when a finger stays down on this area without moving, while still letting the widget
 * underneath receive its normal touches. It watches in the Initial pass (before the widget's own view sees the
 * event); once it fires it swallows the rest of the gesture, so the widget does not also register a tap.
 */
fun Modifier.onLongPressAbove(onLongPress: () -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        // Slightly shorter than the system's long-press, so the home screen's own long-press menu loses the race.
        val held = withTimeoutOrNull<Boolean>((viewConfiguration.longPressTimeoutMillis - 100).coerceAtLeast(150)) {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull false
                if (!change.pressed) return@withTimeoutOrNull false
                if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) return@withTimeoutOrNull false
            }
            @Suppress("UNREACHABLE_CODE")
            false
        }
        if (held == null) {
            onLongPress()
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                event.changes.forEach { it.consume() }
            } while (event.changes.any { it.pressed })
        }
    }
}
