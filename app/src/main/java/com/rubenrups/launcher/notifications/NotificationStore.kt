package com.rubenrups.launcher.notifications

import android.app.PendingIntent
import android.media.session.MediaController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class NotificationItem(
    val key: String,
    val packageName: String,
    val title: String,
    val text: String,
    val contentIntent: PendingIntent?,
    val postTime: Long,
    /** Tapping it should also clear it (the app asked for that). */
    val autoCancel: Boolean,
)

/** The ongoing turn-by-turn notification of a navigation app (Google Maps, Waze). */
data class NavigationState(
    val packageName: String,
    /** Usually the distance to the next turn, or the street. */
    val title: String,
    /** The instruction, e.g. "Turn left onto Main St". */
    val text: String,
    /** Trip summary such as "12 min · 5.3 km · 15:42". */
    val summary: String,
    val contentIntent: PendingIntent?,
)

data class MediaState(
    val title: String,
    val artist: String,
    val isPlaying: Boolean,
    val controller: MediaController,
)

/**
 * Process-wide bridge between the system-bound [LauncherNotificationService] and the UI.
 * The service is created by the framework, so it cannot be injected; a singleton is the pragmatic seam.
 */
object NotificationStore {
    private val _notifications = MutableStateFlow<List<NotificationItem>>(emptyList())
    val notifications: StateFlow<List<NotificationItem>> = _notifications.asStateFlow()

    private val _media = MutableStateFlow<MediaState?>(null)
    val media: StateFlow<MediaState?> = _media.asStateFlow()

    private val _navigation = MutableStateFlow<NavigationState?>(null)
    val navigation: StateFlow<NavigationState?> = _navigation.asStateFlow()

    /** False until the user grants notification access and the system binds our service. */
    private val _listenerConnected = MutableStateFlow(false)
    val listenerConnected: StateFlow<Boolean> = _listenerConnected.asStateFlow()

    /** Set by the service while it is connected; lets the UI clear a notification by key. */
    @Volatile
    internal var dismisser: ((String) -> Unit)? = null

    fun dismiss(key: String) {
        dismisser?.invoke(key)
    }

    internal fun setNotifications(items: List<NotificationItem>) { _notifications.value = items }
    internal fun setNavigation(state: NavigationState?) { _navigation.value = state }
    internal fun setMedia(state: MediaState?) { _media.value = state }
    internal fun setConnected(connected: Boolean) { _listenerConnected.value = connected }
}
