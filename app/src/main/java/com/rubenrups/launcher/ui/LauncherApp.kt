package com.rubenrups.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rubenrups.launcher.LauncherViewModel

@Composable
fun LauncherApp(viewModel: LauncherViewModel) {
    val drawerOpen by viewModel.drawerOpen.collectAsStateWithLifecycle()
    val searchOpen by viewModel.searchOpen.collectAsStateWithLifecycle()
    val settingsOpen by viewModel.settingsOpen.collectAsStateWithLifecycle()

    // Registered last = highest priority: settings closes before the drawer.
    BackHandler(enabled = drawerOpen) { viewModel.closeDrawer() }
    BackHandler(enabled = searchOpen) { viewModel.closeSearch() }
    BackHandler(enabled = settingsOpen) { viewModel.setSettingsOpen(false) }

    Box(Modifier.fillMaxSize()) {
        HomeScreen(
            viewModel,
            onOpenDrawer = { viewModel.openDrawer() },
            onOpenSearch = { viewModel.openSearch() },
            onOpenSettings = { viewModel.setSettingsOpen(true) },
        )

        AnimatedVisibility(
            visible = drawerOpen,
            enter = slideInVertically { it / 3 } + fadeIn(),
            exit = slideOutVertically { it / 3 } + fadeOut(),
        ) {
            AppDrawer(viewModel)
        }

        AnimatedVisibility(visible = searchOpen, enter = fadeIn(), exit = fadeOut()) {
            SearchScreen(
                viewModel,
                onClose = { viewModel.closeSearch() },
                onOpenDrawer = {
                    viewModel.closeSearch()
                    viewModel.openDrawer()
                },
                onOpenSettings = { viewModel.setSettingsOpen(true) },
            )
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
