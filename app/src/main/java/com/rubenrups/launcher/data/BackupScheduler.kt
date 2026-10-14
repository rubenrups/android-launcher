package com.rubenrups.launcher.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Writes the launcher's backup file into a folder the user picked (through Android's folder picker, so no storage
 * permission is needed) and keeps only the newest few. Blocking: call from a background dispatcher.
 */
object BackupScheduler {
    private const val PREFIX = "rups-launcher-backup-"
    private const val KEEP = 5

    fun isDue(settings: LauncherSettings, now: Long = System.currentTimeMillis()): Boolean =
        settings.autoBackup && settings.autoBackupFolder.isNotBlank() &&
            now - settings.lastBackupMs >= settings.autoBackupDays * DAY_MS

    /** Returns null on success, or a short reason it failed. */
    fun write(context: Context, prefs: LauncherPrefs): String? {
        val folder = prefs.settings.value.autoBackupFolder
        if (folder.isBlank()) return "No backup folder chosen."
        return try {
            val tree = Uri.parse(folder)
            val treeId = DocumentsContract.getTreeDocumentId(tree)
            val parent = DocumentsContract.buildDocumentUriUsingTree(tree, treeId)
            val resolver = context.contentResolver
            val name = PREFIX + SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date()) + ".json"
            val file = DocumentsContract.createDocument(resolver, parent, "application/json", name)
                ?: return "Could not create a file in the folder."
            resolver.openOutputStream(file)?.use { it.write(prefs.exportJson().toByteArray()) }
                ?: return "Could not write the backup file."
            prune(context, tree, treeId)
            prefs.update { it.copy(lastBackupMs = System.currentTimeMillis()) }
            null
        } catch (e: Exception) {
            "Backup failed: ${e.message ?: e.javaClass.simpleName}"
        }
    }

    /** Deletes our oldest backups beyond [KEEP]; files with other names are never touched. */
    private fun prune(context: Context, tree: Uri, treeId: String) {
        val resolver = context.contentResolver
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, treeId)
        val ours = ArrayList<Pair<String, String>>() // display name to document id
        resolver.query(
            children,
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null, null, null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val name = cursor.getString(1).orEmpty()
                if (name.startsWith(PREFIX) && name.endsWith(".json")) ours.add(name to cursor.getString(0))
            }
        }
        // The date is in the name, so the newest sort last.
        ours.sortedBy { it.first }.dropLast(KEEP).forEach { (_, id) ->
            runCatching { DocumentsContract.deleteDocument(resolver, DocumentsContract.buildDocumentUriUsingTree(tree, id)) }
        }
    }

    private const val DAY_MS = 24 * 60 * 60 * 1000L
}
