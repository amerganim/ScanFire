package com.ganim.scanfire.ocr

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.tasks.await
import java.util.Locale

/**
 * ML Kit on-device translation. Each language model (~30 MB) is downloaded once from Google for
 * free and then translation runs fully offline. No API key, no per-use cost.
 */
class Translator {
    data class Language(val code: String, val name: String)

    val languages: List<Language> by lazy {
        TranslateLanguage.getAllLanguages()
            .map { Language(it, Locale.forLanguageTag(it).getDisplayName(Locale.getDefault()).replaceFirstChar(Char::titlecase)) }
            .sortedBy { it.name }
    }

    /** BCP-47 code of the text's language when ML Kit can translate from it, else null. */
    suspend fun detect(text: String): String? {
        val client = LanguageIdentification.getClient()
        return try {
            val code = client.identifyLanguage(text.take(2000)).await()
            TranslateLanguage.fromLanguageTag(code)
        } finally {
            client.close()
        }
    }

    suspend fun isDownloaded(code: String): Boolean =
        RemoteModelManager.getInstance().isModelDownloaded(TranslateRemoteModel.Builder(code).build()).await()

    suspend fun translate(text: String, source: String, target: String): String {
        if (source == target) return text
        val client = Translation.getClient(TranslatorOptions.Builder().setSourceLanguage(source).setTargetLanguage(target).build())
        return try {
            client.downloadModelIfNeeded(DownloadConditions.Builder().build()).await()
            // Paragraph by paragraph keeps the layout and stays well inside the model's input size.
            text.split("\n\n").map { para -> if (para.isBlank()) para else client.translate(para).await() }.joinToString("\n\n")
        } finally {
            client.close()
        }
    }
}
