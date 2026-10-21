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

    /** Deletes the folder and its subfolders; their documents go to the recycle bin (restorable to the top level). */
    suspend fun deleteFolder(id: Long) = withContext(Dispatchers.IO) {
        val folder = db.folders().get(id) ?: return@withContext
        db.withTransaction {
            val docIds = db.documents().idsInFolders(db.folders().subtreeIds(id))
            if (docIds.isNotEmpty()) db.documents().trashOutOfFolder(docIds)
            db.folders().delete(folder)
        }
    }

    suspend fun createDocument(name: String? = null, folderId: Long? = null): Long =
        db.documents().insert(DocumentEntity(name = name?.trim()?.ifEmpty { null } ?: defaultName(), folderId = folderId))

    suspend fun renameDocument(id: Long, name: String) {
        db.documents().get(id)?.let { db.documents().update(it.copy(name = name.trim(), updatedAt = System.currentTimeMillis())) }
    }

    suspend fun moveDocument(id: Long, folderId: Long?) {
        db.documents().get(id)?.let { db.documents().update(it.copy(folderId = folderId)) }
    }

    /** Moves to the recycle bin. */
    suspend fun deleteDocument(id: Long) = db.documents().softDelete(listOf(id))

    suspend fun deleteDocuments(ids: Collection<Long>) = db.documents().softDelete(ids)

    fun trash(): Flow<List<DocumentSummary>> = db.documents().observeTrash()

    suspend fun restore(ids: Collection<Long>) = db.documents().restore(ids)

    /** Gone for good: rows and page files. Used for failed imports, "Delete forever" and purging. */
    suspend fun deleteForever(ids: Collection<Long>) = withContext(Dispatchers.IO) {
        db.withTransaction { ids.forEach { db.documents().delete(it) } }
        ids.forEach(files::deleteDocument)
    }

    suspend fun emptyTrash() = deleteForever(db.documents().allTrashed())

    /** Removes documents that have been in the bin for more than [days]. */
    suspend fun purgeTrash(days: Int = TRASH_DAYS) =
        deleteForever(db.documents().trashedBefore(System.currentTimeMillis() - days * 24L * 60 * 60 * 1000))

    companion object {
        const val TRASH_DAYS = 30

        fun defaultName(time: Long = System.currentTimeMillis()): String =
            "Scan " + SimpleDateFormat("yyyy-MM-dd HH.mm", Locale.getDefault()).format(Date(time))
    }
}
