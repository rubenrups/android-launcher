package com.rubenrups.launcher.data

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherActivityInfo
import android.app.SearchManager
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.Bitmap
import android.net.Uri
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.provider.Settings
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToInt

/** Source of truth for installed, launchable apps. Keeps itself fresh via [LauncherApps.Callback]. */
class AppRepository(private val context: Context) {

    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val userManager = context.getSystemService(UserManager::class.java)
    private val activityInfos = HashMap<String, LauncherActivityInfo>()
    private val iconCache = LruCache<String, ImageBitmap>(1024)

    /** Icons are rendered once at this size (48dp) and scaled down by the UI, so every screen can share the cache. */
    private val iconPx = (48 * context.resources.displayMetrics.density).roundToInt()

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
        val me = Process.myUserHandle()
        // Every profile Android exposes to launchers: personal, plus work (and other) profiles.
        val entries = userManager.userProfiles.flatMap { user ->
            val main = user == me
            launcherApps.getActivityList(null, user)
                .filter { !main || it.componentName.packageName != context.packageName }
                .map { info ->
                    val serial = if (main) 0L else userManager.getSerialNumberForUser(user)
                    info to AppInfo(info.label.toString(), info.componentName, user, main, serial)
                }
        }
        synchronized(activityInfos) {
            activityInfos.clear()
            entries.forEach { (info, app) -> activityInfos[app.key] = info }
        }
        invalidateIcons()
        _apps.value = entries.map { it.second }
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
        // ACTION_DELETE would target the personal copy; uninstalling inside another profile needs that profile.
        if (!app.isMainProfile) return
        context.startActivity(
            Intent(Intent.ACTION_DELETE, Uri.fromParts("package", app.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    /** The app's own quick actions ("New message", "Scan"…). Empty unless we are the default home app. */
    fun shortcutsFor(app: AppInfo): List<AppShortcut> {
        if (!launcherApps.hasShortcutHostPermission()) return emptyList()
        val query = LauncherApps.ShortcutQuery()
            .setPackage(app.packageName)
            .setQueryFlags(
                LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST
            )
        val found: List<ShortcutInfo> = runCatching { launcherApps.getShortcuts(query, app.user) }.getOrNull().orEmpty()
        return found
            .filter { it.isEnabled && (it.activity == null || it.activity == app.component) }
            .sortedBy { it.rank }
            .take(MAX_SHORTCUTS)
            .map { AppShortcut(it.shortLabel?.toString() ?: it.id, it) }
    }

    fun launchShortcut(shortcut: AppShortcut) {
        runCatching { launcherApps.startShortcut(shortcut.info, null, null) }
    }

    fun webSearch(query: String, engine: WebSearchEngine) {
        val template = engine.urlTemplate
        val intent = if (template != null) {
            Intent(Intent.ACTION_VIEW, Uri.parse(template.format(Uri.encode(query))))
        } else {
            Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, query)
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }.onFailure {
            // No app handles the system search intent; fall back to a plain web page.
            runCatching {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(query)))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
    }

    /** Blocking; call from a background dispatcher. */
    fun loadIcon(app: AppInfo): ImageBitmap? {
        iconCache.get(app.key)?.let { return it }
        val info = synchronized(activityInfos) { activityInfos[app.key] } ?: return null
        val themed = iconPack?.let { iconPacks.iconFor(it, app.component) }
        val raw = themed ?: info.getIcon(0)
        // Work-profile apps get the briefcase badge so they are told apart from their personal twins.
        val drawable = if (app.isMainProfile) raw else context.packageManager.getUserBadgedIcon(raw, app.user)
        val bitmap: Bitmap = drawable.toBitmap(iconPx, iconPx)
        return bitmap.asImageBitmap().also { iconCache.put(app.key, it) }
    }

    private companion object {
        const val MAX_SHORTCUTS = 4
    }
}
