package com.rubenrups.launcher

import android.annotation.SuppressLint
import android.content.Context

/**
 * Opens the notification shade. There is no public API for this; the framework method is reached
 * via reflection and needs the (normal) EXPAND_STATUS_BAR permission. It can break on future
 * Android versions, so failure is silent by design.
 */
@SuppressLint("WrongConstant")
fun expandNotificationShade(context: Context) {
    runCatching {
        val service = context.getSystemService("statusbar")
        Class.forName("android.app.StatusBarManager")
            .getMethod("expandNotificationsPanel")
            .invoke(service)
    }
}
