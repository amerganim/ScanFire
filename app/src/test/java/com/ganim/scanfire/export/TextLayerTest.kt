package com.ganim.scanfire.export

import com.ganim.scanfire.ocr.OcrWord
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.text.PDFTextStripper
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.File

class TextLayerTest {
    /** Any TrueType font on the build machine: Arial on Windows, DejaVu on the Linux CI runner. */
    private val font = listOf(
        "C:/Windows/Fonts/arial.ttf",
        "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
        "/Library/Fonts/Arial.ttf",
    ).map(::File).firstOrNull { it.exists() }

    @Test
    fun `hidden text layer is extractable from the saved PDF`() {
        assumeTrue(font != null, "no TrueType font available on this machine")
        val bytes = ByteArrayOutputStream().also { out ->
            PDDocument().use { doc ->
                val page = PDPage(PDRectangle(595f, 842f))
                doc.addPage(page)
                val words = listOf(
                    OcrWord("Invoice", 0.10f, 0.10f, 0.30f, 0.14f),
                    OcrWord("Total:", 0.10f, 0.50f, 0.22f, 0.54f),
                    OcrWord("1,250.00", 0.24f, 0.50f, 0.40f, 0.54f),
                )
                PDPageContentStream(doc, page).use { cs -> TextLayer(doc) { font }.draw(cs, words, 0f, 0f, 595f, 842f) }
                doc.save(out)
            }
        }.toByteArray()

        val text = PDDocument.load(bytes).use { PDFTextStripper().getText(it) }
        text shouldContain "Invoice"
        text shouldContain "1,250.00"
    }
}
