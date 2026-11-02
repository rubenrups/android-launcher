package com.rubenrups.launcher

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import com.rubenrups.launcher.data.LauncherPrefsHolder
import com.rubenrups.launcher.data.ProfileSignals

/** Opens or closes car mode when a chosen Bluetooth device connects or the phone is plugged in. */
class CarTriggerReceiver : BroadcastReceiver() {

    @Suppress("DEPRECATION")
    override fun onReceive(context: Context, intent: Intent) {
        val settings = LauncherPrefsHolder.get(context).settings.value
        when (intent.action) {
            BluetoothDevice.ACTION_ACL_CONNECTED, BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                } else {
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                }
                // Context profiles follow every device, whether or not car mode cares about it.
                device?.address?.let { ProfileSignals.onBluetooth(context, it, intent.action == BluetoothDevice.ACTION_ACL_CONNECTED) }
                if (!settings.carAutoBluetooth) return
                if (device?.address !in settings.carBluetoothDevices) return
                if (intent.action == BluetoothDevice.ACTION_ACL_CONNECTED) {
                    CarMode.autoOpen(context)
                } else if (settings.carAutoClose) {
                    CarMode.autoClose(context)
                }
            }
            Intent.ACTION_POWER_CONNECTED -> {
                ProfileSignals.onPower(context, true)
                if (!settings.carAutoCharging) return
                val landscape = context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                if (!settings.carChargingLandscapeOnly || landscape) CarMode.autoOpen(context)
            }
            Intent.ACTION_POWER_DISCONNECTED -> {
                ProfileSignals.onPower(context, false)
                if (settings.carAutoCharging && settings.carAutoClose) CarMode.autoClose(context)
            }
        }
    }
}
