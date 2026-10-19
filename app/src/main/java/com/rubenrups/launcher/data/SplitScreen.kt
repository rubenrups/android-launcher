package com.rubenrups.launcher.data

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
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

/** The visible words on the current window, for finding buttons by label and for saying what was there when one is missing. */
private fun AccessibilityNodeInfo.labels(out: MutableList<Pair<String, AccessibilityNodeInfo>> = ArrayList()): MutableList<Pair<String, AccessibilityNodeInfo>> {
    val text = (text ?: contentDescription)?.toString()?.trim().orEmpty()
    if (text.isNotEmpty()) out.add(text to this)
    for (i in 0 until childCount) getChild(i)?.labels(out)
    return out
}

/** Taps the node, or the nearest ancestor that can be tapped. */
private fun AccessibilityNodeInfo.tap(): Boolean {
    var node: AccessibilityNodeInfo? = this
    while (node != null) {
        if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
        node = node.parent
    }
    return false
}

object SplitScreen {
    /** What the last attempt did, for the status row in settings (toasts are easy to miss while apps swap). */
    @Volatile
    var lastResult: String = "No attempt yet"
        private set

    /** Presses two harmless actions and reports what the system answered, to tell "service blocked" from "split screen blocked". */
    suspend fun diagnose() {
        val service = SplitScreenService.instance
        if (service == null) {
            lastResult = "Test: the service is not connected"
            return
        }
        val shade = service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
        delay(700)
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        delay(300)
        val split = service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN)
        delay(500)
        // If that toggle did something on the home screen, press it again to undo it.
        if (split) service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN)
        lastResult = "Test: notifications=$shade, split screen=$split (Android ${android.os.Build.VERSION.RELEASE}, ${android.os.Build.MODEL})"
    }

    private val SPLIT_LABELS = listOf("split screen", "split-screen", "splitscreen", "split", "gesplitst scherm", "scherm splitsen", "splitscreen")

    /** Recents → tap the app's icon → "Split screen". Returns null on success, or what went wrong and what was on screen. */
    private suspend fun splitViaRecents(service: SplitScreenService, app: AppInfo, gapMs: Long): String? {
        if (!service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)) return "Recents could not be opened"
        delay(gapMs)
        var root = service.rootInActiveWindow ?: return "Recents opened, but its contents cannot be read"
        var labels = root.labels()
        val icon = labels.firstOrNull { it.first.contains(app.label, ignoreCase = true) }
            ?: return "Recents: no icon for ${app.label}. Saw: " + labels.map { it.first }.distinct().take(25).joinToString(" | ")
        if (!icon.second.tap()) return "Recents: could not tap ${app.label}'s icon"
        delay(gapMs / 2 + 300)
        root = service.rootInActiveWindow ?: return "Tapped the icon, but the menu cannot be read"
        labels = root.labels()
        val split = labels.firstOrNull { l -> SPLIT_LABELS.any { l.first.equals(it, ignoreCase = true) } }
            ?: labels.firstOrNull { l -> SPLIT_LABELS.any { l.first.contains(it, ignoreCase = true) } }
            ?: return "Menu: no split-screen option. Saw: " + labels.map { it.first }.distinct().take(25).joinToString(" | ")
        if (!split.second.tap()) return "Menu: could not tap \"${split.first}\""
        return null
    }

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
        var pressed = service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN)
        if (!pressed) {
            // Some phones (this one: Android 16) refuse the action. Do what you would do by hand in Recents instead.
            val error = splitViaRecents(service, first, gapMs)
            if (error != null) {
                lastResult = error
                Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                return
            }
            pressed = true
        }
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
