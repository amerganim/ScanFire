package com.ganim.scanfire.ocr

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.ganim.scanfire.imaging.Bitmaps
import kotlinx.coroutines.Dispatchers
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

/** On-device text recognition. Each script's model is fetched once by Play services, then works offline. */
class OcrEngine {
    private val recognizers = mutableMapOf<OcrScript, TextRecognizer>()

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

    suspend fun recognize(bitmap: Bitmap, script: OcrScript): String {
        val result = recognizer(script).process(InputImage.fromBitmap(bitmap, 0)).await()
        return layout(result)
    }

    suspend fun recognize(file: File, script: OcrScript): String {
        // ML Kit wants text at least ~16 px tall; 2400 px on the long edge keeps body text legible and memory sane.
        val bitmap = withContext(Dispatchers.IO) { Bitmaps.decode(file, maxEdge = 2400) } ?: return ""
        return try {
            recognize(bitmap, script)
        } finally {
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
