package com.rubenrups.launcher

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import com.rubenrups.launcher.data.GestureActions

/**
 * Runs the gesture actions that only the system may perform (lock, recents, quick settings, power menu,
 * screenshot). Android only lets an app do that through an accessibility service, so this one does nothing
 * else: it listens to no events and reads no screen content.
 */
class GestureService : AccessibilityService() {
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
        private var instance: GestureService? = null

        val isEnabled: Boolean get() = instance != null

        /** The global action behind a gesture key, or null when the key is not one of those (or the phone is too old). */
        private fun globalActionFor(key: String): Int? = when (key) {
            GestureActions.RECENTS -> GLOBAL_ACTION_RECENTS
            GestureActions.QUICK_SETTINGS -> GLOBAL_ACTION_QUICK_SETTINGS
            GestureActions.POWER_MENU -> GLOBAL_ACTION_POWER_DIALOG
            GestureActions.LOCK -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) GLOBAL_ACTION_LOCK_SCREEN else null
            GestureActions.SCREENSHOT -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) GLOBAL_ACTION_TAKE_SCREENSHOT else null
            else -> null
        }

        /** False when the service is not switched on (or the phone is too old), so the caller can point at settings. */
        fun perform(key: String): Boolean {
            val service = instance ?: return false
            val action = globalActionFor(key) ?: return false
            return service.performGlobalAction(action)
        }

        fun needsService(key: String) = globalActionFor(key) != null
    }
}

/** The torch needs no permission; its state is followed through a callback so the gesture can toggle it. */
object Flashlight {
    @Volatile
    private var on = false
    private var registered = false

    fun toggle(context: Context) {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return
        try {
            if (!registered) {
                registered = true
                manager.registerTorchCallback(object : CameraManager.TorchCallback() {
                    override fun onTorchModeChanged(cameraId: String, enabled: Boolean) { on = enabled }
                }, null)
            }
            val id = manager.cameraIdList.firstOrNull {
                val c = manager.getCameraCharacteristics(it)
                c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
                    c.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            } ?: return
            manager.setTorchMode(id, !on)
        } catch (_: CameraAccessException) {
            // Camera busy or no flash; nothing to do.
        }
    }
}
