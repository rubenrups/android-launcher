package com.rubenrups.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rubenrups.launcher.LauncherViewModel
import kotlinx.coroutines.launch

/** How far a finger has to travel, as a fraction of the screen height, to pull the drawer fully open. */
private const val FULL_PULL_FRACTION = 0.6f

@Composable
fun LauncherApp(viewModel: LauncherViewModel) {
    val drawerOpen by viewModel.drawerOpen.collectAsStateWithLifecycle()
    val settingsOpen by viewModel.settingsOpen.collectAsStateWithLifecycle()

    // 0 = drawer closed, 1 = fully open. Gestures set it directly so the drawer tracks the finger;
    // everything else (buttons, back, launching an app) animates it.
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val screenHeightDp = LocalConfiguration.current.screenHeightDp
    val fullPullPx = with(density) { screenHeightDp.dp.toPx() } * FULL_PULL_FRACTION
    // Only recompose the drawer's host when it appears or disappears, not on every animation frame.
    val drawerVisible by remember { derivedStateOf { progress.value > 0f } }

    LaunchedEffect(drawerOpen) {
        progress.animateTo(if (drawerOpen) 1f else 0f, tween(durationMillis = 260))
    }

    // Registered last = highest priority: settings closes before the drawer.
    BackHandler(enabled = drawerOpen) { viewModel.closeDrawer() }
    BackHandler(enabled = settingsOpen) { viewModel.setSettingsOpen(false) }

    Box(Modifier.fillMaxSize()) {
        HomeScreen(
            viewModel,
            onOpenDrawer = { focusSearch -> viewModel.openDrawer(focusSearch) },
            onDrawerDrag = { upPx -> scope.launch { progress.snapTo((upPx / fullPullPx).coerceIn(0f, 1f)) } },
            onDrawerRelease = { open ->
                if (open) {
                    viewModel.openDrawer(false)
                } else if (!drawerOpen) {
                    // A pull that was let go too early: slide back down.
                    scope.launch { progress.animateTo(0f, tween(durationMillis = 180)) }
                }
            },
            onOpenSettings = { viewModel.setSettingsOpen(true) },
        )

        if (drawerVisible) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationY = (1f - progress.value) * size.height * 0.5f
                        alpha = progress.value
                    },
            ) {
                AppDrawer(
                    viewModel,
                    onPull = { pulledPx ->
                        scope.launch { progress.snapTo((1f - pulledPx / fullPullPx).coerceIn(0f, 1f)) }
                    },
                    onPullEnd = { close ->
                        if (close) {
                            viewModel.closeDrawer()
                        } else {
                            scope.launch { progress.animateTo(1f, tween(durationMillis = 180)) }
                        }
                    },
                )
            }
        }

        AnimatedVisibility(
            visible = settingsOpen,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            SettingsScreen(viewModel, onClose = { viewModel.setSettingsOpen(false) })
        }
    }
}
