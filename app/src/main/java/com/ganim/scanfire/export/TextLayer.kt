package com.ganim.scanfire.export

import com.ganim.scanfire.ocr.OcrWord
import com.tom_roush.fontbox.ttf.TTFParser
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import com.tom_roush.pdfbox.pdmodel.graphics.state.RenderingMode
import java.io.File

/**
 * Invisible OCR text drawn exactly over each word of a page image, which makes a scanned PDF
 * searchable and copyable in any PDF reader. Glyphs come from the phone's own fonts (Roboto, Noto
 * Sans Bengali, Arabic...), embedded as subsets, so ScanFire ships no font files.
 */
class TextLayer(
    private val doc: PDDocument,
    /** Picks the font file for a word; the phone's fonts by default, injectable for tests. */
    private val fontFile: (String) -> File? = SystemFonts::coveringAll,
) {
    private val fonts = mutableMapOf<File, PDFont>()

    /** Draws [words] (normalised to the image) over the image placed at [x], [y], [w], [h] in PDF points. */
    fun draw(cs: PDPageContentStream, words: List<OcrWord>, x: Float, y: Float, w: Float, h: Float) {
        if (words.isEmpty()) return
        cs.beginText()
        cs.setRenderingMode(RenderingMode.NEITHER)
        for (word in words) {
            val font = fontFor(word.text) ?: continue
            val boxW = (word.right - word.left) * w
            val boxH = (word.bottom - word.top) * h
            if (boxW <= 0.5f || boxH <= 0.5f) continue
            val size = (boxH * 0.9f).coerceIn(1f, 200f)
            val textW = runCatching { font.getStringWidth(word.text) / 1000f * size }.getOrNull() ?: continue
            if (textW <= 0f) continue
            cs.setFont(font, size)
            // Stretch horizontally so selection highlights line up with the word in the image.
            cs.setHorizontalScaling((boxW / textW * 100f).coerceIn(10f, 1000f))
            cs.setTextMatrix(com.tom_roush.pdfbox.util.Matrix.getTranslateInstance(x + word.left * w, y + (1f - word.bottom) * h + boxH * 0.15f))
            runCatching { cs.showText(word.text) }
        }
        cs.endText()
    }

    private fun fontFor(text: String): PDFont? {
        val file = fontFile(text) ?: return null
        return fonts.getOrPut(file) { PDType0Font.load(doc, file) }
    }
}

/** Finds an installed .ttf whose character map covers a piece of text; results are cached per script block. */
object SystemFonts {
    private val dirs = listOf(File("/system/fonts"), File("/product/fonts"))
    private val coverage = mutableMapOf<File, (Int) -> Boolean>()
    private val chosen = mutableMapOf<Character.UnicodeBlock?, File?>()

    private val candidates: List<File> by lazy {
        val all = dirs.flatMap { it.listFiles { f -> f.extension.equals("ttf", true) }?.toList().orEmpty() }
        // Regular weights first; Roboto first for Latin; then everything else.
        all.sortedWith(compareBy({ !it.name.contains("Regular") }, { !it.name.startsWith("Roboto") }, { it.name }))
    }

    @Synchronized
    fun coveringAll(text: String): File? {
        val codepoints = text.codePoints().toArray().filter { !Character.isWhitespace(it) }
        if (codepoints.isEmpty()) return null
        // The block of the first letter-like character decides the font for the whole word.
        val block = codepoints.firstOrNull { Character.isLetter(it) }?.let { Character.UnicodeBlock.of(it) }
        chosen[block]?.let { f -> return f.takeIf { covers(it, codepoints) } }
        val found = candidates.firstOrNull { covers(it, codepoints) }
        chosen[block] = found
        return found
    }

    private fun covers(file: File, codepoints: List<Int>): Boolean {
        val lookup = coverage.getOrPut(file) {
            runCatching {
                val ttf = TTFParser().parse(file)
                // The cmap is read into memory here, so the file can be closed straight away.
                val cmap = ttf.unicodeCmapLookup
                ttf.close()
                val fn: (Int) -> Boolean = { cp -> cmap.getGlyphId(cp) != 0 }
                fn
            }.getOrDefault { _: Int -> false }
        }
        return codepoints.all(lookup)
    }
}
