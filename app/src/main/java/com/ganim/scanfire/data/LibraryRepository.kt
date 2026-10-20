package com.ganim.scanfire.data

import androidx.room.withTransaction
import com.ganim.scanfire.data.db.DocumentEntity
import com.ganim.scanfire.data.db.DocumentSummary
import com.ganim.scanfire.data.db.FolderEntity
import com.ganim.scanfire.data.db.FolderSummary
import com.ganim.scanfire.data.db.ScanFireDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Folders and documents as a library. Page content is handled by [PageRepository]. */
class LibraryRepository(
    private val db: ScanFireDatabase,
    private val files: FileStore,
) {
    fun folders(parentId: Long?): Flow<List<FolderSummary>> = db.folders().observeChildren(parentId)

    fun allFolders(): Flow<List<FolderEntity>> = db.folders().observeAll()

    fun documents(folderId: Long?): Flow<List<DocumentSummary>> = db.documents().observeInFolder(folderId)

    fun allDocuments(): Flow<List<DocumentSummary>> = db.documents().observeAll()

    fun search(query: String): Flow<List<DocumentSummary>> = db.documents().search(query)

    fun document(id: Long): Flow<DocumentEntity?> = db.documents().observe(id)

    suspend fun folder(id: Long): FolderEntity? = db.folders().get(id)

    suspend fun createFolder(name: String, parentId: Long?): Long =
        db.folders().insert(FolderEntity(name = name.trim(), parentId = parentId))

    suspend fun renameFolder(id: Long, name: String) {
        db.folders().get(id)?.let { db.folders().update(it.copy(name = name.trim())) }
    }

    suspend fun moveFolder(id: Long, parentId: Long?) {
        if (parentId != null && parentId in db.folders().subtreeIds(id)) return // no cycles
        db.folders().get(id)?.let { db.folders().update(it.copy(parentId = parentId)) }
    }

    /** Deletes the folder, everything inside it, and the page files on disk. */
    suspend fun deleteFolder(id: Long) = withContext(Dispatchers.IO) {
        val folder = db.folders().get(id) ?: return@withContext
        val docIds = db.documents().idsInFolders(db.folders().subtreeIds(id))
        db.folders().delete(folder)
        docIds.forEach(files::deleteDocument)
    }

    suspend fun createDocument(name: String? = null, folderId: Long? = null): Long =
        db.documents().insert(DocumentEntity(name = name?.trim()?.ifEmpty { null } ?: defaultName(), folderId = folderId))

    suspend fun renameDocument(id: Long, name: String) {
        db.documents().get(id)?.let { db.documents().update(it.copy(name = name.trim(), updatedAt = System.currentTimeMillis())) }
    }

    suspend fun moveDocument(id: Long, folderId: Long?) {
        db.documents().get(id)?.let { db.documents().update(it.copy(folderId = folderId)) }
    }

    suspend fun deleteDocument(id: Long) = withContext(Dispatchers.IO) {
        db.documents().delete(id)
        files.deleteDocument(id)
    }

    suspend fun deleteDocuments(ids: Collection<Long>) = withContext(Dispatchers.IO) {
        db.withTransaction { ids.forEach { db.documents().delete(it) } }
        ids.forEach(files::deleteDocument)
    }

    companion object {
        fun defaultName(time: Long = System.currentTimeMillis()): String =
            "Scan " + SimpleDateFormat("yyyy-MM-dd HH.mm", Locale.getDefault()).format(Date(time))
    }
}
