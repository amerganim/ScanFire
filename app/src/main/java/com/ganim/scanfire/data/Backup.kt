package com.ganim.scanfire.data

import android.content.Context
import android.net.Uri
import com.ganim.scanfire.data.db.ScanFireDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Backup is one ZIP holding the database and every page image. It goes wherever the user saves it
 * (their own Drive, a USB stick...), so ScanFire needs no cloud of its own.
 *
 * Restore can't replace the database under an open Room instance, so it unpacks into a staging
 * folder and the swap happens at the next process start, before Room opens ([applyPendingRestore]).
 */
class Backup(private val context: Context, private val db: ScanFireDatabase) {
    private val staging get() = File(context.filesDir, "restore-staging")

    suspend fun create(target: Uri) = withContext(Dispatchers.IO) {
        // Flush the write-ahead log so the single .db file is complete.
        db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { it.moveToFirst() }
        val dbFile = context.getDatabasePath(ScanFireDatabase.NAME)
        val docs = File(context.filesDir, "docs")
        context.contentResolver.openOutputStream(target, "w")?.use { raw ->
            ZipOutputStream(raw.buffered()).use { zip ->
                zip.putNextEntry(ZipEntry(MARKER)); zip.write("ScanFire backup v1".toByteArray()); zip.closeEntry()
                zip.putNextEntry(ZipEntry("db/${ScanFireDatabase.NAME}")); dbFile.inputStream().use { it.copyTo(zip) }; zip.closeEntry()
                docs.walkTopDown().filter { it.isFile && !it.name.endsWith(".part") }.forEach { file ->
                    zip.putNextEntry(ZipEntry("docs/" + file.relativeTo(docs).invariantSeparatorsPath))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        } ?: error("Couldn't open the destination")
    }

    /** Unpacks and validates a backup into staging. Returns false if it isn't a ScanFire backup. */
    suspend fun stage(source: Uri): Boolean = withContext(Dispatchers.IO) {
        staging.deleteRecursively()
        staging.mkdirs()
        var marker = false
        var hasDb = false
        val root = staging.canonicalFile
        context.contentResolver.openInputStream(source)?.use { raw ->
            ZipInputStream(raw.buffered()).use { zip ->
                generateSequence { zip.nextEntry }.forEach { entry ->
                    val out = File(staging, entry.name).canonicalFile
                    // Zip-slip guard: never write outside the staging folder.
                    if (!out.path.startsWith(root.path + File.separator)) return@forEach
                    if (entry.isDirectory) { out.mkdirs(); return@forEach }
                    out.parentFile?.mkdirs()
                    out.outputStream().use { zip.copyTo(it) }
                    if (entry.name == MARKER) marker = true
                    if (entry.name == "db/${ScanFireDatabase.NAME}") hasDb = true
                }
            }
        }
        (marker && hasDb).also { if (!it) staging.deleteRecursively() }
    }

    companion object {
        private const val MARKER = "scanfire-backup.txt"

        /** Called from Application.onCreate, before anything opens the database. */
        fun applyPendingRestore(context: Context) {
            val staging = File(context.filesDir, "restore-staging")
            val stagedDb = File(staging, "db/${ScanFireDatabase.NAME}")
            if (!stagedDb.exists()) return
            val dbFile = context.getDatabasePath(ScanFireDatabase.NAME)
            listOf("", "-wal", "-shm").forEach { File(dbFile.path + it).delete() }
            dbFile.parentFile?.mkdirs()
            stagedDb.copyTo(dbFile, overwrite = true)
            val docs = File(context.filesDir, "docs")
            docs.deleteRecursively()
            File(staging, "docs").takeIf { it.exists() }?.copyRecursively(docs, overwrite = true)
            staging.deleteRecursively()
        }
    }
}
