package com.ganim.scanfire.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderDao {
    @Insert
    suspend fun insert(folder: FolderEntity): Long

    @Update
    suspend fun update(folder: FolderEntity)

    @Delete
    suspend fun delete(folder: FolderEntity)

    @Query("SELECT * FROM folders WHERE id = :id")
    suspend fun get(id: Long): FolderEntity?

    @Query("SELECT * FROM folders ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<FolderEntity>>

    @Query(
        """
        SELECT f.id, f.name, f.parentId, f.createdAt,
          (SELECT COUNT(*) FROM documents d WHERE d.folderId = f.id AND d.deletedAt IS NULL AND d.vault = 0) +
          (SELECT COUNT(*) FROM folders c WHERE c.parentId = f.id) AS itemCount
        FROM folders f
        WHERE (:parentId IS NULL AND f.parentId IS NULL) OR f.parentId = :parentId
        ORDER BY f.name COLLATE NOCASE
        """,
    )
    fun observeChildren(parentId: Long?): Flow<List<FolderSummary>>

    /** Every folder id below [rootId], inclusive, used to collect files before a cascade delete. */
    @Query(
        """
        WITH RECURSIVE tree(id) AS (
          SELECT :rootId UNION ALL SELECT f.id FROM folders f JOIN tree t ON f.parentId = t.id
        ) SELECT id FROM tree
        """,
    )
    suspend fun subtreeIds(rootId: Long): List<Long>
}

@Dao
interface DocumentDao {
    @Insert
    suspend fun insert(doc: DocumentEntity): Long

    @Update
    suspend fun update(doc: DocumentEntity)

    @Query("DELETE FROM documents WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM documents WHERE id = :id")
    suspend fun get(id: Long): DocumentEntity?

    @Query("SELECT * FROM documents WHERE id = :id")
    fun observe(id: Long): Flow<DocumentEntity?>

    @Query("SELECT id FROM documents WHERE folderId IN (:folderIds)")
    suspend fun idsInFolders(folderIds: List<Long>): List<Long>

    @Query("UPDATE documents SET updatedAt = :time WHERE id = :id")
    suspend fun touch(id: Long, time: Long = System.currentTimeMillis())

    @Query(
        """
        SELECT d.id, d.name, d.folderId, d.createdAt, d.updatedAt,
          (SELECT COUNT(*) FROM pages p WHERE p.documentId = d.id) AS pageCount,
          (SELECT p.imageFile FROM pages p WHERE p.documentId = d.id ORDER BY p.position LIMIT 1) AS coverFile,
          (SELECT p.updatedAt FROM pages p WHERE p.documentId = d.id ORDER BY p.position LIMIT 1) AS coverUpdatedAt
        FROM documents d
        WHERE ((:folderId IS NULL AND d.folderId IS NULL) OR d.folderId = :folderId) AND d.deletedAt IS NULL AND d.vault = 0
        """,
    )
    fun observeInFolder(folderId: Long?): Flow<List<DocumentSummary>>

    /** Searches names and recognised text across every folder. */
    @Query(
        """
        SELECT d.id, d.name, d.folderId, d.createdAt, d.updatedAt,
          (SELECT COUNT(*) FROM pages p WHERE p.documentId = d.id) AS pageCount,
          (SELECT p.imageFile FROM pages p WHERE p.documentId = d.id ORDER BY p.position LIMIT 1) AS coverFile,
          (SELECT p.updatedAt FROM pages p WHERE p.documentId = d.id ORDER BY p.position LIMIT 1) AS coverUpdatedAt
        FROM documents d
        WHERE d.deletedAt IS NULL AND d.vault = 0 AND (d.name LIKE '%' || :query || '%'
           OR EXISTS (SELECT 1 FROM pages p WHERE p.documentId = d.id AND p.ocrText LIKE '%' || :query || '%'))
        """,
    )
    fun search(query: String): Flow<List<DocumentSummary>>

    @Query(
        """
        SELECT d.id, d.name, d.folderId, d.createdAt, d.updatedAt,
          (SELECT COUNT(*) FROM pages p WHERE p.documentId = d.id) AS pageCount,
          (SELECT p.imageFile FROM pages p WHERE p.documentId = d.id ORDER BY p.position LIMIT 1) AS coverFile,
          (SELECT p.updatedAt FROM pages p WHERE p.documentId = d.id ORDER BY p.position LIMIT 1) AS coverUpdatedAt
        FROM documents d WHERE d.deletedAt IS NULL AND d.vault = 0 ORDER BY d.updatedAt DESC
        """,
    )
    fun observeAll(): Flow<List<DocumentSummary>>

    @Query(
        """
        SELECT d.id, d.name, d.folderId, d.createdAt, d.updatedAt,
          (SELECT COUNT(*) FROM pages p WHERE p.documentId = d.id) AS pageCount,
          (SELECT p.imageFile FROM pages p WHERE p.documentId = d.id ORDER BY p.position LIMIT 1) AS coverFile,
          (SELECT p.updatedAt FROM pages p WHERE p.documentId = d.id ORDER BY p.position LIMIT 1) AS coverUpdatedAt,
          d.deletedAt
        FROM documents d WHERE d.deletedAt IS NOT NULL ORDER BY d.deletedAt DESC
        """,
    )
    fun observeTrash(): Flow<List<DocumentSummary>>

    @Query("UPDATE documents SET deletedAt = :time WHERE id IN (:ids)")
    suspend fun softDelete(ids: Collection<Long>, time: Long = System.currentTimeMillis())

    /** Restored documents whose folder is gone land at the top level. */
    @Query("UPDATE documents SET deletedAt = NULL, folderId = CASE WHEN folderId IN (SELECT id FROM folders) THEN folderId ELSE NULL END WHERE id IN (:ids)")
    suspend fun restore(ids: Collection<Long>)

    @Query("SELECT id FROM documents WHERE deletedAt IS NOT NULL AND deletedAt < :before")
    suspend fun trashedBefore(before: Long): List<Long>

    @Query("SELECT id FROM documents WHERE deletedAt IS NOT NULL")
    suspend fun allTrashed(): List<Long>

    @Query("UPDATE documents SET folderId = NULL, deletedAt = :time WHERE id IN (:ids) AND vault = 0")
    suspend fun trashOutOfFolder(ids: Collection<Long>, time: Long = System.currentTimeMillis())

    @Query(
        """
        SELECT d.id, d.name, d.folderId, d.createdAt, d.updatedAt,
          (SELECT COUNT(*) FROM pages p WHERE p.documentId = d.id) AS pageCount,
          NULL AS coverFile, NULL AS coverUpdatedAt
        FROM documents d WHERE d.vault = 1 ORDER BY d.updatedAt DESC
        """,
    )
    fun observeVault(): Flow<List<DocumentSummary>>

    @Query("UPDATE documents SET vault = :vault, folderId = NULL, updatedAt = :time WHERE id = :id")
    suspend fun setVault(id: Long, vault: Boolean, time: Long = System.currentTimeMillis())

    @Query("SELECT * FROM documents WHERE remindAt IS NOT NULL AND deletedAt IS NULL ORDER BY remindAt")
    fun observeReminders(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE remindAt IS NOT NULL AND deletedAt IS NULL")
    suspend fun reminders(): List<DocumentEntity>

    @Query("UPDATE documents SET remindAt = :at, remindNote = :note WHERE id = :id")
    suspend fun setReminder(id: Long, at: Long?, note: String?)
}

@Dao
interface ExpenseDao {
    @Insert
    suspend fun insert(expense: ExpenseEntity): Long

    @Update
    suspend fun update(expense: ExpenseEntity)

    @Query("DELETE FROM expenses WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM expenses ORDER BY date DESC, id DESC")
    fun observeAll(): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE documentId = :docId LIMIT 1")
    suspend fun forDocument(docId: Long): ExpenseEntity?
}

@Dao
interface PageDao {
    @Insert
    suspend fun insert(page: PageEntity): Long

    @Insert
    suspend fun insertAll(pages: List<PageEntity>): List<Long>

    @Update
    suspend fun update(page: PageEntity)

    @Update
    suspend fun updateAll(pages: List<PageEntity>)

    @Query("DELETE FROM pages WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM pages WHERE id = :id")
    suspend fun get(id: Long): PageEntity?

    @Query("SELECT * FROM pages WHERE id = :id")
    fun observe(id: Long): Flow<PageEntity?>

    @Query("SELECT * FROM pages WHERE documentId = :docId ORDER BY position")
    fun observeForDocument(docId: Long): Flow<List<PageEntity>>

    @Query("SELECT * FROM pages WHERE documentId = :docId ORDER BY position")
    suspend fun forDocument(docId: Long): List<PageEntity>

    @Query("SELECT COALESCE(MAX(position), -1) FROM pages WHERE documentId = :docId")
    suspend fun maxPosition(docId: Long): Int

    @Transaction
    suspend fun reorder(pages: List<PageEntity>) {
        updateAll(pages.mapIndexed { i, p -> p.copy(position = i) })
    }
}

@Dao
interface CodeScanDao {
    @Insert
    suspend fun insert(scan: CodeScanEntity): Long

    @Query("DELETE FROM code_history WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM code_history")
    suspend fun clear()

    @Query("SELECT * FROM code_history ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<CodeScanEntity>>
}
