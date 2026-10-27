package com.rubenrups.launcher

import android.accessibilityservice.AccessibilityService
import android.os.Build
import android.view.accessibility.AccessibilityEvent

/**
 * Locks the screen for the "Lock phone" gesture. Android only lets an app do that through an accessibility
 * service, so this one does nothing else: it listens to no events and reads no screen content.
 */
class LockScreenService : AccessibilityService() {
    override fun onServiceConnected() {
        instance = this
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        if (instance === this) instance = null
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    companion object {
        @Volatile
        private var instance: LockScreenService? = null

        val isEnabled: Boolean get() = instance != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

        /** False when the service is not switched on (or the phone is older than Android 9), so the caller can point at settings. */
        fun lock(): Boolean {
            val service = instance ?: return false
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
            return service.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
        }
    }
}
