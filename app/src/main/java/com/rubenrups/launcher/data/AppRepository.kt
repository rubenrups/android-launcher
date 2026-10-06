package com.rubenrups.launcher.data

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.net.Uri
import android.os.Process
import android.os.UserHandle
import android.provider.Settings
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Source of truth for installed, launchable apps. Keeps itself fresh via [LauncherApps.Callback]. */
class AppRepository(private val context: Context) {

    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val activityInfos = HashMap<String, LauncherActivityInfo>()
    private val iconCache = LruCache<String, ImageBitmap>(256)

    val iconPacks = IconPackManager(context)
    private var iconPack: String? = null

    /** Bumped whenever cached icons are invalidated, so the UI knows to reload them. */
    private val _iconVersion = MutableStateFlow(0)
    val iconVersion: StateFlow<Int> = _iconVersion.asStateFlow()

    private val _apps = MutableStateFlow<List<AppInfo>>(emptyList())
    val apps: StateFlow<List<AppInfo>> = _apps.asStateFlow()

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageAdded(packageName: String, user: UserHandle) = refresh()
        override fun onPackageChanged(packageName: String, user: UserHandle) = refresh()
        override fun onPackageRemoved(packageName: String, user: UserHandle) = refresh()
        override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = refresh()
        override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = refresh()
    }

    init {
        refresh()
        launcherApps.registerCallback(callback)
    }

    fun refresh() {
        val infos = launcherApps.getActivityList(null, Process.myUserHandle())
            .filter { it.componentName.packageName != context.packageName }
        synchronized(activityInfos) {
            activityInfos.clear()
            infos.forEach { activityInfos[it.componentName.flattenToString()] = it }
        }
        invalidateIcons()
        _apps.value = infos
            .map { AppInfo(it.label.toString(), it.componentName, it.user) }
            .sortedWith(compareBy({ it.letter }, { it.label.lowercase() }))
    }

    fun setIconPack(packageName: String?) {
        if (packageName == iconPack) return
        iconPack = packageName
        invalidateIcons()
    }

    private fun invalidateIcons() {
        iconCache.evictAll()
        _iconVersion.value += 1
    }

    fun launch(app: AppInfo) {
        runCatching { launcherApps.startMainActivity(app.component, app.user, null, null) }
    }

    fun openAppInfo(app: AppInfo) {
        runCatching { launcherApps.startAppDetailsActivity(app.component, app.user, null, null) }
            .onFailure {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", app.packageName, null))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
    }

    fun uninstall(app: AppInfo) {
        context.startActivity(
            Intent(Intent.ACTION_DELETE, Uri.fromParts("package", app.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    /** Blocking; call from a background dispatcher. */
    fun loadIcon(app: AppInfo, sizePx: Int): ImageBitmap? {
        iconCache.get(app.key)?.let { return it }
        val info = synchronized(activityInfos) { activityInfos[app.key] } ?: return null
        val themed = iconPack?.let { iconPacks.iconFor(it, app.component) }
        val bitmap: Bitmap = (themed ?: info.getIcon(0)).toBitmap(sizePx, sizePx)
        return bitmap.asImageBitmap().also { iconCache.put(app.key, it) }
    }
}
