package com.rubenrups.launcher.data

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Uri
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class UpToDate(val installed: Int) : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data class Downloading(val info: UpdateInfo, val percent: Int) : UpdateState
    /** Downloaded and waiting for the user to press Install. */
    data class Ready(val info: UpdateInfo) : UpdateState
    data class Installing(val info: UpdateInfo) : UpdateState
    data class Failed(val reason: String, val info: UpdateInfo?) : UpdateState
}

/**
 * Checks for a newer build, downloads it (on unmetered networks, if allowed) and installs it through
 * the system package installer. Process-wide because the installer reports back to a broadcast receiver.
 * Android always asks you to confirm an install; this just removes everything else you would do by hand.
 */
object UpdateManager {
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private val mutex = Mutex()
    private var lastCheck = 0L

    /** [force] = the user asked: ignore the throttle and report errors. Background checks stay quiet. */
    suspend fun check(context: Context, token: String, force: Boolean, autoDownload: Boolean) = mutex.withLock {
        val now = System.currentTimeMillis()
        val busy = _state.value is UpdateState.Downloading || _state.value is UpdateState.Installing
        if (busy || (!force && now - lastCheck < CHECK_INTERVAL_MS)) return@withLock
        lastCheck = now
        val before = _state.value
        if (force) _state.value = UpdateState.Checking

        when (val result = withContext(Dispatchers.IO) { UpdateChecker.check(context, token) }) {
            is UpdateChecker.Result.UpToDate -> _state.value = UpdateState.UpToDate(result.installed)
            is UpdateChecker.Result.Failed -> _state.value = if (force) UpdateState.Failed(result.reason, null) else before
            is UpdateChecker.Result.Available -> {
                val info = result.update
                _state.value = if (updateFile(context, info.build).length() > 0) UpdateState.Ready(info) else UpdateState.Available(info)
                if (autoDownload && _state.value is UpdateState.Available && info.assetUrl != null && !isMetered(context)) {
                    download(context, token, info)
                }
            }
        }
    }

    /** Downloads if needed, then hands the APK to the installer. */
    suspend fun installNow(context: Context, token: String) = mutex.withLock {
        val info = when (val s = _state.value) {
            is UpdateState.Available -> s.info
            is UpdateState.Ready -> s.info
            is UpdateState.Failed -> s.info ?: return@withLock
            else -> return@withLock
        }
        if (!context.packageManager.canRequestPackageInstalls()) {
            // Sideloaded installs need this one-time permission for the app doing the installing.
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
            _state.value = UpdateState.Failed("Allow \"Install unknown apps\" for Rups Launcher, then tap Install again.", info)
            return@withLock
        }
        val file = updateFile(context, info.build)
        if (file.length() == 0L && !download(context, token, info)) return@withLock
        _state.value = UpdateState.Installing(info)
        runCatching { withContext(Dispatchers.IO) { ApkInstaller.install(context, file) } }
            .onFailure { _state.value = UpdateState.Failed("Could not start the install.", info) }
    }

    internal fun onInstallFailed(reason: String) {
        val info = (_state.value as? UpdateState.Installing)?.info
        _state.value = UpdateState.Failed(reason, info)
    }

    /** Returns true when the file is complete on disk. The mutex is already held by the caller. */
    private suspend fun download(context: Context, token: String, info: UpdateInfo): Boolean {
        _state.value = UpdateState.Downloading(info, 0)
        val file = updateFile(context, info.build)
        return try {
            withContext(Dispatchers.IO) {
                file.parentFile?.listFiles()?.filter { it != file }?.forEach { it.delete() }
                UpdateChecker.download(info, token, file) { percent ->
                    if (_state.value is UpdateState.Downloading) _state.value = UpdateState.Downloading(info, percent)
                }
            }
            _state.value = UpdateState.Ready(info)
            true
        } catch (e: Exception) {
            file.delete()
            _state.value = UpdateState.Failed("Download failed: ${e.message ?: "network error"}", info)
            false
        }
    }

    private fun updateFile(context: Context, build: Int) = File(File(context.cacheDir, "updates"), "build-$build.apk")

    private fun isMetered(context: Context): Boolean =
        runCatching { context.getSystemService(ConnectivityManager::class.java).isActiveNetworkMetered }.getOrDefault(true)

    private const val CHECK_INTERVAL_MS = 3 * 60 * 60 * 1000L
}
