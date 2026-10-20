package com.ganim.scanfire.ocr

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext

/**
 * Tesseract language models, downloaded once from the Tesseract project's own GitHub repository
 * (tessdata_fast, Apache 2.0) into app storage. GitHub hosting is free, so this costs nobody anything.
 */
class TessData(context: Context) {
    /** Tesseract wants a directory that *contains* "tessdata/". */
    val dataPath: File = File(context.filesDir, "tesseract")
    private val dir = File(dataPath, "tessdata").apply { mkdirs() }

    private val _installed = MutableStateFlow(scan())
    val installed: StateFlow<Set<String>> = _installed

    /** Download progress per language code, 0..1. */
    private val _progress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val progress: StateFlow<Map<String, Float>> = _progress

    private fun scan(): Set<String> =
        dir.listFiles { f -> f.name.endsWith(".traineddata") }?.map { it.name.removeSuffix(".traineddata") }?.toSet().orEmpty()

    fun has(code: String) = File(dir, "$code.traineddata").exists()

    fun sizeOf(code: String) = File(dir, "$code.traineddata").length()

    suspend fun download(code: String) = withContext(Dispatchers.IO) {
        val target = File(dir, "$code.traineddata")
        if (target.exists()) return@withContext
        val part = File(dir, "$code.traineddata.part")
        _progress.update { it + (code to 0f) }
        try {
            var url = URL("https://github.com/tesseract-ocr/tessdata_fast/raw/main/$code.traineddata")
            var connection: HttpURLConnection
            // GitHub answers with redirects to its raw-content host; follow them (including https→https host changes).
            var hops = 0
            while (true) {
                connection = (url.openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = false
                    connectTimeout = 15_000
                    readTimeout = 30_000
                }
                val status = connection.responseCode
                if (status in 300..399 && hops++ < 5) {
                    url = URL(url, connection.getHeaderField("Location"))
                    connection.disconnect()
                    continue
                }
                if (status != 200) error("Download failed (HTTP $status)")
                break
            }
            val total = connection.contentLengthLong.takeIf { it > 0 }
            connection.inputStream.use { input ->
                part.outputStream().use { out ->
                    val buffer = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        if (!coroutineContext.isActive) error("Cancelled")
                        val n = input.read(buffer)
                        if (n < 0) break
                        out.write(buffer, 0, n)
                        done += n
                        if (total != null) _progress.update { it + (code to done / total.toFloat()) }
                    }
                }
            }
            // A model is at least tens of kilobytes; anything smaller is an error page.
            if (part.length() < 20_000) error("Download incomplete")
            part.renameTo(target)
        } finally {
            part.delete()
            _progress.update { it - code }
            _installed.value = scan()
        }
    }

    fun delete(code: String) {
        File(dir, "$code.traineddata").delete()
        _installed.value = scan()
    }
}
