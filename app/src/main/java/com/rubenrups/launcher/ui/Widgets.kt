package com.rubenrups.launcher.ui

import android.view.View
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.remember
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
                LazyColumn(state = listState, modifier = Modifier.weight(1f, fill = false).verticalScrollbar(listState)) {
                    items(providers, key = { it.provider.flattenToString() }) { provider ->
                        // The provider's component names its package; look the app's display name up from that.
                        val app = runCatching {
                            val appInfo = packageManager.getApplicationInfo(provider.provider.packageName, 0)
                            packageManager.getApplicationLabel(appInfo).toString()
                        }.getOrDefault("")
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
                            Text(provider.loadLabel(packageManager), color = Color.White, fontSize = 16.sp, maxLines = 1)
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
