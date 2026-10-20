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
          (SELECT COUNT(*) FROM documents d WHERE d.folderId = f.id) +
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
        WHERE (:folderId IS NULL AND d.folderId IS NULL) OR d.folderId = :folderId
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
        WHERE d.name LIKE '%' || :query || '%'
           OR EXISTS (SELECT 1 FROM pages p WHERE p.documentId = d.id AND p.ocrText LIKE '%' || :query || '%')
        """,
    )
    fun search(query: String): Flow<List<DocumentSummary>>

    @Query(
        """
        SELECT d.id, d.name, d.folderId, d.createdAt, d.updatedAt,
          (SELECT COUNT(*) FROM pages p WHERE p.documentId = d.id) AS pageCount,
          (SELECT p.imageFile FROM pages p WHERE p.documentId = d.id ORDER BY p.position LIMIT 1) AS coverFile,
          (SELECT p.updatedAt FROM pages p WHERE p.documentId = d.id ORDER BY p.position LIMIT 1) AS coverUpdatedAt
        FROM documents d ORDER BY d.updatedAt DESC
        """,
    )
    fun observeAll(): Flow<List<DocumentSummary>>
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
