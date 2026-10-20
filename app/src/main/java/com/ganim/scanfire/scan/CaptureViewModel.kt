package com.ganim.scanfire.scan

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ganim.scanfire.data.LibraryRepository
import com.ganim.scanfire.data.PageRepository
import com.ganim.scanfire.data.SettingsRepository
import com.ganim.scanfire.pdf.PdfTools
import kotlinx.coroutines.CancellationException
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Lives at the root so an import keeps running while screens change. Copies the picked or
 * scanned images into a document, then reports where they went.
 */
class CaptureViewModel(
    private val library: LibraryRepository,
    private val pages: PageRepository,
    private val settings: SettingsRepository,
    private val pdfTools: PdfTools,
) : ViewModel() {
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    /** [fromScanner] pages already carry the scanner's own filter, so the default filter is not stacked on top. */
    fun newDocument(folderId: Long?, uris: List<Uri>, fromScanner: Boolean, onDone: (Long) -> Unit) = run {
        val docId = library.createDocument(folderId = folderId)
        val added = pages.addImages(docId, uris, filterFor(fromScanner))
        if (added == 0) {
            library.deleteDocument(docId)
        } else {
            onDone(docId)
        }
    }

    fun addToDocument(docId: Long, uris: List<Uri>, fromScanner: Boolean) = run {
        pages.addImages(docId, uris, filterFor(fromScanner))
    }

    /** Turns a PDF into a ScanFire document, one page image per PDF page at ~200 dpi. */
    fun newDocumentFromPdf(folderId: Long?, pdf: File, name: String, password: String?, onDone: (Long) -> Unit, onError: (Exception) -> Unit) = run {
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
