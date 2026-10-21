package com.ganim.scanfire.export

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ganim.scanfire.data.LibraryRepository
import com.ganim.scanfire.data.PageRepository
import com.ganim.scanfire.data.SettingsRepository
import com.ganim.scanfire.ocr.OcrEngine
import com.ganim.scanfire.ocr.OcrLanguage
import com.ganim.scanfire.ui.common.safeFileName
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.io.File

enum class ExportAction { Share, Save, Print }

sealed interface ExportEvent {
    data class Share(val files: List<File>, val mime: String, val subject: String) : ExportEvent
    /** Ask the user where to save; then call [ExportViewModel.saveTo]. */
    data class PickDestination(val file: File, val mime: String) : ExportEvent
    data class Print(val pdf: File, val name: String) : ExportEvent
    data class Message(val text: String) : ExportEvent
}

class ExportViewModel(
    private val library: LibraryRepository,
    private val pages: PageRepository,
    private val settings: SettingsRepository,
    private val exporter: Exporter,
    private val ocr: OcrEngine,
) : ViewModel() {
    /** Non-null while exporting; says what's happening (OCR for the text layer can take a while). */
    private val _busy = MutableStateFlow<String?>(null)
    val busy: StateFlow<String?> = _busy
    private val _events = Channel<ExportEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var pendingSave: File? = null

    /** Exports [pageIds] of [docId] (all pages when empty) in [format], then shares, saves or prints it. */
    fun export(docId: Long, pageIds: Set<Long>, format: ExportFormat, action: ExportAction, pageSize: String? = null, searchable: Boolean = false) {
        viewModelScope.launch {
            _busy.value = "Preparing export…"
            try {
                val doc = library.document(docId).filterNotNull().first()
                val all = pages.pagesNow(docId)
                val selected = if (pageIds.isEmpty()) all else all.filter { it.id in pageIds }
                val images = selected.map(pages::imageFile)
                val s = settings.settings.first()
                val name = safeFileName(doc.name)
                val format2 = if (action == ExportAction.Print) ExportFormat.Pdf else format
                val outputs: List<File> = when (format2) {
                    ExportFormat.Pdf -> {
                        val layers = if (searchable) {
                            val language = OcrLanguage.of(s.ocrScript)
                            images.mapIndexed { i, image ->
                                _busy.value = "Reading text for search: page ${i + 1} of ${images.size}…"
                                runCatching { ocr.words(image, language) }.getOrDefault(emptyList())
                            }
                        } else null
                        _busy.value = "Building PDF…"
                        listOf(exporter.pdf(name, images, PdfOptions(pageSize ?: s.pdfPageSize, s.pdfMargin, s.exportQuality), textLayers = layers))
                    }
                    ExportFormat.Jpeg ->
                        // Saving goes to one file the user picks, so several images become a ZIP.
                        if (action == ExportAction.Save && images.size > 1) listOf(exporter.zip(name, images, s.exportQuality))
                        else exporter.jpegs(name, images, s.exportQuality)
                    ExportFormat.LongImage -> listOf(exporter.longImage(name, images, s.exportQuality))
                    ExportFormat.Zip -> listOf(exporter.zip(name, images, s.exportQuality))
                }
                val mime = if (outputs.first().extension == "zip") ExportFormat.Zip.mime else format2.mime
                when (action) {
                    ExportAction.Share -> _events.send(ExportEvent.Share(outputs, mime, doc.name))
                    ExportAction.Save -> {
                        pendingSave = outputs.first()
                        _events.send(ExportEvent.PickDestination(outputs.first(), mime))
                    }
                    ExportAction.Print -> _events.send(ExportEvent.Print(outputs.first(), doc.name))
                }
            } catch (e: Exception) {
                _events.send(ExportEvent.Message("Export failed: ${e.message ?: e.javaClass.simpleName}"))
            } finally {
                _busy.value = null
            }
        }
    }

    fun saveTo(context: Context, uri: Uri?) {
        val file = pendingSave ?: return
        pendingSave = null
        if (uri == null) return
        viewModelScope.launch {
            val result = runCatching { withContext(Dispatchers.IO) { Sharing.copyTo(context, file, uri) } }
            _events.send(ExportEvent.Message(if (result.isSuccess) "Saved ${file.name}" else "Couldn't save: ${result.exceptionOrNull()?.message}"))
        }
    }
}
