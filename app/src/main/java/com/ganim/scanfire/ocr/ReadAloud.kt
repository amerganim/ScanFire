package com.ganim.scanfire.ocr

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

/**
 * Reads recognised text aloud with Android's own text-to-speech (offline with the phone's
 * installed voices, free). Speaks paragraph by paragraph so long pages stay under the
 * engine's input limit.
 */
class ReadAloud(context: Context) {
    private val _speaking = MutableStateFlow(false)
    val speaking: StateFlow<Boolean> = _speaking
    private var ready = false
    private var pending: (() -> Unit)? = null

    private val tts = TextToSpeech(context.applicationContext) { status ->
        ready = status == TextToSpeech.SUCCESS
        if (ready) pending?.invoke()
        pending = null
    }.apply {
        setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) { _speaking.value = true }
            override fun onDone(id: String?) { if (id == LAST) _speaking.value = false }
            @Deprecated("Deprecated in Java")
            override fun onError(id: String?) { _speaking.value = false }
        })
    }

    /** Returns false (via [onUnavailable]) when no voice for [locale] is installed. */
    fun speak(text: String, locale: Locale, onUnavailable: (Locale) -> Unit) {
        val start = {
            when (tts.setLanguage(locale)) {
                TextToSpeech.LANG_MISSING_DATA, TextToSpeech.LANG_NOT_SUPPORTED -> onUnavailable(locale)
                else -> {
                    tts.stop()
                    val max = TextToSpeech.getMaxSpeechInputLength() - 100
                    val parts = text.split(Regex("\n\\s*\n")).flatMap { it.chunked(max) }.filter { it.isNotBlank() }
                    parts.forEachIndexed { i, part ->
                        tts.speak(part, if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, if (i == parts.lastIndex) LAST else "p$i")
                    }
                }
            }
        }
        if (ready) start() else pending = start
    }

    fun stop() {
        tts.stop()
        _speaking.value = false
    }

    fun shutdown() = tts.shutdown()

    companion object {
        private const val LAST = "last"

        /** Speech locale for an OCR language; Latin-script text gets its language from detection. */
        fun localeFor(language: OcrLanguage, detected: String?): Locale = when (language) {
            is OcrLanguage.MlKit -> when (language.script) {
                OcrScript.Devanagari -> Locale.forLanguageTag("hi")
                OcrScript.Chinese -> Locale.CHINESE
                OcrScript.Japanese -> Locale.JAPANESE
                OcrScript.Korean -> Locale.KOREAN
                OcrScript.Latin -> detected?.let(Locale::forLanguageTag) ?: Locale.getDefault()
            }
            // Tesseract uses ISO 639-2 codes; Locale understands them and maps to the 2-letter form.
            is OcrLanguage.Tesseract -> Locale.forLanguageTag(language.code)
        }
    }
}
