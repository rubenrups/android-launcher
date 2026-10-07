package com.rubenrups.launcher.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherActivityInfo
import android.app.SearchManager
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.provider.Settings
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.math.roundToInt

/** Source of truth for installed, launchable apps. Keeps itself fresh via [LauncherApps.Callback]. */
class AppRepository(private val context: Context) {

    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val userManager = context.getSystemService(UserManager::class.java)
    private val activityInfos = HashMap<String, LauncherActivityInfo>()
    private val iconCache = LruCache<String, ImageBitmap>(1024)

    // Everything that talks to the system runs here, never on the main thread.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var refreshJob: Job? = null

    // Survives process death, so coming back to a killed launcher doesn't start from zero.
    private val cachePrefs = context.getSharedPreferences("app_cache", Context.MODE_PRIVATE)
    private val iconDir = File(context.cacheDir, "icons").apply { mkdirs() }

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
        override fun onPackageAdded(packageName: String, user: UserHandle) = packagesChanged(arrayOf(packageName))
        override fun onPackageChanged(packageName: String, user: UserHandle) = packagesChanged(arrayOf(packageName))
        override fun onPackageRemoved(packageName: String, user: UserHandle) = packagesChanged(arrayOf(packageName))
        override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) =
            packagesChanged(packageNames)
        override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) =
            packagesChanged(packageNames)
    }

    init {
        // Draw from the last known list straight away, then ask the system what is really installed.
        _apps.value = loadCachedApps()
        scope.launch {
            refreshNow()
            // Apps shown from the cache had no system icon info yet; make their icons retry.
            _iconVersion.update { it + 1 }
        }
        launcherApps.registerCallback(callback)
    }

    /** Only the touched apps lose their icons; everything else stays cached. Callbacks arrive on the main thread. */
    private fun packagesChanged(packages: Array<out String>) {
        scope.launch {
            val evicted = packages.map { evictIcons(it) }.any { it }
            if (evicted) _iconVersion.update { it + 1 }
            scheduleRefresh()
        }
    }

    /** Package events come in bursts (a Play Store update run, a work profile resuming): collapse them into one query. */
    private fun scheduleRefresh() {
        refreshJob?.cancel()
        refreshJob = scope.launch {
            delay(REFRESH_DEBOUNCE_MS)
            refreshNow()
        }
    }

    @Synchronized
    private fun refreshNow() {
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
        val sorted = entries.map { it.second }.sortedWith(compareBy({ it.letter }, { it.label.lowercase() }))
        _apps.value = sorted
        saveCachedApps(sorted)
    }

    private fun saveCachedApps(apps: List<AppInfo>) {
        val array = JSONArray()
        apps.forEach { app ->
            array.put(
                JSONObject()
                    .put("l", app.label)
                    .put("p", app.component.packageName)
                    .put("c", app.component.className)
                    .put("m", app.isMainProfile)
                    .put("s", app.profileId)
            )
        }
        cachePrefs.edit().putString(KEY_APPS, array.toString()).apply()
    }

    private fun loadCachedApps(): List<AppInfo> {
        val json = cachePrefs.getString(KEY_APPS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(json)
            (0 until array.length()).mapNotNull { i ->
                val o = array.getJSONObject(i)
                val main = o.getBoolean("m")
                val serial = o.getLong("s")
                val user = if (main) me() else userManager.getUserForSerialNumber(serial) ?: return@mapNotNull null
                AppInfo(o.getString("l"), ComponentName(o.getString("p"), o.getString("c")), user, main, serial)
            }
        }.getOrDefault(emptyList())
    }

    private fun me(): UserHandle = Process.myUserHandle()

    fun setIconPack(packageName: String?) {
        if (packageName == iconPack) return
        iconPack = packageName
        invalidateIcons()
    }

    /** A different icon pack changes every icon, so this one really does start over. */
    private fun invalidateIcons() {
        iconCache.evictAll()
        _iconVersion.update { it + 1 }
        scope.launch {
            val suffix = "__${packTag()}.png"
            iconDir.listFiles()?.filter { !it.name.endsWith(suffix) }?.forEach { it.delete() }
        }
    }

    /** Drops one package's icons from memory and disk. Returns true if anything was cached for it. */
    private fun evictIcons(packageName: String): Boolean {
        var evicted = false
        iconCache.snapshot().keys.filter { it.startsWith("$packageName/") }.forEach {
            iconCache.remove(it)
            evicted = true
        }
        val prefix = "${sanitize(packageName)}__"
        iconDir.listFiles()?.filter { it.name.startsWith(prefix) }?.forEach {
            it.delete()
            evicted = true
        }
        return evicted
    }

    private fun packTag() = sanitize(iconPack ?: "default")

    private fun sanitize(text: String) = text.replace(Regex("[^A-Za-z0-9]"), "_")

    private fun iconFile(app: AppInfo) =
        File(iconDir, "${sanitize(app.packageName)}__${sanitize(app.key)}__${packTag()}.png")

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

    /** Blocking; call from a background dispatcher. Memory first, then disk, then ask the system. */
    fun loadIcon(app: AppInfo): ImageBitmap? {
        iconCache.get(app.key)?.let { return it }

        val file = iconFile(app)
        if (file.exists()) {
            BitmapFactory.decodeFile(file.path)?.let { decoded ->
                return decoded.asImageBitmap().also { iconCache.put(app.key, it) }
            }
        }

        val info = synchronized(activityInfos) { activityInfos[app.key] } ?: return null
        val themed = iconPack?.let { iconPacks.iconFor(it, app.component) }
        val raw = themed ?: info.getIcon(0)
        // Work-profile apps get the briefcase badge so they are told apart from their personal twins.
        val drawable = if (app.isMainProfile) raw else context.packageManager.getUserBadgedIcon(raw, app.user)
        val bitmap: Bitmap = drawable.toBitmap(iconPx, iconPx)
        writeIconFile(file, bitmap)
        return bitmap.asImageBitmap().also { iconCache.put(app.key, it) }
    }

    /** Written under a unique temp name, then renamed, so a reader never sees a half-written icon. */
    private fun writeIconFile(file: File, bitmap: Bitmap) {
        runCatching {
            val temp = File(iconDir, "${file.name}.${Thread.currentThread().id}.tmp")
            temp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            if (!temp.renameTo(file)) temp.delete()
        }
    }

    private companion object {
        const val MAX_SHORTCUTS = 4
        const val REFRESH_DEBOUNCE_MS = 300L
        const val KEY_APPS = "apps"
    }
}
