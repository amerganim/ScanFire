package com.ganim.scanfire.scan

import android.content.Context
import android.net.Uri
import com.ganim.scanfire.imaging.Bitmaps
import com.ganim.scanfire.imaging.PageComposer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ganim.scanfire.data.LibraryRepository
import com.ganim.scanfire.data.PageRepository
import com.ganim.scanfire.data.SettingsRepository
import com.ganim.scanfire.pdf.PdfTools
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * Lives at the root so an import keeps running while screens change. Copies the picked or
 * scanned images into a document, then reports where they went.
 */
class CaptureViewModel(
    private val context: Context,
    private val library: LibraryRepository,
    private val pages: PageRepository,
    private val settings: SettingsRepository,
    private val pdfTools: PdfTools,
) : ViewModel() {
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    /** One-off user messages, e.g. when none of the picked images could be read. */
    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    /** [fromScanner] pages already carry the scanner's own filter, so the default filter is not stacked on top. */
    fun newDocument(folderId: Long?, uris: List<Uri>, fromScanner: Boolean, onDone: (Long) -> Unit) = run {
        val docId = library.createDocument(folderId = folderId)
        val added = pages.addImages(docId, uris, filterFor(fromScanner))
        if (added == 0) {
            library.deleteDocument(docId)
            _messages.send(UNREADABLE)
        } else {
            if (added < uris.size) _messages.send("${uris.size - added} of ${uris.size} images couldn't be read")
            onDone(docId)
        }
    }

    fun addToDocument(docId: Long, uris: List<Uri>, fromScanner: Boolean) = run {
        if (pages.addImages(docId, uris, filterFor(fromScanner)) == 0) _messages.send(UNREADABLE)
    }

    private companion object {
        const val UNREADABLE = "Couldn't read the selected images"
    }

    /** Turns a PDF into a ScanFire document, one page image per PDF page at ~200 dpi. */
    fun newDocumentFromPdf(folderId: Long?, pdf: File, name: String, password: String?, onDone: (Long) -> Unit, onError: (Exception) -> Unit) = run {
        pdfToDocument(folderId, pdf, name, password, onDone, onError)
    }

    /** A PDF shared or "opened with" ScanFire from another app. */
    fun importPdf(uri: Uri, onDone: (Long) -> Unit, onError: (Exception) -> Unit) = run {
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
            library.deleteDocument(docId)
            if (e is CancellationException) throw e
            onError(e)
        }
    }

    /** Both sides of a card on one sheet, as a new document. */
    fun idCard(uris: List<Uri>, onDone: (Long) -> Unit) = run {
        val sides = withContext(Dispatchers.IO) { uris.take(2).mapNotNull { Bitmaps.decode(context.contentResolver, it, maxEdge = 1600) } }
        if (sides.isEmpty()) return@run
        val docId = library.createDocument(name = "ID card " + LibraryRepository.defaultName().removePrefix("Scan "))
        pages.addBitmaps(docId, sequenceOf(PageComposer.idCardSheet(sides)))
        onDone(docId)
    }

    /** Each captured spread becomes two pages. */
    fun book(uris: List<Uri>, onDone: (Long) -> Unit) = run {
        val docId = library.createDocument(name = "Book " + LibraryRepository.defaultName().removePrefix("Scan "))
        val halves = uris.asSequence()
            .mapNotNull { Bitmaps.decode(context.contentResolver, it) }
            .flatMap { PageComposer.splitSpread(it).asSequence() }
        if (pages.addBitmaps(docId, halves) == 0) library.deleteDocument(docId) else onDone(docId)
    }

    /** Merges library documents into a new one, in the order given. */
    fun mergeDocuments(docIds: List<Long>, folderId: Long?, onDone: (Long) -> Unit) = run {
        val docId = library.createDocument(name = "Merged " + LibraryRepository.defaultName().removePrefix("Scan "), folderId = folderId)
        pages.copyPages(docIds, docId)
        onDone(docId)
    }

    private suspend fun filterFor(fromScanner: Boolean) =
        if (fromScanner) "original" else settings.settings.first().defaultFilter

    private fun run(block: suspend () -> Unit) {
        viewModelScope.launch {
            _busy.value = true
            try {
                block()
            } finally {
                _busy.value = false
            }
        }
    }
}
