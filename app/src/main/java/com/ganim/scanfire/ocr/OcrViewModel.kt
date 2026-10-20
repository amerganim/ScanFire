package com.ganim.scanfire.ocr

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ganim.scanfire.data.FileStore
import com.ganim.scanfire.data.LibraryRepository
import com.ganim.scanfire.data.PageRepository
import com.ganim.scanfire.data.SettingsRepository
import com.ganim.scanfire.data.db.PageEntity
import com.ganim.scanfire.export.DocxWriter
import com.ganim.scanfire.ui.common.safeFileName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class OcrState(
    val title: String = "",
    /** True when looking at one page, whose text can be edited and saved back. */
    val singlePage: Boolean = false,
    val text: String = "",
    val edited: Boolean = false,
    val running: Boolean = false,
    val progress: Pair<Int, Int> = 0 to 0,
    val script: OcrScript = OcrScript.Latin,
    val error: String? = null,
    val translation: String? = null,
    val translating: Boolean = false,
    val translateStatus: String? = null,
    val targetLanguage: String = "en",
)

/** Recognises text for one page ([pageId] > 0) or the whole document, caching results on each page. */
class OcrViewModel(
    private val docId: Long,
    private val pageId: Long,
    private val library: LibraryRepository,
    private val pages: PageRepository,
    private val settings: SettingsRepository,
    private val engine: OcrEngine,
    private val translator: Translator,
    private val files: FileStore,
) : ViewModel() {
    private val _state = MutableStateFlow(OcrState(singlePage = pageId > 0))
    val state: StateFlow<OcrState> = _state
    val languages get() = translator.languages

    private var targets: List<PageEntity> = emptyList()
    private var job: Job? = null

    init {
        viewModelScope.launch {
            val doc = library.document(docId).filterNotNull().first()
            val script = OcrScript.of(settings.settings.first().ocrScript)
            targets = pages.pagesNow(docId).filter { pageId <= 0 || it.id == pageId }
            val title = if (pageId > 0) "${doc.name} · page ${pages.pagesNow(docId).indexOfFirst { it.id == pageId } + 1}" else doc.name
            _state.update { it.copy(title = title, script = script) }
            run(force = false)
        }
    }

    fun setScript(script: OcrScript) {
        _state.update { it.copy(script = script) }
        viewModelScope.launch { settings.setOcrScript(script.key) }
        run(force = true)
    }

    /** Recognises every target page; cached text is reused unless [force]. */
    fun run(force: Boolean) {
        job?.cancel()
        job = viewModelScope.launch {
            val script = _state.value.script
            _state.update { it.copy(running = true, error = null, progress = 0 to targets.size, translation = null) }
            val texts = mutableListOf<String>()
            try {
                targets.forEachIndexed { i, page ->
                    val cached = if (force) null else page.ocrText
                    val text = cached ?: engine.recognize(pages.imageFile(page), script).also { pages.setOcrText(page.id, it) }
                    texts += text
                    _state.update { it.copy(progress = i + 1 to targets.size) }
                }
                targets = pages.pagesNow(docId).filter { t -> targets.any { it.id == t.id } }
                _state.update { it.copy(text = combine(texts), edited = false, running = false) }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.update {
                    it.copy(running = false, error = "Text recognition failed: ${e.message ?: "the model may still be downloading. Try again in a moment."}")
                }
            }
        }
    }

    private fun combine(texts: List<String>): String =
        if (texts.size == 1) texts.first()
        else texts.mapIndexed { i, t -> "— Page ${i + 1} —\n$t" }.joinToString("\n\n")

    fun edit(text: String) = _state.update { it.copy(text = text, edited = true) }

    fun saveEdit() {
        val page = targets.singleOrNull() ?: return
        viewModelScope.launch {
            pages.setOcrText(page.id, _state.value.text)
            _state.update { it.copy(edited = false) }
        }
    }

    fun setTarget(code: String) = _state.update { it.copy(targetLanguage = code) }

    fun translate() {
        val text = _state.value.text
        if (text.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(translating = true, translateStatus = "Detecting language…", translation = null) }
            try {
                val source = translator.detect(text)
                if (source == null) {
                    _state.update { it.copy(translating = false, translateStatus = "Couldn't tell which language this is.") }
                    return@launch
                }
                val target = _state.value.targetLanguage
                if (!translator.isDownloaded(source) || !translator.isDownloaded(target)) {
                    _state.update { it.copy(translateStatus = "Downloading language model (once, then offline)…") }
                }
                val result = translator.translate(text, source, target)
                _state.update { it.copy(translating = false, translation = result, translateStatus = "Translated from ${languageName(source)}") }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.update { it.copy(translating = false, translateStatus = "Translation failed: ${e.message ?: "check your connection for the first download"}") }
            }
        }
    }

    private fun languageName(code: String) = translator.languages.firstOrNull { it.code == code }?.name ?: code

    suspend fun exportTxt(text: String = _state.value.text): File = withContext(Dispatchers.IO) {
        files.freshExportFile(safeFileName(_state.value.title) + ".txt").apply { writeText(text) }
    }

    suspend fun exportDocx(): File = withContext(Dispatchers.IO) {
        val texts = if (targets.size > 1) targets.map { pages.pagesNow(docId).first { p -> p.id == it.id }.ocrText.orEmpty() } else listOf(_state.value.text)
        files.freshExportFile(safeFileName(_state.value.title) + ".docx").apply {
            outputStream().use { DocxWriter.write(texts, it, _state.value.title) }
        }
    }
}
