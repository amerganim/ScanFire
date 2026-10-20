package com.ganim.scanfire.scan

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ganim.scanfire.data.LibraryRepository
import com.ganim.scanfire.data.PageRepository
import com.ganim.scanfire.data.SettingsRepository
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
