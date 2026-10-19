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

    private val SPLIT_LABELS = listOf("split screen", "split-screen", "splitscreen", "split", "gesplitst scherm", "scherm splitsen")

    /** Every window the service can see (Recents is its own window, which "the active window" does not always point at). */
    private fun roots(service: SplitScreenService): List<AccessibilityNodeInfo> =
        (service.windows.mapNotNull { it.root } + listOfNotNull(service.rootInActiveWindow)).distinctBy { System.identityHashCode(it) }

    private fun dump(service: SplitScreenService): String =
        roots(service).joinToString(" ; ") { r ->
            "${r.packageName}: " + r.labels().map { it.first }.distinct().take(8).joinToString(" | ")
        }

    /** Waits up to [timeoutMs] for a labelled node to appear in any window except [excludePackage]'s. */
    private suspend fun awaitNode(
        service: SplitScreenService,
        timeoutMs: Long,
        excludePackage: String?,
        matches: (String) -> Boolean,
    ): AccessibilityNodeInfo? {
        var waited = 0L
        while (waited <= timeoutMs) {
            roots(service)
                .filter { it.packageName?.toString() != excludePackage }
                .forEach { root -> root.labels().firstOrNull { matches(it.first) }?.let { return it.second } }
            delay(250)
            waited += 250
        }
        return null
    }

    /** Recents → tap the app's icon → "Split screen". Returns null on success, or what went wrong and what was on screen. */
    private suspend fun splitViaRecents(service: SplitScreenService, app: AppInfo, gapMs: Long): String? {
        if (!service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)) return "Recents could not be opened"
        // The icon above the app's card, in a window that is not the app's own.
        val icon = awaitNode(service, gapMs + 3000, app.packageName) { it.contains(app.label, ignoreCase = true) }
            ?: return "Recents: no icon for ${app.label} outside the app. Windows: " + dump(service)
        if (!icon.tap()) return "Recents: could not tap ${app.label}'s icon"
        val split = awaitNode(service, 2500, null) { l -> SPLIT_LABELS.any { l.equals(it, ignoreCase = true) } }
            ?: awaitNode(service, 500, null) { l -> SPLIT_LABELS.any { l.contains(it, ignoreCase = true) } }
            ?: return "Menu: no split-screen option. Windows: " + dump(service)
        if (!split.tap()) return "Menu: could not tap the split-screen option"
        return null
    }

    /** Opens [first], toggles split screen, then opens [second] beside it. [gapMs] is how long to wait for each step. */
    suspend fun launch(context: Context, repository: AppRepository, first: AppInfo, second: AppInfo, gapMs: Long, method: String) {
        val plain = { a: AppInfo -> a.shortcutId == null && a.isMainProfile }
        if (method != "action" && plain(first) && plain(second)) {
            // No service needed: a stop-over activity starts the first app, then the second one next to it.
            lastResult = "Adjacent launch: ${first.label} + ${second.label}"
            context.startActivity(
                Intent(context, com.rubenrups.launcher.SplitLaunchActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    .putExtra(com.rubenrups.launcher.SplitLaunchActivity.EXTRA_FIRST, first.component.flattenToString())
                    .putExtra(com.rubenrups.launcher.SplitLaunchActivity.EXTRA_SECOND, second.component.flattenToString())
                    .putExtra(com.rubenrups.launcher.SplitLaunchActivity.EXTRA_DELAY, gapMs.coerceAtMost(900L).coerceAtLeast(500L))
            )
            return
        }
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
