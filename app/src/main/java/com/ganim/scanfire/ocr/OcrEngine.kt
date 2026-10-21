package com.ganim.scanfire.ocr

import android.graphics.Bitmap
import com.ganim.scanfire.imaging.Bitmaps
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.googlecode.tesseract.android.TessBaseAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File

/** Scripts ML Kit can read on-device. Latin covers English, Spanish, French, German, Indonesian and many more. */
enum class OcrScript(val key: String, val label: String) {
    Latin("latin", "Latin (English, Spanish, French…)"),
    Devanagari("devanagari", "Devanagari (Hindi, Marathi, Nepali…)"),
    Chinese("chinese", "Chinese"),
    Japanese("japanese", "Japanese"),
    Korean("korean", "Korean"),
    ;

    companion object {
        fun of(key: String) = entries.firstOrNull { it.key == key } ?: Latin
    }
}

class OcrLanguageMissing(val language: OcrLanguage.Tesseract) : Exception("${language.name} isn't downloaded yet")

/** One recognised word and where it sits on the page, normalised to 0..1 of the page image. */
data class OcrWord(val text: String, val left: Float, val top: Float, val right: Float, val bottom: Float)

/**
 * On-device text recognition with two engines: ML Kit (models from Play services) and
 * Tesseract (models from [TessData]). Both run offline.
 */
class OcrEngine(private val tessData: TessData) {
    private val recognizers = mutableMapOf<OcrScript, TextRecognizer>()

    /** TessBaseAPI isn't thread-safe and is heavy to create, so one instance is reused under a lock. */
    private val tessLock = Mutex()
    private var tess: TessBaseAPI? = null
    private var tessLanguage: String? = null

    private fun recognizer(script: OcrScript): TextRecognizer = synchronized(recognizers) {
        recognizers.getOrPut(script) {
            TextRecognition.getClient(
                when (script) {
                    OcrScript.Latin -> TextRecognizerOptions.DEFAULT_OPTIONS
                    OcrScript.Devanagari -> DevanagariTextRecognizerOptions.Builder().build()
                    OcrScript.Chinese -> ChineseTextRecognizerOptions.Builder().build()
                    OcrScript.Japanese -> JapaneseTextRecognizerOptions.Builder().build()
                    OcrScript.Korean -> KoreanTextRecognizerOptions.Builder().build()
                },
            )
        }
    }

    suspend fun recognize(file: File, language: OcrLanguage): String = when (language) {
        is OcrLanguage.MlKit -> {
            // ML Kit wants text at least ~16 px tall; 2400 px on the long edge keeps body text legible and memory sane.
            val bitmap = withContext(Dispatchers.IO) { Bitmaps.decode(file, maxEdge = 2400) }
            if (bitmap == null) "" else try { recognizeMlKit(bitmap, language.script) } finally { bitmap.recycle() }
        }
        is OcrLanguage.Tesseract -> recognizeTesseract(file, language)
    }

    /** Words with positions, for the searchable-PDF text layer and for redaction. */
    suspend fun words(file: File, language: OcrLanguage): List<OcrWord> = when (language) {
        is OcrLanguage.MlKit -> {
            val bitmap = withContext(Dispatchers.IO) { Bitmaps.decode(file, maxEdge = 2400) }
            if (bitmap == null) emptyList() else try {
                val w = bitmap.width.toFloat(); val h = bitmap.height.toFloat()
                recognizer(language.script).process(InputImage.fromBitmap(bitmap, 0)).await()
                    .textBlocks.flatMap { it.lines }.flatMap { it.elements }
                    .mapNotNull { e -> e.boundingBox?.let { b -> OcrWord(e.text, b.left / w, b.top / h, b.right / w, b.bottom / h) } }
            } finally {
                bitmap.recycle()
            }
        }
        is OcrLanguage.Tesseract -> withTesseract(language) { api ->
            val bitmap = Bitmaps.decode(file, maxEdge = 2000) ?: return@withTesseract emptyList()
            try {
                val w = bitmap.width.toFloat(); val h = bitmap.height.toFloat()
                api.setImage(bitmap)
                api.utF8Text // runs recognition
                val out = mutableListOf<OcrWord>()
                val it = api.resultIterator ?: return@withTesseract emptyList()
                try {
                    it.begin()
                    do {
                        val text = it.getUTF8Text(TessBaseAPI.PageIteratorLevel.RIL_WORD)?.trim().orEmpty()
                        val r = it.getBoundingRect(TessBaseAPI.PageIteratorLevel.RIL_WORD)
                        if (text.isNotEmpty() && r != null) out += OcrWord(text, r.left / w, r.top / h, r.right / w, r.bottom / h)
                    } while (it.next(TessBaseAPI.PageIteratorLevel.RIL_WORD))
                } finally {
                    it.delete()
                }
                out
            } finally {
                api.clear()
                bitmap.recycle()
            }
        }
    }

    /** Runs [block] with a TessBaseAPI loaded for [language] (plus English when available). */
    private suspend fun <T> withTesseract(language: OcrLanguage.Tesseract, block: (TessBaseAPI) -> T): T {
        if (!tessData.has(language.code)) throw OcrLanguageMissing(language)
        val languages = if (language.code != "eng" && tessData.has("eng")) "${language.code}+eng" else language.code
        return withContext(Dispatchers.Default) {
            tessLock.withLock {
                val api = tess?.takeIf { tessLanguage == languages } ?: run {
                    tess?.recycle()
                    TessBaseAPI().also {
                        check(it.init(tessData.dataPath.absolutePath, languages, TessBaseAPI.OEM_LSTM_ONLY)) { "Couldn't load the ${language.name} model" }
                        it.pageSegMode = TessBaseAPI.PageSegMode.PSM_AUTO
                        tess = it
                        tessLanguage = languages
                    }
                }
                block(api)
            }
        }
    }

    private suspend fun recognizeMlKit(bitmap: Bitmap, script: OcrScript): String {
        val result = recognizer(script).process(InputImage.fromBitmap(bitmap, 0)).await()
        return layout(result)
    }

    // Mixed pages (Bengali with English words, Arabic with numbers...) read better with English alongside,
    // which withTesseract adds when it's downloaded.
    private suspend fun recognizeTesseract(file: File, language: OcrLanguage.Tesseract): String = withTesseract(language) { api ->
        // Tesseract is happiest with text ~30 px tall; 2000 px on the long edge is a good balance with speed.
        val bitmap = Bitmaps.decode(file, maxEdge = 2000) ?: return@withTesseract ""
        try {
            api.setImage(bitmap)
            api.utF8Text.orEmpty().replace(Regex("\n{3,}"), "\n\n").trim()
        } finally {
            api.clear()
            bitmap.recycle()
        }
    }

    companion object {
        /** Blocks in reading order (top to bottom, then left to right), separated by blank lines. */
        fun layout(text: Text): String =
            text.textBlocks
                .sortedWith(compareBy({ (it.boundingBox?.top ?: 0) / 24 }, { it.boundingBox?.left ?: 0 }))
                .joinToString("\n\n") { block -> block.lines.joinToString("\n") { it.text } }
                .trim()
    }
}
