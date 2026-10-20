package com.rubenrups.launcher.data

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import java.io.File

/** Hands a downloaded APK to Android's package installer and reports the outcome to [UpdateManager]. */
object ApkInstaller {
    const val ACTION_RESULT = "com.rubenrups.launcher.INSTALL_RESULT"

    /**
     * True when [apk] is this app's package, signed by the certificate(s) of the installed copy. A cheap early check:
     * it does not replace Android's own signature test at install time, and it is only as strong as the signing key.
     */
    @Suppress("DEPRECATION")
    fun isSignedLikeThisApp(context: Context, apk: File): Boolean = try {
        val pm = context.packageManager
        val flags = PackageManager.GET_SIGNING_CERTIFICATES
        val archive = pm.getPackageArchiveInfo(apk.path, flags)
        val installed = pm.getPackageInfo(context.packageName, flags)
        val installedCerts = installed.signingInfo?.apkContentsSigners?.map { it.toCharsString() }?.toSet().orEmpty()
        val offeredCerts = archive?.signingInfo?.apkContentsSigners?.map { it.toCharsString() }?.toSet().orEmpty()
        archive?.packageName == context.packageName && installedCerts.isNotEmpty() && installedCerts == offeredCerts
    } catch (_: Exception) {
        false
    }

    /** Blocking (copies the file); call from a background dispatcher. Android still asks the user to confirm. */
    fun install(context: Context, apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(context.packageName)
        // Android 12+ may skip the confirmation for updates of an app this app installed before.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            apk.inputStream().use { input ->
                session.openWrite("update", 0, apk.length()).use { out ->
                    input.copyTo(out)
                    session.fsync(out)
                }
            }
            // Explicit intent + mutable: the system adds the status extras to it.
            val callback = Intent(context, InstallResultReceiver::class.java).setAction(ACTION_RESULT)
            val pending = PendingIntent.getBroadcast(
                context, sessionId, callback, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            session.commit(pending.intentSender)
        }
    }
}

/** Receives the installer's progress: shows the system's confirm screen, or records why it failed. */
class InstallResultReceiver : BroadcastReceiver() {
    @Suppress("DEPRECATION")
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                runCatching { context.startActivity(confirm?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    .onFailure { UpdateManager.onInstallFailed("Could not open the install screen.") }
            }
            // The app restarts on success; nothing to do.
            PackageInstaller.STATUS_SUCCESS -> Unit
            PackageInstaller.STATUS_FAILURE_ABORTED -> UpdateManager.onInstallFailed("Install cancelled.")
            else -> UpdateManager.onInstallFailed(
                intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "The install failed.",
            )
        }
    }
}
