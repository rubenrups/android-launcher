package com.rubenrups.launcher.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rubenrups.launcher.LauncherViewModel
import com.rubenrups.launcher.data.AppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val SCRUBBER_LETTERS = listOf('#') + ('A'..'Z').toList()

@Composable
fun AppDrawer(viewModel: LauncherViewModel, onClose: () -> Unit) {
    val allApps by viewModel.allApps.collectAsStateWithLifecycle()
    val favourites by viewModel.favourites.collectAsStateWithLifecycle()
    val favouriteKeys = remember(favourites) { favourites.mapTo(HashSet()) { it.key } }
    var query by remember { mutableStateOf("") }
    var activeLetter by remember { mutableStateOf<Char?>(null) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val apps = remember(allApps, query) {
        if (query.isBlank()) allApps else allApps.filter { it.label.contains(query, ignoreCase = true) }
    }

    fun jumpTo(letter: Char) {
        activeLetter = letter
        // Jump to the first app at or after the letter, so empty letters still land somewhere sensible.
        val index = apps.indexOfFirst { it.letter >= letter }.takeIf { it >= 0 } ?: apps.lastIndex
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
                    modifier = Modifier.weight(1f),
                    reverseLayout = false,
                ) {
                    items(apps, key = { it.key }) { app ->
                        DrawerRow(app, isFavourite = app.key in favouriteKeys, viewModel = viewModel)
                    }
                }
                if (query.isBlank()) {
                    AlphabetScrubber(
                        letters = SCRUBBER_LETTERS,
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
                keyboardActions = KeyboardActions(onSearch = { apps.firstOrNull()?.let(viewModel::launch) }),
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
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            )
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
private fun DrawerRow(app: AppInfo, isFavourite: Boolean, viewModel: LauncherViewModel) {
    var menuOpen by remember { mutableStateOf(false) }

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
            Text(app.label, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Light, maxLines = 1)
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(if (isFavourite) "Remove from favourites" else "Add to favourites") },
                onClick = {
                    menuOpen = false
                    if (isFavourite) viewModel.removeFavourite(app) else viewModel.addFavourite(app)
                },
            )
            DropdownMenuItem(text = { Text("App info") }, onClick = {
                menuOpen = false
                viewModel.repository.openAppInfo(app)
            })
            DropdownMenuItem(text = { Text("Uninstall") }, onClick = {
                menuOpen = false
                viewModel.repository.uninstall(app)
            })
        }
    }
}

@Composable
private fun AppIcon(app: AppInfo, viewModel: LauncherViewModel) {
    val sizePx = with(LocalDensity.current) { 36.dp.roundToPx() }
    val icon by produceState<ImageBitmap?>(null, app.key) {
        value = withContext(Dispatchers.Default) { viewModel.repository.loadIcon(app, sizePx) }
    }
    Box(Modifier.size(36.dp)) {
        icon?.let { Image(bitmap = it, contentDescription = null, modifier = Modifier.fillMaxSize()) }
    }
}

/** A–Z strip along the right edge: tap or drag to jump through the list. */
@Composable
private fun AlphabetScrubber(
    letters: List<Char>,
    onLetter: (Char) -> Unit,
    onRelease: () -> Unit,
    modifier: Modifier = Modifier,
) {
    fun pick(y: Float, height: Int) {
        val index = ((y / height) * letters.size).toInt().coerceIn(0, letters.lastIndex)
        onLetter(letters[index])
    }

    Column(
        modifier = modifier
            .pointerInput(letters) {
                detectTapGestures(onPress = { offset ->
                    pick(offset.y, size.height)
                    tryAwaitRelease()
                    onRelease()
                })
            }
            .pointerInput(letters) {
                detectVerticalDragGestures(
                    onDragStart = { offset -> pick(offset.y, size.height) },
                    onDragEnd = onRelease,
                    onDragCancel = onRelease,
                    onVerticalDrag = { change, _ -> pick(change.position.y, size.height) },
                )
            },
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        letters.forEach { letter ->
            Text(
                letter.toString(),
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
    }
}
