package com.rubenrups.launcher.ui

import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.rubenrups.launcher.notifications.NotificationItem
import com.rubenrups.launcher.notifications.NotificationStore

/**
 * Sends a PendingIntent that belongs to another app. Since Android 14 the sender has to opt in to letting
 * the target start an activity: the other app (a messenger, a music player) is in the background, so
 * without this the launch is silently blocked. We are the visible home screen, so allowing it is correct.
 * Returns false if the owner has already cancelled the intent.
 */
fun PendingIntent.sendAllowingLaunch(context: Context): Boolean {
    val options = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        ActivityOptions.makeBasic()
            .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
            .toBundle()
    } else {
        null
    }
    return try {
        send(context, 0, null, null, null, null, options)
        true
    } catch (_: PendingIntent.CanceledException) {
        false
    }
}

/** Opens what a notification points at, falling back to its app, and clears it if the app asked for that. */
fun openNotification(context: Context, item: NotificationItem) {
    val opened = item.contentIntent?.sendAllowingLaunch(context) == true
    if (!opened) {
        context.packageManager.getLaunchIntentForPackage(item.packageName)?.let {
            runCatching { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
    }
    if (item.autoCancel) NotificationStore.dismiss(item.key)
}
