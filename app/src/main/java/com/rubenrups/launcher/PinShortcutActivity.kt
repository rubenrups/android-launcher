package com.rubenrups.launcher

import android.content.pm.LauncherApps
import android.os.Bundle
import android.os.Process
import android.os.UserManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import com.rubenrups.launcher.data.LauncherPrefsHolder
import com.rubenrups.launcher.data.shortcutKey

/**
 * Receives "add to home screen" requests from apps such as Chrome (ShortcutManager.requestPinShortcut).
 * Without a handler for CONFIRM_PIN_SHORTCUT the request silently goes nowhere.
 */
class PinShortcutActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val launcherApps = getSystemService(LauncherApps::class.java)
        val request = launcherApps?.getPinItemRequest(intent)
        val info = request?.takeIf { it.isValid && it.requestType == LauncherApps.PinItemRequest.REQUEST_TYPE_SHORTCUT }
            ?.shortcutInfo
        if (request == null || info == null) {
            finish()
            return
        }

        val label = (info.shortLabel ?: info.longLabel ?: info.id).toString()
        val profileId = if (info.userHandle == Process.myUserHandle()) 0L
        else getSystemService(UserManager::class.java).getSerialNumberForUser(info.userHandle)

        fun accept(favourite: Boolean) {
            if (request.accept() && favourite) {
                val key = shortcutKey(info.`package`, info.id, profileId)
                LauncherPrefsHolder.get(this).update {
                    if (key in it.favourites) it else it.copy(favourites = it.favourites + key)
                }
            }
            finish()
        }

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                PinDialog(
                    label = label,
                    onFavourite = { accept(true) },
                    onDrawer = { accept(false) },
                    onCancel = { finish() },
                )
            }
        }
    }
}

@Composable
private fun PinDialog(label: String, onFavourite: () -> Unit, onDrawer: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Add shortcut") },
        text = { Text("\"$label\" will appear in your app drawer. Also pin it to the home screen?") },
        confirmButton = { TextButton(onClick = onFavourite) { Text("Add to favourites") } },
        dismissButton = {
            TextButton(onClick = onDrawer) { Text("Drawer only") }
        },
    )
}
