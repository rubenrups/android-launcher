package com.rubenrups.launcher.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogWindowProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rubenrups.launcher.LauncherViewModel
import com.rubenrups.launcher.data.AppFolder
import com.rubenrups.launcher.data.AppInfo
import com.rubenrups.launcher.data.VirtualApps
import com.rubenrups.launcher.data.FolderEntry
import com.rubenrups.launcher.data.FolderSort

/** Single-line text prompt, used for creating and renaming folders. */
@Composable
fun NameDialog(
    title: String,
    initial: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(TextFieldValue(initial, TextRange(0, initial.length))) }
    val focus = remember { FocusRequester() }
    // Open ready to type; a missed focus (dialog still attaching) is harmless.
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            TextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                modifier = Modifier.focusRequester(focus),
            )
        },
        confirmButton = {
            TextButton(onClick = { if (text.text.isNotBlank()) onConfirm(text.text.trim()) }) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** "Add to folder": pick an existing folder or start a new one. */
@Composable
fun FolderPickerDialog(
    app: AppInfo,
    folders: List<AppFolder>,
    onPick: (AppFolder) -> Unit,
    onNew: () -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(24.dp), color = Color(0xFF1A1A1A)) {
            val scroll = rememberScrollState()
            Column(Modifier.heightIn(max = 560.dp).padding(top = 16.dp, bottom = 8.dp)) {
                Text(
                    "Add ${app.label} to…",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
                if (folders.size > 4) PickerSearchField(query) { query = it }
                Column(Modifier.weight(1f, fill = false).verticalScrollbar(scroll).verticalScroll(scroll)) {
                    folders.filter { matchesQuery(query, it.name) }.sortedBy { it.name.lowercase() }.forEach { folder ->
                        PickerRow(folder.name) { onPick(folder) }
                    }
                }
                // Pinned below the scrolling list so it is reachable however many folders there are.
                PickerRow("New folder…", onClick = onNew)
            }
        }
    }
}

@Composable
private fun PickerRow(label: String, onClick: () -> Unit) {
    Text(
        label,
        color = Color.White,
        fontSize = 16.sp,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 14.dp),
    )
}

/** The contents of a folder as a popup. Long-press an app inside for "Remove from folder". */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FolderDialog(
    entry: FolderEntry,
    viewModel: LauncherViewModel,
    onDismiss: () -> Unit,
    /** See-through variant for the home screen, so the wallpaper stays visible behind it. */
    translucent: Boolean = false,
) {
    var picking by remember { mutableStateOf(false) }
    var sorting by remember { mutableStateOf(false) }
    val favourites by viewModel.favourites.collectAsStateWithLifecycle()
    val favouriteKeys = remember(favourites) { favourites.mapTo(HashSet()) { it.key } }
    Dialog(onDismissRequest = onDismiss) {
        if (translucent) {
            // The dialog window dims everything behind it by default; keep that light so the wallpaper shows.
            val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
            SideEffect { dialogWindow?.setDimAmount(0.12f) }
        }
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = if (translucent) Color(0xFF1A1A1A).copy(alpha = 0.6f) else Color(0xF21A1A1A),
        ) {
            val scroll = rememberScrollState()
            Column(
                Modifier.heightIn(max = 560.dp).verticalScrollbar(scroll).verticalScroll(scroll).padding(vertical = 16.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        entry.label,
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Light,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "Sort: ${entry.folder.sort.label}",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 14.sp,
                        modifier = Modifier.clickable { sorting = true }.padding(8.dp),
                    )
                }
                if (entry.apps.isEmpty()) {
                    Text(
                        "This folder is empty.",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    )
                }
                entry.apps.forEachIndexed { index, app ->
                    var menuOpen by remember { mutableStateOf(false) }
                    val isFavourite = app.key in favouriteKeys
                    Box {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = {
                                        onDismiss()
                                        viewModel.launch(app)
                                    },
                                    onLongClick = { menuOpen = true },
                                )
                                .padding(horizontal = 24.dp, vertical = 10.dp),
                        ) {
                            AppIcon(app, viewModel)
                            Text(app.label, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Light, maxLines = 1)
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            AppShortcutItems(app, viewModel) { menuOpen = false }
                            // Pin one app to the home screen without taking it out of its folder.
                            DropdownMenuItem(
                                text = { Text(if (isFavourite) "Remove from favourites" else "Add to favourites") },
                                onClick = {
                                    menuOpen = false
                                    if (isFavourite) viewModel.removeFavourite(app) else viewModel.addFavourite(app)
                                },
                            )
                            if (entry.folder.sort == FolderSort.MANUAL) {
                                if (index > 0) {
                                    DropdownMenuItem(text = { Text("Move up") }, onClick = {
                                        menuOpen = false
                                        viewModel.swapInFolder(entry.folder.id, app.key, entry.apps[index - 1].key)
                                    })
                                }
                                if (index < entry.apps.lastIndex) {
                                    DropdownMenuItem(text = { Text("Move down") }, onClick = {
                                        menuOpen = false
                                        viewModel.swapInFolder(entry.folder.id, app.key, entry.apps[index + 1].key)
                                    })
                                }
                            }
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
                PickerRow("+ Add apps") { picking = true }
            }
        }
    }
    if (picking) AppPickerDialog(entry.folder.id, viewModel, onDismiss = { picking = false })
    if (sorting) FolderSortDialog(entry, viewModel, onDismiss = { sorting = false })
}

/** Rounded square standing in for a folder icon: shows its emoji, or how many apps it holds. */
@Composable
fun FolderGlyph(count: Int, icon: String? = null, modifier: Modifier = Modifier, size: Dp = 36.dp) {
    Box(
        modifier.size(size).background(Color.White.copy(alpha = 0.18f), RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) {
            Text(icon, fontSize = (size.value * 0.5f).sp)
        } else {
            Text(count.toString(), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
    }
}

/**
 * Tick the apps that belong in a folder. Changes apply immediately, so "Done" just closes the dialog.
 * An app lives in one folder, so ticking one that is elsewhere moves it (and says where it came from).
 */
@Composable
fun AppPickerDialog(folderId: String, viewModel: LauncherViewModel, onDismiss: () -> Unit) {
    val apps by viewModel.visibleApps.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val folder = settings.folders.firstOrNull { it.id == folderId } ?: return
    val listState = rememberLazyListState()
    var query by remember { mutableStateOf("") }
    val shownApps = apps.filter { matchesQuery(query, it.label) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(24.dp), color = Color(0xF21A1A1A)) {
            Column(Modifier.heightIn(max = 560.dp).padding(top = 16.dp)) {
                Text(
                    "Add apps to ${folder.name}",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
                PickerSearchField(query) { query = it }
                LazyColumn(state = listState, modifier = Modifier.weight(1f, fill = false).verticalScrollbar(listState)) {
                    items(shownApps, key = { it.key }) { app ->
                        val inThis = app.key in folder.appKeys
                        val elsewhere = settings.folders.firstOrNull { it.id != folderId && app.key in it.appKeys }
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (inThis) viewModel.removeFromFolder(app) else viewModel.addToFolder(app, folderId)
                                }
                                .padding(horizontal = 24.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            AppIcon(app, viewModel, size = 32.dp)
                            Column(Modifier.weight(1f)) {
                                Text(app.label, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Light, maxLines = 1)
                                elsewhere?.let {
                                    Text("In ${it.name}", color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp)
                                }
                            }
                            Checkbox(checked = inThis, onCheckedChange = null)
                        }
                    }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End).padding(8.dp)) { Text("Done") }
            }
        }
    }
}

/** Choose how a folder orders its apps. Manual unlocks "Move up / Move down" on each app. */
@Composable
fun FolderSortDialog(entry: FolderEntry, viewModel: LauncherViewModel, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(24.dp), color = Color(0xF21A1A1A)) {
            Column(Modifier.padding(vertical = 16.dp)) {
                Text(
                    "Sort ${entry.label}",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
                FolderSort.entries.forEach { sort ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.setFolderSort(entry, sort)
                                onDismiss()
                            }
                            .padding(horizontal = 24.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(sort.label, color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
                        if (entry.folder.sort == sort) Text("✓", color = Color.White, fontSize = 18.sp)
                    }
                }
                Text(
                    "Manual: long-press an app in the folder to move it up or down.",
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
            }
        }
    }
}

private val FOLDER_ICONS = listOf(
    "📁", "⭐", "❤️", "🎮", "💬",
    "🎵", "🎬", "📷", "🛒", "💰",
    "🧰", "🌐", "📰", "🍔", "✈️",
    "🔒", "🏠", "🤖", "📚", "⚡",
)

/** Pick a folder's icon from a preset set, type your own emoji, or go back to the app count. */
@Composable
fun FolderIconDialog(entry: FolderEntry, viewModel: LauncherViewModel, onDismiss: () -> Unit) {
    var custom by remember { mutableStateOf("") }
    val scroll = rememberScrollState()
    val current = entry.folder.icon

    fun choose(icon: String?) {
        viewModel.setFolderIcon(entry.folder.id, icon)
        onDismiss()
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(24.dp), color = Color(0xF21A1A1A)) {
            Column(
                Modifier.heightIn(max = 560.dp).verticalScrollbar(scroll).verticalScroll(scroll).padding(vertical = 16.dp),
            ) {
                Text(
                    "Icon for ${entry.label}",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
                Row(
                    Modifier.fillMaxWidth().clickable { choose(null) }.padding(horizontal = 24.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Show number of apps", color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
                    if (current == null) Text("✓", color = Color.White, fontSize = 18.sp)
                }
                FOLDER_ICONS.chunked(5).forEach { rowIcons ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                        rowIcons.forEach { icon ->
                            Box(
                                Modifier
                                    .size(48.dp)
                                    .background(
                                        if (icon == current) Color.White.copy(alpha = 0.25f) else Color.Transparent,
                                        RoundedCornerShape(12.dp),
                                    )
                                    .clickable { choose(icon) },
                                contentAlignment = Alignment.Center,
                            ) { Text(icon, fontSize = 26.sp) }
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextField(
                        value = custom,
                        onValueChange = { custom = it.take(8) },
                        singleLine = true,
                        placeholder = { Text("Your own emoji") },
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { if (custom.isNotBlank()) choose(custom.trim()) }) { Text("Set") }
                }
            }
        }
    }
}
