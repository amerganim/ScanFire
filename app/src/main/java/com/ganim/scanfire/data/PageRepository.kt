package com.ganim.scanfire.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.net.Uri
import androidx.room.withTransaction
import com.ganim.scanfire.data.db.PageEntity
import com.ganim.scanfire.data.db.ScanFireDatabase
import com.ganim.scanfire.imaging.Bitmaps
import com.ganim.scanfire.imaging.PageRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/** Pages and their image files. Every write to disk happens before the row that points at it. */
class PageRepository(
    private val context: Context,
    private val db: ScanFireDatabase,
    private val files: FileStore,
) {
    fun pages(docId: Long): Flow<List<PageEntity>> = db.pages().observeForDocument(docId)

    fun page(id: Long): Flow<PageEntity?> = db.pages().observe(id)

    suspend fun pagesNow(docId: Long): List<PageEntity> = db.pages().forDocument(docId)

    fun imageFile(page: PageEntity) = files.file(page.documentId, page.imageFile)

    fun originalFile(page: PageEntity) = files.file(page.documentId, page.originalFile)

    /** Imports images (scanner output or gallery picks) as new pages at the end of the document. */
    suspend fun addImages(docId: Long, uris: List<Uri>, filter: String = "original"): Int = withContext(Dispatchers.IO) {
        val bitmaps = uris.asSequence().mapNotNull { uri -> runCatching { Bitmaps.decode(context.contentResolver, uri) }.getOrNull() }
        addBitmaps(docId, bitmaps, filter)
    }

    suspend fun addBitmaps(docId: Long, bitmaps: Sequence<Bitmap>, filter: String = "original"): Int = withContext(Dispatchers.IO) {
        var position = db.pages().maxPosition(docId) + 1
        val pages = mutableListOf<PageEntity>()
        for (bitmap in bitmaps) {
            val originalName = files.newImageName("_o")
            val imageName = files.newImageName()
            Bitmaps.save(bitmap, files.file(docId, originalName), quality = 95)
            Bitmaps.save(PageRenderer.render(bitmap, 0, FULL, filter), files.file(docId, imageName))
            pages += PageEntity(documentId = docId, position = position++, imageFile = imageName, originalFile = originalName, filter = filter)
        }
        if (pages.isNotEmpty()) {
            db.withTransaction {
                db.pages().insertAll(pages)
                db.documents().touch(docId)
            }
        }
        pages.size
    }

    /** Re-renders a page from its original with new settings. The new image gets a new file name so caches never show a stale copy. */
    suspend fun edit(pageId: Long, rotation: Int? = null, crop: RectF? = null, filter: String? = null) = withContext(Dispatchers.IO) {
        val page = db.pages().get(pageId) ?: return@withContext
        val newRotation = rotation ?: page.rotation
        val newCrop = crop ?: RectF(page.cropLeft, page.cropTop, page.cropRight, page.cropBottom)
        val newFilter = filter ?: page.filter
        val original = Bitmaps.decode(originalFile(page)) ?: return@withContext
        val name = files.newImageName()
        Bitmaps.save(PageRenderer.render(original, newRotation, newCrop, newFilter), files.file(page.documentId, name))
        db.pages().update(
            page.copy(
                imageFile = name,
                rotation = ((newRotation % 360) + 360) % 360,
                cropLeft = newCrop.left, cropTop = newCrop.top, cropRight = newCrop.right, cropBottom = newCrop.bottom,
                filter = newFilter,
                // A new crop or rotation can change what text is on the page; filters don't.
                ocrText = if (newRotation == page.rotation && newCrop == RectF(page.cropLeft, page.cropTop, page.cropRight, page.cropBottom)) page.ocrText else null,
                updatedAt = System.currentTimeMillis(),
            ),
        )
        db.documents().touch(page.documentId)
        files.deleteFiles(page.documentId, page.imageFile)
    }

    suspend fun reorder(docId: Long, orderedIds: List<Long>) {
        val byId = db.pages().forDocument(docId).associateBy { it.id }
        db.pages().reorder(orderedIds.mapNotNull { byId[it] })
        db.documents().touch(docId)
    }

    suspend fun delete(pageIds: Collection<Long>) = withContext(Dispatchers.IO) {
        val pages = pageIds.mapNotNull { db.pages().get(it) }
        if (pages.isEmpty()) return@withContext
        val docId = pages.first().documentId
        db.withTransaction {
            pages.forEach { db.pages().delete(it.id) }
            db.pages().reorder(db.pages().forDocument(docId))
            db.documents().touch(docId)
        }
        pages.forEach { files.deleteFiles(it.documentId, it.imageFile, it.originalFile) }
    }

    suspend fun setOcrText(pageId: Long, text: String?) {
        db.pages().get(pageId)?.let { db.pages().update(it.copy(ocrText = text)) }
    }

    companion object {
        val FULL = RectF(0f, 0f, 1f, 1f)
    }
}
