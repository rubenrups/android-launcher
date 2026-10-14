package com.rubenrups.launcher.ui

import android.Manifest
import android.app.NotificationManager
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.rubenrups.launcher.LauncherViewModel
import com.rubenrups.launcher.data.LauncherSettings

private fun hasBluetoothPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

/** The settings for everything that can open car mode by itself, silence the phone while driving, and call people. */
@Composable
internal fun CarAutomationGroups(viewModel: LauncherViewModel, settings: LauncherSettings) {
    val context = LocalContext.current

    // Permission statuses change in system screens; look again when we come back.
    var refresh by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) refresh++ }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val overlayOk = remember(refresh) { Settings.canDrawOverlays(context) }
    val policyOk = remember(refresh) { context.getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted }
    val bluetoothOk = remember(refresh) { hasBluetoothPermission(context) }
    var chooseDevices by remember { mutableStateOf(false) }

    val bluetoothPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.update { it.copy(carAutoBluetooth = true) }
        refresh++
    }
    val callPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.update { it.copy(callDirect = granted) }
        refresh++
    }
    // The phone picker hands back one number with a temporary right to read it: no contacts permission needed.
    val pickContact = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data ?: return@rememberLauncherForActivityResult
        val projection = arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER)
        runCatching {
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val name = cursor.getString(0).orEmpty()
                    val number = cursor.getString(1).orEmpty().filter { it.isDigit() || it == '+' }
                    viewModel.addCallContact(name, number)
                }
            }
        }
    }
    fun open(intent: Intent) {
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    Group("Open car mode automatically") {
        SwitchRow("When connected to car Bluetooth", settings.carAutoBluetooth) { on ->
            if (on && !bluetoothOk) {
                bluetoothPermission.launch(Manifest.permission.BLUETOOTH_CONNECT)
            } else {
                viewModel.update { it.copy(carAutoBluetooth = on) }
            }
        }
        if (settings.carAutoBluetooth) {
            NavRow("Choose devices", "${settings.carBluetoothDevices.size} selected") { chooseDevices = true }
        }
        SwitchRow("When the phone is plugged in", settings.carAutoCharging) { on -> viewModel.update { it.copy(carAutoCharging = on) } }
        if (settings.carAutoCharging) {
            SwitchRow("Only when held in landscape", settings.carChargingLandscapeOnly) { on ->
                viewModel.update { it.copy(carChargingLandscapeOnly = on) }
            }
        }
        SwitchRow(
            "Close it again afterwards",
            settings.carAutoClose,
            subtitle = "When the Bluetooth device disconnects or the charger is removed, if that is what opened it",
        ) { on -> viewModel.update { it.copy(carAutoClose = on) } }
        InfoRow("Opening from the background", if (overlayOk) "Allowed" else "Not allowed")
        if (!overlayOk) {
            NavRow("Allow display over other apps") {
                open(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")))
            }
        }
        Hint(
            "Android only lets an app start itself from the background if it may display over other apps, so allow " +
                "that for the triggers to work while another app is open. Without it, car mode still opens whenever you " +
                "next look at the launcher."
        )
    }
    Group("While car mode is open") {
        SwitchRow(
            "Do not disturb (priority only)",
            settings.carDnd,
            subtitle = "Calls from allowed contacts still ring. Your previous setting returns afterwards",
        ) { on ->
            viewModel.update { it.copy(carDnd = on) }
            if (on && !policyOk) open(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
        }
        if (settings.carDnd && !policyOk) {
            Hint("Android needs you to give Rups Launcher access to Do Not Disturb first.")
            NavRow("Open Do Not Disturb access") { open(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)) }
        }
    }
    Group("Call buttons") {
        settings.callContacts.forEachIndexed { index, contact ->
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(contact.name.ifBlank { contact.number }, color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
                Text(
                    "▲",
                    color = if (index > 0) Color.White else Color.White.copy(alpha = 0.2f),
                    fontSize = 16.sp,
                    modifier = Modifier.clickable(enabled = index > 0) { viewModel.moveCallContact(index, -1) }.padding(12.dp),
                )
                Text(
                    "▼",
                    color = if (index < settings.callContacts.lastIndex) Color.White else Color.White.copy(alpha = 0.2f),
                    fontSize = 16.sp,
                    modifier = Modifier.clickable(enabled = index < settings.callContacts.lastIndex) { viewModel.moveCallContact(index, 1) }.padding(12.dp),
                )
                Text("Remove", color = Accent, fontSize = 13.sp, modifier = Modifier.clickable { viewModel.removeCallContact(index) }.padding(12.dp))
            }
        }
        NavRow("Add a contact") {
            pickContact.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI))
        }
        SwitchRow(
            "Call straight away",
            settings.callDirect,
            subtitle = "Off: the dialer opens with the number filled in and you press call",
        ) { on ->
            if (on && context.checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
                callPermission.launch(Manifest.permission.CALL_PHONE)
            } else {
                viewModel.update { it.copy(callDirect = on) }
            }
        }
        Hint("Contacts show as buttons on the Call card in car mode. They stay on this device and are not in backups.")
    }

    if (chooseDevices) {
        BluetoothDevicesDialog(
            selected = settings.carBluetoothDevices,
            onToggle = { address ->
                viewModel.update {
                    it.copy(carBluetoothDevices = if (address in it.carBluetoothDevices) it.carBluetoothDevices - address else it.carBluetoothDevices + address)
                }
            },
            onOpenBluetoothSettings = { open(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) },
            onDismiss = { chooseDevices = false },
        )
    }
}

/** The phone's paired Bluetooth devices, with a tick on the ones that should open car mode. */
@Composable
private fun BluetoothDevicesDialog(
    selected: Set<String>,
    onToggle: (String) -> Unit,
    onOpenBluetoothSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val devices: List<BluetoothDevice> = remember {
        runCatching {
            context.getSystemService(BluetoothManager::class.java)?.adapter?.bondedDevices.orEmpty().toList()
        }.getOrDefault(emptyList())
    }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(24.dp), color = Color(0xF21A1A1A)) {
            Column(Modifier.heightIn(max = 520.dp).padding(top = 16.dp)) {
                Text(
                    "Car Bluetooth devices",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
                if (devices.isEmpty()) {
                    Text(
                        "No paired devices found. Pair your car in Android's Bluetooth settings first.",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    )
                    TextButton(onClick = onOpenBluetoothSettings, modifier = Modifier.padding(horizontal = 12.dp)) { Text("Open Bluetooth settings") }
                }
                LazyColumn(Modifier.weight(1f, fill = false)) {
                    items(devices, key = { it.address }) { device ->
                        val name = runCatching { device.name }.getOrNull() ?: device.address
                        Row(
                            Modifier.fillMaxWidth().clickable { onToggle(device.address) }.padding(horizontal = 24.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(name, color = Color.White, fontSize = 17.sp, modifier = Modifier.weight(1f))
                            Checkbox(checked = device.address in selected, onCheckedChange = null)
                        }
                    }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End).padding(8.dp)) { Text("Done") }
            }
        }
    }
}
