package com.rubenrups.launcher.notifications

import android.app.Notification
import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.graphics.drawable.toBitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class LauncherNotificationService : NotificationListenerService() {

    private val listenerComponent by lazy { ComponentName(this, LauncherNotificationService::class.java) }
    private val sessionManager by lazy { getSystemService(MediaSessionManager::class.java) }
    private var controller: MediaController? = null

    private val controllerCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = publishMedia()
        override fun onMetadataChanged(metadata: MediaMetadata?) = publishMedia()
        override fun onSessionDestroyed() = refreshMedia(activeSessions())
    }

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener {
        refreshMedia(it.orEmpty())
    }

    override fun onListenerConnected() {
        NotificationStore.setConnected(true)
        NotificationStore.dismisser = { key -> runCatching { cancelNotification(key) } }
        runCatching { sessionManager.addOnActiveSessionsChangedListener(sessionsListener, listenerComponent) }
        refreshMedia(activeSessions())
        refreshNotifications()
    }

    override fun onListenerDisconnected() {
        NotificationStore.setConnected(false)
        NotificationStore.dismisser = null
        runCatching { sessionManager.removeOnActiveSessionsChangedListener(sessionsListener) }
        controller?.unregisterCallback(controllerCallback)
        controller = null
        NotificationStore.setMedia(null)
        NotificationStore.setNavigation(null)
        NotificationStore.setNotifications(emptyList())
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) = refreshNotifications()
    override fun onNotificationRemoved(sbn: StatusBarNotification) = refreshNotifications()

    private fun refreshNotifications() {
        val active = runCatching { activeNotifications }.getOrNull().orEmpty()
        NotificationStore.setNavigation(active.firstNotNullOfOrNull { it.toNavigation() })
        NotificationStore.setNotifications(
            active
                .filter { it.packageName != packageName && !it.isOngoing && !it.isGroupSummary() && !it.isTransport() }
                .mapNotNull { it.toItem() }
                .sortedByDescending { it.postTime }
        )
    }

    /** Navigation apps keep one ongoing notification with the next instruction while a route is active. */
    private fun StatusBarNotification.toNavigation(): NavigationState? {
        if (packageName !in NAVIGATION_PACKAGES || !isOngoing) return null
        val extras = notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val summary = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString().orEmpty()
        if (title.isBlank() && text.isBlank()) return null
        // Reading the picture means inflating the notification's layout, so only redo it when the text has changed.
        val signature = "$packageName|$title|$text"
        if (signature != lastNavSignature) {
            lastNavSignature = signature
            lastManeuver = runCatching { findManeuverBitmap() }.getOrNull() ?: lastManeuver
        }
        return NavigationState(packageName, title, text, summary, notification.contentIntent, lastManeuver)
    }

    private var lastNavSignature: String? = null
    private var lastManeuver: Bitmap? = null

    /**
     * The turn arrow of a navigation notification. Some apps put it in the notification's large icon; Google Maps
     * draws it inside a custom layout, so failing that we inflate that layout and take its biggest picture.
     */
    @Suppress("DEPRECATION")
    private fun StatusBarNotification.findManeuverBitmap(): Bitmap? {
        notification.getLargeIcon()?.loadDrawable(this@LauncherNotificationService)?.let { return it.toLimitedBitmap() }
        val views = notification.bigContentView ?: notification.contentView ?: return null
        val root = FrameLayout(this@LauncherNotificationService)
        val inflated = views.apply(this@LauncherNotificationService, root)
        var best: Drawable? = null
        var bestArea = 0
        fun visit(view: View) {
            if (view is ImageView && view.visibility == View.VISIBLE) {
                val d = view.drawable
                val area = (d?.intrinsicWidth ?: 0) * (d?.intrinsicHeight ?: 0)
                if (d != null && area > bestArea) {
                    best = d
                    bestArea = area
                }
            }
            if (view is ViewGroup) for (i in 0 until view.childCount) visit(view.getChildAt(i))
        }
        visit(inflated)
        // Ignore tiny pictures such as status dots.
        return best?.takeIf { bestArea >= MIN_MANEUVER_AREA }?.toLimitedBitmap()
    }

    private fun Drawable.toLimitedBitmap(): Bitmap? {
        val w = intrinsicWidth.takeIf { it > 0 } ?: return null
        val h = intrinsicHeight.takeIf { it > 0 } ?: return null
        val scale = minOf(1f, MAX_MANEUVER_PX / maxOf(w, h).toFloat())
        return toBitmap((w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1))
    }

    private fun StatusBarNotification.isGroupSummary() =
        notification.flags and Notification.FLAG_GROUP_SUMMARY != 0

    private fun StatusBarNotification.isTransport() =
        notification.category == Notification.CATEGORY_TRANSPORT

    private fun StatusBarNotification.toItem(): NotificationItem? {
        val extras = notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        if (title.isBlank() && text.isBlank()) return null
        val autoCancel = notification.flags and Notification.FLAG_AUTO_CANCEL != 0
        return NotificationItem(key, packageName, title, text, notification.contentIntent, postTime, autoCancel)
    }

    private fun activeSessions(): List<MediaController> =
        runCatching { sessionManager.getActiveSessions(listenerComponent) }.getOrNull().orEmpty()

    private fun appLabel(packageName: String): String =
        runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString() }
            .getOrDefault(packageName)

    private fun refreshMedia(sessions: List<MediaController>) {
        val preferred = sessions.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: sessions.firstOrNull()
        if (preferred?.sessionToken != controller?.sessionToken) {
            controller?.unregisterCallback(controllerCallback)
            controller = preferred
            preferred?.registerCallback(controllerCallback)
        }
        publishMedia()
    }

    private fun publishMedia() {
        val c = controller
        if (c == null) {
            NotificationStore.setMedia(null)
            return
        }
        val state = c.playbackState?.state
        val active = state == PlaybackState.STATE_PLAYING || state == PlaybackState.STATE_PAUSED ||
            state == PlaybackState.STATE_BUFFERING
        // Some players publish no track title; fall back to the app's name rather than hiding the controls.
        val title = c.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() }
            ?: if (active) appLabel(c.packageName) else null
        if (title == null) {
            NotificationStore.setMedia(null)
            return
        }
        NotificationStore.setMedia(
            MediaState(
                title = title,
                artist = c.metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty(),
                isPlaying = c.playbackState?.state == PlaybackState.STATE_PLAYING,
                controller = c,
                customActions = c.playbackState?.customActions.orEmpty(),
                art = c.metadata?.let {
                    it.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                        ?: it.getBitmap(MediaMetadata.METADATA_KEY_ART)
                        ?: it.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
                },
            )
        )
    }

    private companion object {
        const val MIN_MANEUVER_AREA = 24 * 24
        const val MAX_MANEUVER_PX = 256
        val NAVIGATION_PACKAGES = setOf("com.google.android.apps.maps", "com.waze")
    }
}
