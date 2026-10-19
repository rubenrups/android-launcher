package com.rubenrups.launcher.data

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import kotlinx.coroutines.delay

/** A saved pair of apps that opens in split screen: [firstKey] fills the top (or left) pane. */
data class SplitPair(val id: String, val name: String, val firstKey: String, val secondKey: String)

/**
 * Does nothing but press the system's "split screen" action when asked. Android has no public way to start a
 * split-screen pair, so the launcher opens the first app, toggles split screen through this service, then opens
 * the second app next to it.
 */
class SplitScreenService : AccessibilityService() {
    override fun onServiceConnected() {
        instance = this
    }

    override fun onUnbind(intent: Intent?): Boolean {
        if (instance === this) instance = null
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    companion object {
        @Volatile
        var instance: SplitScreenService? = null
            private set

        val isEnabled: Boolean get() = instance != null
    }
}

object SplitScreen {
    /** What the last attempt did, for the status row in settings (toasts are easy to miss while apps swap). */
    @Volatile
    var lastResult: String = "No attempt yet"
        private set

    /** Opens [first], toggles split screen, then opens [second] beside it. [gapMs] is how long to wait for each step. */
    suspend fun launch(context: Context, repository: AppRepository, first: AppInfo, second: AppInfo, gapMs: Long) {
        val service = SplitScreenService.instance
        if (service == null) {
            lastResult = "Service is not connected: turn it on in accessibility settings"
            Toast.makeText(context, "Turn on the Rups Launcher split-screen service first", Toast.LENGTH_LONG).show()
            runCatching {
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            return
        }
        lastResult = "Opening ${first.label}…"
        repository.launch(first)
        delay(gapMs)
        val pressed = service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN)
        lastResult = if (pressed) "Split action accepted by the system; then opened ${second.label}" else "The system refused the split-screen action"
        Toast.makeText(
            context,
            if (pressed) "Split screen: pressed, opening ${second.label}" else "Split screen: the system refused the action",
            Toast.LENGTH_SHORT,
        ).show()
        delay(gapMs)
        repository.launch(second, adjacent = true)
    }
}
