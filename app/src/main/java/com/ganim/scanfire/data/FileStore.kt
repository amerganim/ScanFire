package com.ganim.scanfire.data

import android.content.Context
import java.io.File
import java.util.UUID

/**
 * Page images live in app-private storage: `files/docs/<docId>/<name>.jpg`. Nothing is written to
 * shared storage unless the user exports it. Temporary export output goes to `cache/exports`.
 */
class FileStore(context: Context) {
    private val root = File(context.filesDir, "docs").apply { mkdirs() }
    val exportDir: File = File(context.cacheDir, "exports").apply { mkdirs() }
    val tempDir: File = File(context.cacheDir, "tmp").apply { mkdirs() }

    fun docDir(docId: Long): File = File(root, docId.toString()).apply { mkdirs() }

    fun file(docId: Long, name: String): File = File(docDir(docId), name)

    fun newImageName(suffix: String = ""): String = UUID.randomUUID().toString() + suffix + ".jpg"

    fun deleteDocument(docId: Long) {
        File(root, docId.toString()).deleteRecursively()
    }

    fun deleteFiles(docId: Long, vararg names: String) {
        names.forEach { File(docDir(docId), it).delete() }
    }

    /** Fresh, empty export folder so stale files are never shared by accident. */
    fun freshExportFile(fileName: String): File {
        exportDir.listFiles()?.forEach { if (System.currentTimeMillis() - it.lastModified() > 60 * 60 * 1000) it.deleteRecursively() }
        return File(exportDir, fileName).also { it.delete() }
    }

    fun newTempFile(ext: String): File = File(tempDir, UUID.randomUUID().toString() + "." + ext)

    val docsRoot: File get() = root
}
