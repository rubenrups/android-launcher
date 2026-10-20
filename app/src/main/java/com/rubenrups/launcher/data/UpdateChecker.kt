package com.rubenrups.launcher.data

import android.content.Context
import androidx.core.content.pm.PackageInfoCompat
import org.json.JSONArray
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** [assetUrl] is the GitHub API URL of the APK file (works for private repositories with a token). */
data class UpdateInfo(
    val build: Int,
    val pageUrl: String,
    val assetUrl: String?,
    val size: Long,
    /** What changed, from the release text (the commit message of that build); blank when there is none. */
    val notes: String = "",
)

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
            val apk = release.optJSONArray("assets")?.let { assets ->
                (0 until assets.length()).map { assets.getJSONObject(it) }.firstOrNull { it.optString("name").endsWith(".apk") }
            }
            Result.Available(
                installed,
                UpdateInfo(
                    build,
                    release.getString("html_url"),
                    apk?.optString("url")?.ifBlank { null },
                    apk?.optLong("size") ?: 0L,
                    cleanNotes(release.optString("body")),
                ),
            )
        } else {
            Result.UpToDate(installed)
        }
    }

    /**
     * Downloads the release APK to [target]. GitHub answers the API URL with a redirect to a signed storage
     * URL; the token must not travel there (storage rejects it), so redirects are followed by hand.
     * Blocking; throws on any failure.
     */
    fun download(info: UpdateInfo, token: String, target: File, onProgress: (Int) -> Unit) {
        var url = info.assetUrl ?: throw IOException("This release has no APK file.")
        target.parentFile?.mkdirs()
        repeat(MAX_REDIRECTS) {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = false
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = DOWNLOAD_TIMEOUT_MS
            connection.setRequestProperty("Accept", "application/octet-stream")
            connection.setRequestProperty("User-Agent", "RupsLauncher")
            if (token.isNotBlank() && URL(url).host == "api.github.com") {
                connection.setRequestProperty("Authorization", "Bearer ${token.trim()}")
            }
            try {
                when (val code = connection.responseCode) {
                    200 -> {
                        val total = connection.contentLengthLong.takeIf { it > 0 } ?: info.size
                        var done = 0L
                        var lastPercent = -1
                        connection.inputStream.use { input ->
                            target.outputStream().use { out ->
                                val buffer = ByteArray(64 * 1024)
                                while (true) {
                                    val n = input.read(buffer)
                                    if (n < 0) break
                                    out.write(buffer, 0, n)
                                    done += n
                                    val percent = if (total > 0) (done * 100 / total).toInt().coerceIn(0, 100) else 0
                                    if (percent != lastPercent) {
                                        lastPercent = percent
                                        onProgress(percent)
                                    }
                                }
                            }
                        }
                        if (total > 0 && done != total) throw IOException("incomplete download")
                        return
                    }
                    301, 302, 303, 307, 308 -> {
                        val next = URL(URL(url), connection.getHeaderField("Location") ?: throw IOException("bad redirect"))
                        // Never let a redirect move the APK onto an unencrypted connection.
                        if (next.protocol != "https") throw IOException("redirect to a non-HTTPS address")
                        url = next.toString()
                    }
                    404 -> throw IOException(if (token.isBlank()) "not found (private repository? add a token)" else "not found")
                    401, 403 -> throw IOException("GitHub refused the token (HTTP $code)")
                    else -> throw IOException("HTTP $code")
                }
            } finally {
                connection.disconnect()
            }
        }
        throw IOException("too many redirects")
    }

    /** Drops the attribution trailers a commit message ends with, keeping what a person wants to read. */
    private fun cleanNotes(body: String): String =
        body.lines()
            .filterNot { it.startsWith("Co-Authored-By:", ignoreCase = true) || it.startsWith("Claude-Session:", ignoreCase = true) }
            .joinToString("\n")
            .trim()

    private const val MAX_REDIRECTS = 5
    private const val DOWNLOAD_TIMEOUT_MS = 30_000
    private const val REPO = "rubenrups/android-launcher"
    private const val TIMEOUT_MS = 8_000
}
