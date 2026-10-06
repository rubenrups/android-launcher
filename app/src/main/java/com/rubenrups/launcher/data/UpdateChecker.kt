package com.rubenrups.launcher.data

import android.content.Context
import androidx.core.content.pm.PackageInfoCompat
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(val build: Int, val pageUrl: String)

/**
 * Looks at the newest GitHub release. CI publishes each build as a release tagged "build-N", where N
 * equals the app's versionCode, so "is there an update" is just "is N bigger than mine".
 */
object UpdateChecker {

    sealed interface Result {
        data class UpToDate(val installed: Int) : Result
        data class Available(val installed: Int, val update: UpdateInfo) : Result
        data class Failed(val reason: String) : Result
    }

    fun installedBuild(context: Context): Int =
        PackageInfoCompat.getLongVersionCode(context.packageManager.getPackageInfo(context.packageName, 0)).toInt()

    /** Blocking; call from a background dispatcher. */
    fun check(context: Context, token: String): Result {
        val installed = installedBuild(context)
        return try {
            val connection = URL("https://api.github.com/repos/$REPO/releases?per_page=1").openConnection() as HttpURLConnection
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("User-Agent", "RupsLauncher")
            if (token.isNotBlank()) connection.setRequestProperty("Authorization", "Bearer ${token.trim()}")
            try {
                when (val code = connection.responseCode) {
                    200 -> parse(installed, connection.inputStream.bufferedReader().use { it.readText() })
                    404 -> Result.Failed(
                        if (token.isBlank()) "Not found. The repository is private: add a read-only token below."
                        else "Not found. Check that the token can read this repository."
                    )
                    401, 403 -> Result.Failed("GitHub refused the token (HTTP $code).")
                    else -> Result.Failed("GitHub answered HTTP $code.")
                }
            } finally {
                connection.disconnect()
            }
        } catch (e: Exception) {
            Result.Failed("Could not reach GitHub.")
        }
    }

    private fun parse(installed: Int, body: String): Result {
        val release = JSONArray(body).optJSONObject(0) ?: return Result.Failed("No releases published yet.")
        val build = release.optString("tag_name").removePrefix("build-").toIntOrNull()
            ?: return Result.Failed("Latest release has an unexpected tag.")
        return if (build > installed) {
            Result.Available(installed, UpdateInfo(build, release.getString("html_url")))
        } else {
            Result.UpToDate(installed)
        }
    }

    private const val REPO = "rubenrups/android-launcher"
    private const val TIMEOUT_MS = 8_000
}
