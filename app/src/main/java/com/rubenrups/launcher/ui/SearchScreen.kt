package com.rubenrups.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rubenrups.launcher.LauncherViewModel
import com.rubenrups.launcher.data.AppInfo

private val ResultShadow = TextStyle(
    shadow = Shadow(color = Color.Black.copy(alpha = 0.6f), offset = Offset(0f, 2f), blurRadius = 8f),
)

/** One search row: the app plus an optional grey subtitle ("Last opened", folder name…). */
private data class SearchResult(val app: AppInfo, val subtitle: String?)

/**
 * Niagara-style search: a pill bar at the top with the keyboard already open, and results listed
 * underneath. With an empty query it suggests the last-opened and most-used apps.
 */
@Composable
fun SearchScreen(
    viewModel: LauncherViewModel,
    onClose: () -> Unit,
    onOpenDrawer: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val apps by viewModel.visibleApps.collectAsStateWithLifecycle()
    val usage by viewModel.usage.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var menuOpen by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }

    LaunchedEffect(Unit) { focus.requestFocus() }

    val results = remember(apps, usage, settings.folders, query) {
        val folderOf = { app: AppInfo -> settings.folders.firstOrNull { app.key in it.appKeys }?.name }
        if (query.isBlank()) {
            val last = apps.firstOrNull { it.key == usage.last }
            val frequent = apps
                .filter { it.key != usage.last && (usage.counts[it.key] ?: 0) > 0 }
                .sortedByDescending { usage.counts[it.key] ?: 0 }
                .take(6)
            listOfNotNull(last?.let { SearchResult(it, "Last opened") }) +
                frequent.map { SearchResult(it, "Frequently used") }
        } else {
            apps.filter { it.label.contains(query, ignoreCase = true) }
                // Names that start with the query first, then the rest alphabetically.
                .sortedWith(compareBy({ !it.label.startsWith(query, ignoreCase = true) }, { it.label.lowercase() }))
                .map { SearchResult(it, folderOf(it)) }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
            // Tapping empty space dismisses search; rows below consume their own taps.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose)
            .windowInsetsPadding(WindowInsets.systemBars)
            .imePadding(),
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 24.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .background(Color(0xFF101010), RoundedCornerShape(28.dp))
                    .padding(start = 24.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = TextStyle(color = Color.White, fontSize = 20.sp),
                    cursorBrush = SolidColor(Color.White),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { results.firstOrNull()?.let { viewModel.launch(it.app) } }),
                    modifier = Modifier.weight(1f).focusRequester(focus),
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (query.isEmpty()) Text("Search apps", color = Color.White.copy(alpha = 0.6f), fontSize = 20.sp)
                            inner()
                        }
                    },
                )
                Box {
                    Text(
                        "⋮",
                        color = Color.White,
                        fontSize = 22.sp,
                        modifier = Modifier.clickable { menuOpen = true }.padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text("App drawer") }, onClick = {
                            menuOpen = false
                            onOpenDrawer()
                        })
                        DropdownMenuItem(text = { Text("Settings") }, onClick = {
                            menuOpen = false
                            onOpenSettings()
                        })
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            LazyColumn(Modifier.fillMaxWidth()) {
                items(results, key = { it.app.key }) { result ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.launch(result.app) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        AppIcon(result.app, viewModel, size = 40.dp)
                        Column {
                            Text(result.app.label, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Light, style = ResultShadow, maxLines = 1)
                            result.subtitle?.let {
                                Text(it, color = Color.White.copy(alpha = 0.8f), fontSize = 15.sp, style = ResultShadow, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}
