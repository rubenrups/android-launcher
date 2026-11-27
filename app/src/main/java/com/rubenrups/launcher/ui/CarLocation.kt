package com.rubenrups.launcher.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.rubenrups.launcher.data.GeoFix

internal fun hasLocationPermission(context: Context): Boolean =
    context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

/**
 * Follows the phone's position while this is on screen (and only then, so it needs just the "while using the app"
 * location permission) and hands every fix to [onFix]. Does nothing when [enabled] is false or permission is missing.
 */
@Composable
internal fun TrackLocation(enabled: Boolean, minDistanceM: Float = 10f, intervalMs: Long = 20_000L, onFix: (Location) -> Unit) {
    val context = LocalContext.current
    val current by rememberUpdatedState(onFix)
    DisposableEffect(enabled) {
        val manager = context.getSystemService(LocationManager::class.java)
        val listener = LocationListener { location -> current(location) }
        if (enabled && manager != null && hasLocationPermission(context)) {
            listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).forEach { provider ->
                runCatching {
                    if (manager.isProviderEnabled(provider)) {
                        manager.requestLocationUpdates(provider, intervalMs, minDistanceM, listener, Looper.getMainLooper())
                    }
                }
            }
        }
        onDispose { runCatching { manager?.removeUpdates(listener) } }
    }
}

internal fun Location.toFix() = GeoFix(latitude, longitude, time.takeIf { it > 0 } ?: System.currentTimeMillis(), accuracy)
