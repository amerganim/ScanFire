package com.ganim.scanfire.tools

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ganim.scanfire.pdf.PageRanges
import com.ganim.scanfire.pdf.PdfPasswordRequired
import com.ganim.scanfire.pdf.PdfTools
import com.ganim.scanfire.ui.common.safeFileName
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

enum class PdfTool(val title: String, val description: String, val multiple: Boolean = false) {
    Import("Import PDF", "Turn a PDF into a ScanFire document to edit, OCR or re-export"),
    Merge("Merge PDFs", "Combine several PDFs into one, in the order you pick them", multiple = true),
    Extract("Extract pages", "Keep only the pages you choose, e.g. 1-3, 7"),
    Compress("Compress PDF", "Shrink a scanned PDF by re-encoding its pages"),
    Protect("Add password", "Lock a PDF so it needs a password to open"),
    Unlock("Remove password", "Save an unlocked copy of a PDF you know the password for"),
    Watermark("Watermark", "Stamp diagonal text across every page"),
    PageNumbers("Page numbers", "Add \"n / N\" to the bottom of every page"),
}

sealed interface ToolStep {
    data object Idle : ToolStep
    data class Working(val message: String) : ToolStep
    /** Inputs are loaded; the tool needs options (or just confirmation) before running. */
    data class Options(val tool: PdfTool, val name: String, val pageCount: Int, val encrypted: Boolean) : ToolStep
    data class Password(val tool: PdfTool, val wrongPassword: Boolean) : ToolStep
    data class Done(val tool: PdfTool, val file: File) : ToolStep
    data class Failed(val message: String) : ToolStep
}

data class ToolOptions(
    val ranges: String = "",
    val password: String = "",
    val text: String = "",
    /** 0 strong, 1 balanced, 2 light. */
    val compression: Int = 1,
)

class PdfToolsViewModel(private val pdf: PdfTools) : ViewModel() {
    private val _step = MutableStateFlow<ToolStep>(ToolStep.Idle)
    val step: StateFlow<ToolStep> = _step

    private var inputs: List<File> = emptyList()
    private var name = "Document"
    private var inputPassword: String? = null
    private var pendingOptions: ToolOptions? = null

    fun picked(tool: PdfTool, uris: List<Uri>) {
        if (uris.isEmpty()) return
        if (tool.multiple && uris.size < 2) {
            _step.value = ToolStep.Failed("Pick at least two PDFs to merge.")
            return
        }
        viewModelScope.launch {
            _step.value = ToolStep.Working("Opening…")
            try {
                inputs.forEach { it.delete() }
                inputs = uris.map { pdf.copyIn(it) }
                name = safeFileName(pdf.displayName(uris.first()))
                inputPassword = null
                pendingOptions = null
                if (tool.multiple) {
                    _step.value = ToolStep.Options(tool, name, 0, encrypted = false)
                } else {
                    val encrypted = pdf.isEncrypted(inputs.first())
                    if (encrypted && tool != PdfTool.Unlock) {
                        _step.value = ToolStep.Password(tool, wrongPassword = false)
                    } else {
                        val count = if (encrypted) 0 else pdf.pageCount(inputs.first())
                        _step.value = ToolStep.Options(tool, name, count, encrypted)
                    }
                }
            } catch (e: Exception) {
                fail(e)
            }
        }
    }

    /** The user typed the password for an encrypted input. */
    fun submitPassword(tool: PdfTool, password: String) {
        viewModelScope.launch {
            try {
                val count = pdf.pageCount(inputs.first(), password)
                inputPassword = password
                val options = pendingOptions
                if (options != null) run(tool, options) else _step.value = ToolStep.Options(tool, name, count, encrypted = true)
            } catch (e: PdfPasswordRequired) {
                _step.value = ToolStep.Password(tool, wrongPassword = true)
            } catch (e: Exception) {
                fail(e)
            }
        }
    }

    /** Validates options; returns an error message to show inline, or null when the tool started. */
    fun validate(tool: PdfTool, options: ToolOptions, pageCount: Int): String? = when (tool) {
        PdfTool.Extract -> if (PageRanges.parse(options.ranges, pageCount) == null) "Enter pages between 1 and $pageCount, like 1-3, 5" else null
        PdfTool.Protect -> if (options.password.length < 4) "Use at least 4 characters" else null
        PdfTool.Unlock -> if (options.password.isEmpty()) "Enter the PDF's password" else null
        PdfTool.Watermark -> if (PdfTools.latin1(options.text).isBlank()) "Enter watermark text (Latin letters)" else null
        else -> null
    }

    fun run(tool: PdfTool, options: ToolOptions) {
        pendingOptions = options
        viewModelScope.launch {
            _step.value = ToolStep.Working("Working on it…")
            try {
                val input = inputs.first()
                val pw = inputPassword
                val out = when (tool) {
                    PdfTool.Import -> input // handled by the caller, which turns it into a document
                    PdfTool.Merge -> pdf.merge(inputs, "$name-merged")
                    PdfTool.Extract -> pdf.extract(input, PageRanges.parse(options.ranges, pdf.pageCount(input, pw))!!, "$name-pages", pw)
                    PdfTool.Compress -> {
                        val (dpi, quality) = when (options.compression) { 0 -> 110 to 55; 2 -> 200 to 80; else -> 150 to 68 }
                        pdf.compress(input, "$name-compressed", dpi, quality, pw)
                    }
                    PdfTool.Protect -> pdf.protect(input, options.password, "$name-locked", pw)
                    PdfTool.Unlock -> pdf.unlock(input, options.password, "$name-unlocked")
                    PdfTool.Watermark -> pdf.watermark(input, options.text, "$name-watermarked", pw)
                    PdfTool.PageNumbers -> pdf.pageNumbers(input, "$name-numbered", pw)
                }
                _step.value = ToolStep.Done(tool, out)
            } catch (e: PdfPasswordRequired) {
                _step.value = if (tool == PdfTool.Unlock) ToolStep.Failed("That password is wrong.") else ToolStep.Password(tool, wrongPassword = inputPassword != null)
            } catch (e: Exception) {
                fail(e)
            }
        }
    }

    val importFile: File? get() = inputs.firstOrNull()
    val importName: String get() = name
    val importPassword: String? get() = inputPassword

    fun reset() {
        _step.value = ToolStep.Idle
    }

    private fun fail(e: Exception) {
        if (e is CancellationException) throw e
        _step.update { ToolStep.Failed("Couldn't process the PDF: ${e.message ?: e.javaClass.simpleName}") }
    }

    override fun onCleared() {
        inputs.forEach { it.delete() }
    }
}
