package com.rubenrups.launcher.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rubenrups.launcher.LauncherViewModel
import com.rubenrups.launcher.data.ContextProfile
import com.rubenrups.launcher.data.LauncherSettings
import com.rubenrups.launcher.data.ProfileTrigger
import com.rubenrups.launcher.data.formatMinutes
import java.util.Calendar
import java.util.UUID

private val ProfileDays = listOf(
    Calendar.MONDAY to "Mon",
    Calendar.TUESDAY to "Tue",
    Calendar.WEDNESDAY to "Wed",
    Calendar.THURSDAY to "Thu",
    Calendar.FRIDAY to "Fri",
    Calendar.SATURDAY to "Sat",
    Calendar.SUNDAY to "Sun",
)

/** Context profiles: a list, and an editor for the one you open. */
@Composable
internal fun ProfilesPage(viewModel: LauncherViewModel, settings: LauncherSettings) {
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    val active by viewModel.activeProfile.collectAsStateWithLifecycle()
    val editing = settings.profiles.firstOrNull { it.id == editingId }

    if (editing == null) {
        Group("Profiles") {
            settings.profiles.forEach { profile ->
                val status = when {
                    active?.id == profile.id -> "On now"
                    else -> profile.trigger.label
                }
                NavRow(profile.name.ifBlank { "Profile" }, status) { editingId = profile.id }
            }
            NavRow("New profile") {
                val id = UUID.randomUUID().toString()
                viewModel.update {
                    it.copy(profiles = it.profiles + ContextProfile(id, "Profile ${it.profiles.size + 1}", favourites = it.favourites))
                }
                editingId = id
            }
            Hint(
                "A profile changes what the home screen shows: its own favourites, apps hidden for a while, parts of the " +
                    "home screen switched off, and optionally car mode. It switches on by itself (a time window, charging, a " +
                    "Bluetooth device) or by hand: use the home menu's Switch profile, or give a gesture the Switch profile action. " +
                    "A profile you switch on by hand stays on until you switch again."
            )
        }
        return
    }
    ProfileEditor(viewModel, settings, editing, isActive = active?.id == editing.id, onBack = { editingId = null })
}

@Composable
private fun ProfileEditor(
    viewModel: LauncherViewModel,
    settings: LauncherSettings,
    profile: ContextProfile,
    isActive: Boolean,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val installed by viewModel.installedApps.collectAsStateWithLifecycle()
    var pickingHidden by remember { mutableStateOf(false) }
    var pickingDevice by remember { mutableStateOf(false) }
    fun change(transform: (ContextProfile) -> ContextProfile) =
        viewModel.update { s -> s.copy(profiles = s.profiles.map { if (it.id == profile.id) transform(it) else it }) }

    val bluetoothPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) pickingDevice = true
    }
    fun chooseDevice() {
        val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) bluetoothPermission.launch(Manifest.permission.BLUETOOTH_CONNECT) else pickingDevice = true
    }

    Group {
        NavRow("‹ All profiles") { onBack() }
    }
    Group("Name") {
        Field(profile.name, "Profile name") { v -> change { it.copy(name = v) } }
        SwitchRow(
            "Use this profile now",
            settings.profileManual == profile.id,
            subtitle = if (isActive) "It is on" else "Switches it on by hand; it stays on until you switch again",
        ) { on -> viewModel.update { it.copy(profileManual = if (on) profile.id else "") } }
    }
    Group("Switches on") {
        ProfileTrigger.entries.forEach { trigger ->
            ChoiceRow(trigger.label, selected = profile.trigger == trigger) { change { it.copy(trigger = trigger) } }
        }
        when (profile.trigger) {
            ProfileTrigger.TIME -> {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    ProfileDays.forEach { (day, label) ->
                        val on = day in profile.days
                        Text(
                            label,
                            color = if (on) Color(0xFF14181B) else Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .weight(1f)
                                .background(if (on) Color.White else Color.White.copy(alpha = 0.1f), RoundedCornerShape(10.dp))
                                .clickable { change { it.copy(days = if (on) it.days - day else it.days + day) } }
                                .padding(vertical = 10.dp),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                SliderRow("From", formatMinutes(profile.startMin), profile.startMin / 30f, 0f..47f) { v -> change { it.copy(startMin = v.toInt() * 30) } }
                SliderRow("Until", formatMinutes(profile.endMin), profile.endMin / 30f, 0f..47f) { v -> change { it.copy(endMin = v.toInt() * 30) } }
                Hint("A window that ends before it starts (22:00 until 06:00) runs overnight.")
            }
            ProfileTrigger.BLUETOOTH -> {
                NavRow("Device", profile.bluetoothName.ifBlank { "None chosen" }) { chooseDevice() }
                Hint("Switches on while this device is connected. Only connections made while the launcher was installed are seen.")
            }
            ProfileTrigger.CHARGING -> Hint("Switches on while the phone is plugged in or on a wireless charger.")
            ProfileTrigger.MANUAL -> Hint("Never switches on by itself.")
        }
        Hint("If more than one automatic profile matches, the first one in the list wins. A profile you switch on by hand beats them all.")
    }
    Group("Favourites") {
        SwitchRow(
            "Own favourites",
            profile.ownFavourites,
            subtitle = "${profile.favourites.size} saved. While this profile is on, adding, removing and reordering favourites changes this list.",
        ) { on -> change { it.copy(ownFavourites = on) } }
    }
    Group("Apps to hide") {
        val chosen = installed.filter { it.key in profile.hiddenApps }.sortedBy { it.label.lowercase() }
        chosen.forEach { app ->
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(app.label, color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
                Text(
                    "Remove",
                    color = Accent,
                    fontSize = 13.sp,
                    modifier = Modifier.clickable { change { it.copy(hiddenApps = it.hiddenApps - app.key) } }.padding(12.dp),
                )
            }
        }
        NavRow("Choose apps") { pickingHidden = true }
        Hint("Hidden from the home screen, the drawer and search while this profile is on. Nothing is uninstalled.")
    }
    Group("Home screen while it is on") {
        SwitchRow("Hide the clock", profile.hideClock) { v -> change { it.copy(hideClock = v) } }
        SwitchRow("Hide notifications", profile.hideNotifications) { v -> change { it.copy(hideNotifications = v) } }
        SwitchRow("Hide the media player", profile.hideMedia) { v -> change { it.copy(hideMedia = v) } }
        SwitchRow("Hide the recent apps row", profile.hideRecents) { v -> change { it.copy(hideRecents = v) } }
        SwitchRow("Open car mode", profile.openCar, subtitle = "Opens when the profile switches on, closes when it ends") { v ->
            change { it.copy(openCar = v) }
        }
    }
    Group {
        NavRow("Delete this profile") {
            viewModel.update { s ->
                s.copy(profiles = s.profiles.filter { it.id != profile.id }, profileManual = if (s.profileManual == profile.id) "" else s.profileManual)
            }
            onBack()
        }
    }

    if (pickingDevice) {
        BluetoothDevicesDialog(
            title = "Bluetooth device for ${profile.name.ifBlank { "this profile" }}",
            selected = setOf(profile.bluetoothAddress),
            onToggle = { address ->
                val name = runCatching {
                    context.getSystemService(android.bluetooth.BluetoothManager::class.java)
                        ?.adapter?.bondedDevices?.firstOrNull { it.address == address }?.name
                }.getOrNull().orEmpty()
                change { it.copy(bluetoothAddress = address, bluetoothName = name.ifBlank { address }) }
                pickingDevice = false
            },
            onOpenBluetoothSettings = {
                context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            },
            onDismiss = { pickingDevice = false },
        )
    }
    if (pickingHidden) {
        Dialog(onDismissRequest = { pickingHidden = false }) {
            Surface(shape = RoundedCornerShape(24.dp), color = Color(0xF21A1A1A)) {
                var query by remember { mutableStateOf("") }
                val listState = rememberLazyListState()
                val shown = installed.filter { matchesQuery(query, it.label) }
                Column(Modifier.heightIn(max = 560.dp).padding(top = 16.dp)) {
                    Text(
                        "Apps to hide in this profile",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    )
                    PickerSearchField(query) { query = it }
                    LazyColumn(state = listState, modifier = Modifier.weight(1f, fill = false).verticalScrollbar(listState)) {
                        items(shown, key = { it.key }) { app ->
                            val hidden = app.key in profile.hiddenApps
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { change { it.copy(hiddenApps = if (hidden) it.hiddenApps - app.key else it.hiddenApps + app.key) } }
                                    .padding(horizontal = 24.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                AppIcon(app, viewModel, size = 32.dp)
                                Text(app.label, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Light, maxLines = 1, modifier = Modifier.weight(1f))
                                Checkbox(checked = hidden, onCheckedChange = null)
                            }
                        }
                    }
                    TextButton(onClick = { pickingHidden = false }, modifier = Modifier.align(Alignment.End).padding(8.dp)) { Text("Done") }
                }
            }
        }
    }
}
