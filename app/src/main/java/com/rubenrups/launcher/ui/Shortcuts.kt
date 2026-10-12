package com.rubenrups.launcher.ui

import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.rubenrups.launcher.LauncherViewModel
import com.rubenrups.launcher.data.AppInfo
import com.rubenrups.launcher.data.AppShortcut
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The app's own quick actions as menu entries. Call from inside a DropdownMenu: it is only composed
 * once the menu opens, so the lookup happens on demand and the items pop in a moment later.
 */
@Composable
fun AppShortcutItems(app: AppInfo, viewModel: LauncherViewModel, onDone: () -> Unit) {
    // A pinned shortcut is not an app: it has no quick actions of its own.
    if (app.shortcutId != null) return
    var shortcuts by remember(app.key) { mutableStateOf(emptyList<AppShortcut>()) }
    LaunchedEffect(app.key) {
        shortcuts = withContext(Dispatchers.Default) { viewModel.repository.shortcutsFor(app) }
    }
    shortcuts.forEach { shortcut ->
        DropdownMenuItem(text = { Text(shortcut.label) }, onClick = {
            onDone()
            viewModel.repository.launchShortcut(shortcut)
            viewModel.closeDrawer()
        })
    }
}
