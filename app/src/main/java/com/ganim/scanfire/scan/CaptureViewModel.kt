package com.ganim.scanfire.scan

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ganim.scanfire.data.LibraryRepository
import com.ganim.scanfire.data.PageRepository
import com.ganim.scanfire.data.SettingsRepository
import com.ganim.scanfire.imaging.Bitmaps
import com.ganim.scanfire.imaging.PageComposer
import com.ganim.scanfire.imaging.PageDetector
import com.ganim.scanfire.imaging.Perspective
import com.ganim.scanfire.imaging.Quad
import com.ganim.scanfire.pdf.PdfTools
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Where captured images go: a new document in a folder, or the end of an existing document. */
sealed interface CaptureTarget {
    data class NewDocument(val folderId: Long?) : CaptureTarget
    data class AddTo(val docId: Long) : CaptureTarget
}

/** One imported photo under review: the page corners found automatically, and the ones in use. */
data class ImportItem(val uri: Uri, val detected: Quad?, val quad: Quad)

data class ImportReview(val target: CaptureTarget, val items: List<ImportItem>)

/**
 * Lives at the root so an import keeps running while screens change. Scanner pages go straight
 * into a document; photos from the gallery or another app first get their page found and
 * straightened ([prepareImport] → review → [finishImport]), so they come out like a scan.
 */
class CaptureViewModel(
    private val context: Context,
    private val library: LibraryRepository,
    private val pages: PageRepository,
    private val settings: SettingsRepository,
    private val pdfTools: PdfTools,
) : ViewModel() {
    /** Non-null while working; the text says what's happening. */
    private val _busy = MutableStateFlow<String?>(null)
    val busy: StateFlow<String?> = _busy

    /** One-off user messages, e.g. when none of the picked images could be read. */
    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    private val _review = MutableStateFlow<ImportReview?>(null)
    val review: StateFlow<ImportReview?> = _review

    /** Scanner output: already cropped and enhanced by the scanner, so stored as-is. */
    fun newDocument(folderId: Long?, uris: List<Uri>, fromScanner: Boolean, onDone: (Long) -> Unit) = run("Adding pages…") {
        val docId = library.createDocument(folderId = folderId)
        val added = pages.addImages(docId, uris, filterFor(fromScanner))
        if (added == 0) {
            library.deleteForever(listOf(docId))
            _messages.send(UNREADABLE)
        } else {
            if (added < uris.size) _messages.send("${uris.size - added} of ${uris.size} images couldn't be read")
            onDone(docId)
        }
    }

    fun addToDocument(docId: Long, uris: List<Uri>, fromScanner: Boolean) = run("Adding pages…") {
        if (pages.addImages(docId, uris, filterFor(fromScanner)) == 0) _messages.send(UNREADABLE)
    }

    /** Finds the page in each photo; [onReady] opens the review screen. */
    fun prepareImport(target: CaptureTarget, uris: List<Uri>, onReady: () -> Unit) = run("Finding pages…") {
        val items = withContext(Dispatchers.Default) {
            uris.mapNotNull { uri ->
                val small = runCatching { Bitmaps.decode(context.contentResolver, uri, maxEdge = 500) }.getOrNull() ?: return@mapNotNull null
                val px = IntArray(small.width * small.height).also { small.getPixels(it, 0, small.width, 0, 0, small.width, small.height) }
                val found = PageDetector.detect(px, small.width, small.height)
                small.recycle()
                ImportItem(uri, found, found ?: Quad.FULL)
            }
        }
        if (items.isEmpty()) {
            _messages.send(UNREADABLE)
            return@run
        }
        _review.value = ImportReview(target, items)
        onReady()
    }

    /** Preview for the review screen, loaded per page so a big import doesn't fill memory. */
    suspend fun preview(uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        runCatching { Bitmaps.decode(context.contentResolver, uri, maxEdge = 1400) }.getOrNull()
    }

    fun setQuad(index: Int, quad: Quad) = _review.update { r ->
        r?.copy(items = r.items.mapIndexed { i, item -> if (i == index) item.copy(quad = quad) else item })
    }

    fun setAllQuads(full: Boolean) = _review.update { r ->
        r?.copy(items = r.items.map { it.copy(quad = if (full) Quad.FULL else it.detected ?: Quad.FULL) })
    }

    fun cancelImport() {
        _review.value = null
    }

    /** Straightens every reviewed photo, applies the default filter, and stores the pages. */
    fun finishImport(onNewDocument: (Long) -> Unit) {
        val review = _review.value ?: return
        _review.value = null
        run("Scanning ${review.items.size} photo${if (review.items.size == 1) "" else "s"}…") {
            val docId = when (val t = review.target) {
                is CaptureTarget.NewDocument -> library.createDocument(folderId = t.folderId)
                is CaptureTarget.AddTo -> t.docId
            }
            val filter = settings.settings.first().defaultFilter
            val straightened = review.items.asSequence().mapNotNull { item ->
                val full = Bitmaps.decode(context.contentResolver, item.uri) ?: return@mapNotNull null
                if (item.quad.isFull) full else straighten(full, item.quad).also { full.recycle() }
            }
            val added = pages.addBitmaps(docId, straightened, filter)
            when {
                added == 0 && review.target is CaptureTarget.NewDocument -> { library.deleteForever(listOf(docId)); _messages.send(UNREADABLE) }
                added == 0 -> _messages.send(UNREADABLE)
                review.target is CaptureTarget.NewDocument -> onNewDocument(docId)
            }
        }
    }

    private fun straighten(src: Bitmap, quad: Quad): Bitmap {
        val w = src.width; val h = src.height
        val px = IntArray(w * h).also { src.getPixels(it, 0, w, 0, 0, w, h) }
        val corners = FloatArray(8) { i -> quad.points[i] * if (i % 2 == 0) w else h }
        val (ow, oh) = Perspective.outputSize(corners, Bitmaps.MAX_EDGE)
        val out = Perspective.warp(px, w, h, corners, ow, oh)
        return Bitmap.createBitmap(out, ow, oh, Bitmap.Config.ARGB_8888)
    }

    /** Turns a PDF into a ScanFire document, one page image per PDF page at ~200 dpi. */
    fun newDocumentFromPdf(folderId: Long?, pdf: File, name: String, password: String?, onDone: (Long) -> Unit, onError: (Exception) -> Unit) = run("Importing PDF…") {
        pdfToDocument(folderId, pdf, name, password, onDone, onError)
    }

    /** A PDF shared or "opened with" ScanFire from another app. */
    fun importPdf(uri: Uri, onDone: (Long) -> Unit, onError: (Exception) -> Unit) = run("Importing PDF…") {
        val file = try { pdfTools.copyIn(uri) } catch (e: Exception) { onError(e); return@run }
        try {
            pdfToDocument(null, file, pdfTools.displayName(uri), null, onDone, onError)
        } finally {
            file.delete()
        }
    }

    private suspend fun pdfToDocument(folderId: Long?, pdf: File, name: String, password: String?, onDone: (Long) -> Unit, onError: (Exception) -> Unit) {
        val docId = library.createDocument(name = name, folderId = folderId)
        try {
            pdfTools.renderPages(pdf, dpi = 200, maxEdge = 3000, password = password) { bitmap, _, _ ->
                pages.addBitmaps(docId, sequenceOf(bitmap))
            }
            onDone(docId)
        } catch (e: Exception) {
            library.deleteForever(listOf(docId))
            if (e is CancellationException) throw e
            onError(e)
        }
    }

    /** Both sides of a card on one sheet, as a new document. */
    fun idCard(uris: List<Uri>, onDone: (Long) -> Unit) = run("Making ID card sheet…") {
        val sides = withContext(Dispatchers.IO) { uris.take(2).mapNotNull { Bitmaps.decode(context.contentResolver, it, maxEdge = 1600) } }
        if (sides.isEmpty()) return@run
        val docId = library.createDocument(name = "ID card " + LibraryRepository.defaultName().removePrefix("Scan "))
        pages.addBitmaps(docId, sequenceOf(PageComposer.idCardSheet(sides)))
        onDone(docId)
    }

    /** Each captured spread becomes two pages. */
    fun book(uris: List<Uri>, onDone: (Long) -> Unit) = run("Splitting pages…") {
        val docId = library.createDocument(name = "Book " + LibraryRepository.defaultName().removePrefix("Scan "))
        val halves = uris.asSequence()
            .mapNotNull { Bitmaps.decode(context.contentResolver, it) }
            .flatMap { PageComposer.splitSpread(it).asSequence() }
        if (pages.addBitmaps(docId, halves) == 0) library.deleteForever(listOf(docId)) else onDone(docId)
    }

    /** Merges library documents into a new one, in the order given. */
    fun mergeDocuments(docIds: List<Long>, folderId: Long?, onDone: (Long) -> Unit) = run("Merging…") {
        val docId = library.createDocument(name = "Merged " + LibraryRepository.defaultName().removePrefix("Scan "), folderId = folderId)
        pages.copyPages(docIds, docId)
        onDone(docId)
    }

    private suspend fun filterFor(fromScanner: Boolean) =
        if (fromScanner) "original" else settings.settings.first().defaultFilter

    private fun run(message: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            _busy.value = message
            try {
                block()
            } finally {
                _busy.value = null
            }
        }
    }

    private companion object {
        const val UNREADABLE = "Couldn't read the selected images"
    }
}
