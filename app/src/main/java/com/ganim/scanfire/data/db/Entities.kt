package com.ganim.scanfire.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "folders",
    foreignKeys = [ForeignKey(FolderEntity::class, ["id"], ["parentId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("parentId")],
)
data class FolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val parentId: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "documents",
    foreignKeys = [ForeignKey(FolderEntity::class, ["id"], ["folderId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("folderId")],
)
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val folderId: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    /** Set when moved to the recycle bin; purged 30 days later. */
    val deletedAt: Long? = null,
)

/**
 * One page. [imageFile] is the processed image shown and exported; [originalFile] is the untouched
 * capture, so rotation, crop and filter can always be re-applied from scratch.
 * Crop is stored normalised (0..1) against the rotated original.
 */
@Entity(
    tableName = "pages",
    foreignKeys = [ForeignKey(DocumentEntity::class, ["id"], ["documentId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("documentId")],
)
data class PageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val documentId: Long,
    val position: Int,
    val imageFile: String,
    val originalFile: String,
    val rotation: Int = 0,
    val filter: String = "original",
    val cropLeft: Float = 0f,
    val cropTop: Float = 0f,
    val cropRight: Float = 1f,
    val cropBottom: Float = 1f,
    val ocrText: String? = null,
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "code_history")
data class CodeScanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val value: String,
    val format: String,
    val type: String,
    val createdAt: Long = System.currentTimeMillis(),
)

/** Row for the library list: the document plus what the list needs without loading every page. */
data class DocumentSummary(
    val id: Long,
    val name: String,
    val folderId: Long?,
    val createdAt: Long,
    val updatedAt: Long,
    val pageCount: Int,
    val coverFile: String?,
    val coverUpdatedAt: Long?,
    val deletedAt: Long? = null,
)

data class FolderSummary(
    val id: Long,
    val name: String,
    val parentId: Long?,
    val createdAt: Long,
    val itemCount: Int,
)
