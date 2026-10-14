package com.rubenrups.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import com.rubenrups.launcher.DashboardEdge
import com.rubenrups.launcher.LauncherViewModel
import kotlinx.coroutines.launch

/** How far a finger has to travel, as a fraction of the screen height, to pull the drawer fully open. */
private const val FULL_PULL_FRACTION = 0.6f

@Composable
fun LauncherApp(viewModel: LauncherViewModel) {
    val drawerOpen by viewModel.drawerOpen.collectAsStateWithLifecycle()
    val settingsOpen by viewModel.settingsOpen.collectAsStateWithLifecycle()
    val dashboardOpen by viewModel.dashboardOpen.collectAsStateWithLifecycle()
    val onboardingOpen by viewModel.onboardingOpen.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val dashboardEdge by viewModel.dashboardEdge.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    // Launch animation: the whole screen shrinks and fades a little while an app starts, and eases back when we return.
    val launchPulse by viewModel.launchPulse.collectAsStateWithLifecycle()
    val launchScale = remember { Animatable(1f) }
    LaunchedEffect(launchPulse) {
        if (launchPulse == 0) return@LaunchedEffect
        launchScale.animateTo(0.9f, tween(durationMillis = 170))
        // If the app never appeared (launch failed, or it is slow), do not stay shrunk.
        delay(1_500)
        launchScale.animateTo(1f, tween(durationMillis = 250))
    }
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            if (launchScale.value != 1f) launchScale.animateTo(1f, tween(durationMillis = 260))
        }
    }

    // Look for a new build while the launcher is on screen: shortly after it appears, then at the chosen interval.
    LaunchedEffect(lifecycleOwner, settings.updateCheckMinutes) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            delay(3_000)
            while (true) {
                viewModel.checkForUpdate()
                delay(settings.updateCheckMinutes * 60_000L)
            }
        }
    }

    // Poll LibreLinkUp once a minute, only while the launcher is on screen; a login change polls straight away.
    LaunchedEffect(settings.glucoseEnabled, settings.glucoseEmail, settings.glucosePassword) {
        // Typing the login restarts this effect on every keystroke; the pause keeps half-typed logins off the server.
        delay(1_500)
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                viewModel.refreshGlucose()
                delay(60_000)
            }
        }
    }

    // 0 = drawer closed, 1 = fully open. Gestures set it directly so the drawer tracks the finger;
    // everything else (buttons, back, launching an app) animates it.
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val screenHeightDp = LocalConfiguration.current.screenHeightDp
    // Attached: the drawer sticks to the finger one-to-one; otherwise a shorter pull is enough to open it.
    val fullPullPx = with(density) { screenHeightDp.dp.toPx() } * if (settings.drawerAttached) 1f else FULL_PULL_FRACTION
    // Only recompose the drawer's host when it appears or disappears, not on every animation frame.
    val drawerVisible by remember { derivedStateOf { progress.value > 0f } }

    LaunchedEffect(drawerOpen) {
        progress.animateTo(if (drawerOpen) 1f else 0f, tween(durationMillis = 260))
    }

    // Registered last = highest priority: settings closes before the drawer.
    BackHandler(enabled = drawerOpen) { viewModel.closeDrawer() }
    BackHandler(enabled = dashboardOpen) { viewModel.setDashboardOpen(false) }
    BackHandler(enabled = settingsOpen) { viewModel.setSettingsOpen(false) }

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                val scale = launchScale.value
                scaleX = scale
                scaleY = scale
                alpha = 1f - (1f - scale) * 4f
            },
    ) {
        // Attached drawer: the home screen rides up with the drawer instead of staying put underneath it.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { if (settings.drawerAttached) translationY = -progress.value * size.height },
        ) {
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
        }

        if (drawerVisible) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        if (settings.drawerAttached) {
                            translationY = (1f - progress.value) * size.height
                        } else {
                            translationY = (1f - progress.value) * size.height * 0.5f
                            alpha = progress.value
                        }
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

        // The dashboard comes in from the side you swiped from, and leaves the same way.
        val slide = tween<IntOffset>(durationMillis = 280)
        AnimatedVisibility(
            visible = dashboardOpen,
            enter = when (dashboardEdge) {
                DashboardEdge.RIGHT -> slideInHorizontally(slide) { it } + fadeIn(tween(200))
                DashboardEdge.LEFT -> slideInHorizontally(slide) { -it } + fadeIn(tween(200))
                DashboardEdge.TOP -> slideInVertically(slide) { -it } + fadeIn(tween(200))
                DashboardEdge.BOTTOM -> slideInVertically(slide) { it } + fadeIn(tween(200))
                DashboardEdge.NONE -> fadeIn(tween(220)) + scaleIn(tween(220), initialScale = 0.94f)
            },
            exit = when (dashboardEdge) {
                DashboardEdge.RIGHT -> slideOutHorizontally(slide) { it } + fadeOut(tween(200))
                DashboardEdge.LEFT -> slideOutHorizontally(slide) { -it } + fadeOut(tween(200))
                DashboardEdge.TOP -> slideOutVertically(slide) { -it } + fadeOut(tween(200))
                DashboardEdge.BOTTOM -> slideOutVertically(slide) { it } + fadeOut(tween(200))
                DashboardEdge.NONE -> fadeOut(tween(180)) + scaleOut(tween(180), targetScale = 0.94f)
            },
        ) {
            DashboardScreen(
                viewModel,
                onClose = { viewModel.setDashboardOpen(false) },
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

        // The first-run setup guide sits on top of everything.
        if (onboardingOpen) OnboardingScreen(viewModel, onFinish = { viewModel.finishOnboarding() })
    }
}
