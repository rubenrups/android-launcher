package com.rubenrups.launcher.data

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A small on-device log: crashes and the result of gesture actions, kept in one capped text file so it can be
 * copied or shared from settings. It never records settings values, logins or notification content.
 */
object Diagnostics {
    private const val MAX_BYTES = 64 * 1024
    private const val KEEP_BYTES = 32 * 1024

    @Volatile
    private var file: File? = null
    private val stamp = SimpleDateFormat("MM-dd HH:mm:ss", Locale.US)

    /** Starts logging and makes uncaught crashes land in the log before the process dies. */
    fun install(context: Context) {
        file = File(context.applicationContext.filesDir, "diagnostics.log")
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { log("CRASH in ${thread.name}\n" + error.stackTraceToString().take(4000)) }
            previous?.uncaughtException(thread, error)
        }
    }

    @Synchronized
    fun log(message: String) {
        val target = file ?: return
        runCatching {
            if (target.length() > MAX_BYTES) {
                val tail = target.readText().takeLast(KEEP_BYTES)
                target.writeText(tail.substringAfter('\n', tail))
            }
            target.appendText("${stamp.format(Date())}  $message\n")
        }
    }

    @Synchronized
    fun read(): String = runCatching { file?.takeIf { it.exists() }?.readText().orEmpty() }.getOrDefault("")

    @Synchronized
    fun clear() {
        runCatching { file?.delete() }
    }
}
