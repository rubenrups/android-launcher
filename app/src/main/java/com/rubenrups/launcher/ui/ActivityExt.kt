package com.rubenrups.launcher.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

/** Compose's LocalContext may be a ContextWrapper around the Activity; unwrap it. */
fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
