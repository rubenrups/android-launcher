package com.rubenrups.launcher

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import com.rubenrups.launcher.ui.LauncherApp
import com.rubenrups.launcher.ui.LauncherTheme

class MainActivity : ComponentActivity() {

    private val viewModel: LauncherViewModel by viewModels()

    // "off", "status" or "both": which system bars the launcher hides (the full screen setting).
    private var fullscreenMode = "off"

    // When we last became visible / were hidden, to tell "Home pressed from another app" from "Home pressed on us".
    private var startedAt = 0L
    private var hasBeenStopped = false

    override fun onStart() {
        super.onStart()
        startedAt = SystemClock.uptimeMillis()
        viewModel.widgets.startListening()
    }

    override fun onStop() {
        hasBeenStopped = true
        viewModel.widgets.stopListening()
        super.onStop()
    }

    /** Hides the status bar (and the navigation bar) while the launcher is up; a swipe from the edge shows them briefly. */
    private fun applyFullscreen() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        when (fullscreenMode) {
            "status" -> {
                controller.show(WindowInsetsCompat.Type.navigationBars())
                controller.hide(WindowInsetsCompat.Type.statusBars())
            }
            "both" -> controller.hide(WindowInsetsCompat.Type.systemBars())
            else -> controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    // The bars come back after the notification shade, a dialog or the keyboard: hide them again when we regain focus.
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyFullscreen()
    }

    /** With a hardware keyboard attached, typing on the home screen starts a search. */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val typed = event.unicodeChar
        if (viewModel.canTypeToSearch() && event.isPrintingKey && typed != 0 &&
            !event.isCtrlPressed && !event.isAltPressed && !event.isMetaPressed
        ) {
            viewModel.openDrawer(focusSearch = true, initialQuery = typed.toChar().toString())
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    // Widget binding and configuration answer through the classic result callback.
    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        viewModel.widgets.handleResult(this, requestCode, resultCode)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Started by a Bluetooth / charging trigger: go straight into car mode.
        if (intent?.getBooleanExtra(CarMode.EXTRA_OPEN, false) == true) viewModel.setDashboardOpen(true)
        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            LaunchedEffect(settings.fullscreenMode) {
                fullscreenMode = settings.fullscreenMode
                applyFullscreen()
            }
            LauncherTheme(settings.accentColor, settings.fontFamily) {
                LauncherApp(viewModel)
            }
        }
    }

    /**
     * Pressing Home while we are already on top returns to the plain home screen. Pressing it from another app
     * brings us back as we were, so car mode stays up (unless "remember car mode" is off).
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(CarMode.EXTRA_OPEN, false)) viewModel.setDashboardOpen(true)
        if (intent.action == Intent.ACTION_MAIN && Intent.CATEGORY_HOME in (intent.categories ?: emptySet())) {
            // Depending on the Android version the new intent arrives just before or just after we become visible
            // again, so "we were hidden" also counts when we only just came back.
            val stillHidden = !lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            val justReturned = hasBeenStopped && SystemClock.uptimeMillis() - startedAt < RETURN_WINDOW_MS
            viewModel.closeAll(fromAnotherApp = stillHidden || justReturned)
        }
    }

    private companion object {
        const val RETURN_WINDOW_MS = 1_000L
    }
}
