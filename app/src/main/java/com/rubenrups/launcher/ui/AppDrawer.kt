package com.rubenrups.launcher.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rubenrups.launcher.LauncherViewModel
import com.rubenrups.launcher.data.AppEntry
import com.rubenrups.launcher.data.AppFolder
import com.rubenrups.launcher.data.AppInfo
import com.rubenrups.launcher.data.Entry
import com.rubenrups.launcher.data.FolderEntry
import com.rubenrups.launcher.data.WebSearchEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

private const val RECENTS_SHOWN = 5

private val SCRUBBER_LETTERS = listOf('#') + ('A'..'Z').toList()

/** Dragging down this far (px) past the top of the list, or flinging down fast enough, closes the drawer. */
private const val CLOSE_DRAG_PX = 200f
private const val CLOSE_FLING_VELOCITY = 1800f

@Composable
fun AppDrawer(viewModel: LauncherViewModel) {
    val allApps by viewModel.visibleApps.collectAsStateWithLifecycle()
    val drawerEntries by viewModel.drawerEntries.collectAsStateWithLifecycle()
    val favourites by viewModel.favourites.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val focusSearch by viewModel.focusSearch.collectAsStateWithLifecycle()
    val favouriteKeys = remember(favourites) { favourites.mapTo(HashSet()) { it.key } }
    var query by remember { mutableStateOf("") }
    var activeLetter by remember { mutableStateOf<Char?>(null) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val currentClose by rememberUpdatedState({ viewModel.closeDrawer() })
    val searchFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    var newFolder by remember { mutableStateOf(false) }
    var pickerFolderId by remember { mutableStateOf<String?>(null) }

    // Search button / swipe-down-to-search: open with the field focused and the keyboard up.
    LaunchedEffect(focusSearch) {
        if (focusSearch) {
            // The field can be mid-attach during the open animation; a missed focus is harmless.
            runCatching { searchFocus.requestFocus() }
            keyboard?.show()
        }
    }

    // Warm the icon cache off the main thread so scrolling and letter jumps never wait on icon decoding.
    LaunchedEffect(allApps) {
        withContext(Dispatchers.Default) { allApps.forEach { viewModel.repository.loadIcon(it) } }
    }

    // Swipe down to close, but only once the list has nothing left to scroll upwards:
    // the leftover ("unconsumed") downward drag is what we measure.
    val closeOnPullDown = remember {
        object : NestedScrollConnection {
            private var pulled = 0f

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput) {
                    pulled = if (available.y > 0f) pulled + available.y else 0f
                }
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (pulled > CLOSE_DRAG_PX || available.y > CLOSE_FLING_VELOCITY) currentClose()
                pulled = 0f
                return Velocity.Zero
            }
        }
    }

    // While searching, results are flat apps (including ones inside folders); otherwise folders + loose apps.
    val entries: List<Entry> = remember(drawerEntries, allApps, query) {
        if (query.isBlank()) {
            drawerEntries
        } else {
            allApps.filter { it.label.contains(query, ignoreCase = true) }.map { AppEntry(it) }
        }
    }

    // Rows above the entries when not searching: an optional recents row, then 'New folder'.
    val recentApps = remember(allApps, settings.recents, settings.showRecents) {
        if (!settings.showRecents) {
            emptyList()
        } else {
            val byKey = allApps.associateBy { it.key }
            settings.recents.mapNotNull { byKey[it] }.take(RECENTS_SHOWN)
        }
    }
    val showRecentsRow = query.isBlank() && recentApps.isNotEmpty()
    val headerRows = (if (query.isBlank()) 1 else 0) + (if (showRecentsRow) 1 else 0)

    fun jumpTo(letter: Char) {
        activeLetter = letter
        // '#' means "top" (folders live there). Otherwise jump to the first app at or after the letter,
        // so empty letters still land somewhere sensible.
        val index = if (letter == '#') {
            0
        } else {
            val appIndex = entries.indexOfFirst { it is AppEntry && it.letter >= letter }
            (if (appIndex >= 0) appIndex else entries.lastIndex) + headerRows
        }
        if (index >= 0) scope.launch { listState.scrollToItem(index) }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.9f))
            .windowInsetsPadding(WindowInsets.systemBars)
            .imePadding(),
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.weight(1f)) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).nestedScroll(closeOnPullDown),
                ) {
                    if (showRecentsRow) {
                        item(key = "recents") { RecentsRow(recentApps, viewModel) }
                    }
                    if (query.isBlank()) {
                        item(key = "new-folder") { NewFolderRow(onClick = { newFolder = true }) }
                    }
                    items(entries, key = { it.key }) { entry ->
                        when (entry) {
                            is AppEntry -> DrawerRow(
                                app = entry.app,
                                isFavourite = entry.key in favouriteKeys,
                                folders = settings.folders,
                                viewModel = viewModel,
                            )
                            is FolderEntry -> FolderRow(
                                entry = entry,
                                isFavourite = entry.key in favouriteKeys,
                                viewModel = viewModel,
                            )
                        }
                    }
                    if (query.isNotBlank() && settings.webSearch != WebSearchEngine.OFF) {
                        item(key = "web-search") { WebSearchRow(query) { viewModel.webSearch(query) } }
                    }
                }
                if (query.isBlank()) {
                    AlphabetScrubber(
                        letters = SCRUBBER_LETTERS,
                        activeLetter = activeLetter,
                        onLetter = ::jumpTo,
                        onRelease = { activeLetter = null },
                        modifier = Modifier.fillMaxHeight().padding(end = 6.dp),
                    )
                }
            }
            TextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text("Search apps", color = Color.White.copy(alpha = 0.5f)) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    val firstApp = entries.filterIsInstance<AppEntry>().firstOrNull()
                    // Nothing matched: hand the query to web search instead of doing nothing.
                    if (firstApp != null) {
                        viewModel.launch(firstApp.app)
                    } else if (query.isNotBlank() && settings.webSearch != WebSearchEngine.OFF) {
                        viewModel.webSearch(query)
                    }
                }),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.White.copy(alpha = 0.12f),
                    unfocusedContainerColor = Color.White.copy(alpha = 0.12f),
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = Color.White,
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().padding(16.dp).focusRequester(searchFocus),
            )
        }

        if (newFolder) {
            NameDialog(
                title = "New folder",
                initial = "",
                confirmLabel = "Create",
                onConfirm = {
                    // Straight on to picking its apps; that is the whole point of making a folder.
                    pickerFolderId = viewModel.createFolder(it)
                    newFolder = false
                },
                onDismiss = { newFolder = false },
            )
        }
        pickerFolderId?.let { id ->
            AppPickerDialog(id, viewModel, onDismiss = { pickerFolderId = null })
        }

        activeLetter?.let { letter ->
            Box(
                Modifier
                    .align(Alignment.Center)
                    .size(96.dp)
                    .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(letter.toString(), color = Color.White, fontSize = 48.sp, fontWeight = FontWeight.Light)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DrawerRow(
    app: AppInfo,
    isFavourite: Boolean,
    folders: List<AppFolder>,
    viewModel: LauncherViewModel,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var pickFolder by remember { mutableStateOf(false) }
    var newFolder by remember { mutableStateOf(false) }
    val currentFolder = folders.firstOrNull { app.key in it.appKeys }

    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = { viewModel.launch(app) },
                    onLongClick = { menuOpen = true },
                )
                .padding(horizontal = 24.dp, vertical = 10.dp),
        ) {
            AppIcon(app, viewModel)
            Text(
                app.label + if (app.isMainProfile) "" else "  💼",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Light,
                maxLines = 1,
            )
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            AppShortcutItems(app, viewModel) { menuOpen = false }
            DropdownMenuItem(
                text = { Text(if (isFavourite) "Remove from favourites" else "Add to favourites") },
                onClick = {
                    menuOpen = false
                    if (isFavourite) viewModel.removeFavourite(app) else viewModel.addFavourite(app)
                },
            )
            DropdownMenuItem(text = { Text("Add to folder…") }, onClick = {
                menuOpen = false
                pickFolder = true
            })
            if (currentFolder != null) {
                DropdownMenuItem(text = { Text("Remove from ${currentFolder.name}") }, onClick = {
                    menuOpen = false
                    viewModel.removeFromFolder(app)
                })
            }
            DropdownMenuItem(text = { Text("Hide app") }, onClick = {
                menuOpen = false
                viewModel.hide(app)
            })
            DropdownMenuItem(text = { Text("App info") }, onClick = {
                menuOpen = false
                viewModel.repository.openAppInfo(app)
            })
            // Uninstalling a work-profile app would hit the personal copy, so only offer it for the main profile.
            if (app.isMainProfile) {
                DropdownMenuItem(text = { Text("Uninstall") }, onClick = {
                    menuOpen = false
                    viewModel.repository.uninstall(app)
                })
            }
        }
    }

    if (pickFolder) {
        FolderPickerDialog(
            app = app,
            folders = folders,
            onPick = {
                viewModel.addToFolder(app, it.id)
                pickFolder = false
            },
            onNew = {
                pickFolder = false
                newFolder = true
            },
            onDismiss = { pickFolder = false },
        )
    }
    if (newFolder) {
        NameDialog(
            title = "New folder",
            initial = "",
            confirmLabel = "Create",
            onConfirm = {
                viewModel.createFolder(it, app)
                newFolder = false
            },
            onDismiss = { newFolder = false },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderRow(entry: FolderEntry, isFavourite: Boolean, viewModel: LauncherViewModel) {
    var menuOpen by remember { mutableStateOf(false) }
    var open by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf(false) }
    var addApps by remember { mutableStateOf(false) }
    var sorting by remember { mutableStateOf(false) }
    var pickingIcon by remember { mutableStateOf(false) }

    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = { open = true },
                    onLongClick = { menuOpen = true },
                )
                .padding(horizontal = 24.dp, vertical = 10.dp),
        ) {
            FolderGlyph(entry.apps.size, entry.folder.icon)
            Text(entry.label, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Light, maxLines = 1)
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(if (isFavourite) "Remove from favourites" else "Add to favourites") },
                onClick = {
                    menuOpen = false
                    if (isFavourite) viewModel.removeFavouriteKey(entry.key) else viewModel.addFavouriteKey(entry.key)
                },
            )
            DropdownMenuItem(text = { Text("Add apps") }, onClick = {
                menuOpen = false
                addApps = true
            })
            DropdownMenuItem(text = { Text("Sort apps…") }, onClick = {
                menuOpen = false
                sorting = true
            })
            DropdownMenuItem(text = { Text("Change icon…") }, onClick = {
                menuOpen = false
                pickingIcon = true
            })
            DropdownMenuItem(text = { Text("Rename") }, onClick = {
                menuOpen = false
                rename = true
            })
            DropdownMenuItem(text = { Text("Delete folder") }, onClick = {
                menuOpen = false
                viewModel.deleteFolder(entry.folder)
            })
        }
    }

    if (open) FolderDialog(entry, viewModel, onDismiss = { open = false })
    if (addApps) AppPickerDialog(entry.folder.id, viewModel, onDismiss = { addApps = false })
    if (sorting) FolderSortDialog(entry, viewModel, onDismiss = { sorting = false })
    if (pickingIcon) FolderIconDialog(entry, viewModel, onDismiss = { pickingIcon = false })
    if (rename) {
        NameDialog(
            title = "Rename folder",
            initial = entry.label,
            confirmLabel = "Save",
            onConfirm = {
                viewModel.renameFolder(entry.folder.id, it)
                rename = false
            },
            onDismiss = { rename = false },
        )
    }
}

@Composable
internal fun AppIcon(app: AppInfo, viewModel: LauncherViewModel, size: Dp = 36.dp) {
    val iconVersion by viewModel.iconVersion.collectAsStateWithLifecycle()
    val icon by produceState<ImageBitmap?>(null, app.key, iconVersion) {
        value = withContext(Dispatchers.Default) { viewModel.repository.loadIcon(app) }
    }
    Box(Modifier.size(size)) {
        icon?.let { Image(bitmap = it, contentDescription = null, modifier = Modifier.fillMaxSize()) }
    }
}

/** The last few launched apps as a row of icons at the top of the drawer. */
@Composable
private fun RecentsRow(apps: List<AppInfo>, viewModel: LauncherViewModel) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        apps.forEach { app ->
            Column(
                Modifier.weight(1f).clickable { viewModel.launch(app) }.padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AppIcon(app, viewModel, 44.dp)
                Text(
                    app.label,
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        // Keep the icons the same width when fewer than five apps have been launched yet.
        repeat(RECENTS_SHOWN - apps.size) { Spacer(Modifier.weight(1f)) }
    }
}

/** Last row while typing: send the query to web search. */
@Composable
private fun WebSearchRow(query: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 12.dp),
    ) {
        Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) { Text("🔍", fontSize = 20.sp) }
        Text(
            "Search the web for \"$query\"",
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 16.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Folders live above the A-Z rows; this is the always-visible shortcut for making a new one. */
@Composable
private fun NewFolderRow(onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 10.dp),
    ) {
        Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
            Text("+", color = Color.White.copy(alpha = 0.7f), fontSize = 26.sp, fontWeight = FontWeight.Light)
        }
        Text("New folder", color = Color.White.copy(alpha = 0.7f), fontSize = 18.sp, fontWeight = FontWeight.Light)
    }
}

/**
 * A–Z strip along the right edge: touch or drag to jump through the list.
 * Reacts the instant a finger lands (no touch-slop wait), letters near the finger bulge out and grow,
 * and each new letter gives a haptic tick.
 */
@Composable
private fun AlphabetScrubber(
    letters: List<Char>,
    activeLetter: Char?,
    onLetter: (Char) -> Unit,
    onRelease: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    var lastLetter by remember { mutableStateOf<Char?>(null) }
    // The gesture block below is created once; these keep it pointed at the latest callbacks.
    val currentOnLetter by rememberUpdatedState(onLetter)
    val currentOnRelease by rememberUpdatedState(onRelease)

    fun pick(y: Float, height: Int) {
        val letter = letters[((y / height) * letters.size).toInt().coerceIn(0, letters.lastIndex)]
        if (letter != lastLetter) {
            lastLetter = letter
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            currentOnLetter(letter)
        }
    }

    val activeIndex = activeLetter?.let { letters.indexOf(it) } ?: -1

    Column(
        modifier = modifier
            .width(44.dp)
            .pointerInput(letters) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    pick(down.position.y, size.height)
                    down.consume()
                    var pressed = true
                    while (pressed) {
                        val change = awaitPointerEvent().changes.firstOrNull() ?: break
                        pressed = change.pressed
                        if (pressed) {
                            pick(change.position.y, size.height)
                            change.consume()
                        }
                    }
                    lastLetter = null
                    currentOnRelease()
                }
            },
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        letters.forEachIndexed { index, letter ->
            val distance = if (activeIndex < 0) Int.MAX_VALUE else abs(index - activeIndex)
            val bulge = if (distance <= 3) (4 - distance) * 8 else 0
            Text(
                letter.toString(),
                color = Color.White.copy(alpha = if (distance <= 3) 1f else 0.7f),
                fontSize = (11 + (if (distance <= 3) (4 - distance) * 2 else 0)).sp,
                modifier = Modifier.offset(x = (-bulge).dp),
            )
        }
    }
}
