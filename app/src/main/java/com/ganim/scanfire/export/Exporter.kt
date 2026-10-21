package com.ganim.scanfire.export

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import com.ganim.scanfire.data.FileStore
import com.ganim.scanfire.imaging.Bitmaps
import com.ganim.scanfire.ocr.OcrWord
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

enum class ExportFormat(val label: String, val ext: String, val mime: String) {
    Pdf("PDF", "pdf", "application/pdf"),
    Jpeg("JPEG images", "jpg", "image/jpeg"),
    LongImage("Long image", "jpg", "image/jpeg"),
    Zip("ZIP of images", "zip", "application/zip"),
}

data class PdfOptions(
    /** "a4", "letter" or "fit". */
    val pageSize: String = "a4",
    val margin: Boolean = true,
    val quality: Int = 85,
    /** Longest image edge inside the PDF; 2480 px is A4 at 300 dpi. */
    val maxEdge: Int = 2480,
)

/** Builds export files in the cache. Nothing reaches shared storage until the user shares or saves. */
class Exporter(private val files: FileStore) {

    /**
     * [textLayers], when given, holds each page's OCR words; they're drawn as invisible text so the PDF is
     * searchable and copyable.
     */
    suspend fun pdf(name: String, images: List<File>, options: PdfOptions, out: File = files.freshExportFile("$name.pdf"), textLayers: List<List<OcrWord>>? = null): File =
        withContext(Dispatchers.IO) {
            PDDocument().use { doc ->
                doc.documentInformation.title = name
                doc.documentInformation.creator = "ScanFire"
                val layer = if (textLayers != null) TextLayer(doc) else null
                for ((index, file) in images.withIndex()) {
                    val bitmap = Bitmaps.decode(file, options.maxEdge) ?: continue
                    val jpeg = jpegBytes(bitmap, options.quality)
                    val image = JPEGFactory.createFromByteArray(doc, jpeg)
                    val box = pageBox(options.pageSize, bitmap.width, bitmap.height)
                    val page = PDPage(box)
                    doc.addPage(page)
                    val fit = fitRect(box.width, box.height, bitmap.width.toFloat(), bitmap.height.toFloat(), if (options.margin && options.pageSize != "fit") 24f else 0f)
                    PDPageContentStream(doc, page).use { cs ->
                        cs.drawImage(image, fit[0], fit[1], fit[2], fit[3])
                        textLayers?.getOrNull(index)?.let { layer?.draw(cs, it, fit[0], fit[1], fit[2], fit[3]) }
                    }
                    bitmap.recycle()
                }
                doc.save(out)
            }
            out
        }

    suspend fun jpegs(name: String, images: List<File>, quality: Int): List<File> = withContext(Dispatchers.IO) {
        images.mapIndexedNotNull { i, file ->
            val bitmap = Bitmaps.decode(file) ?: return@mapIndexedNotNull null
            val out = files.freshExportFile(if (images.size == 1) "$name.jpg" else "$name-${i + 1}.jpg")
            Bitmaps.save(bitmap, out, quality)
            bitmap.recycle()
            out
        }
    }

    suspend fun zip(name: String, images: List<File>, quality: Int): File = withContext(Dispatchers.IO) {
        val out = files.freshExportFile("$name.zip")
        ZipOutputStream(out.outputStream().buffered()).use { zip ->
            images.forEachIndexed { i, file ->
                val bitmap = Bitmaps.decode(file) ?: return@forEachIndexed
                zip.putNextEntry(ZipEntry("$name-${(i + 1).toString().padStart(3, '0')}.jpg"))
                zip.write(jpegBytes(bitmap, quality))
                zip.closeEntry()
                bitmap.recycle()
            }
        }
        out
    }

    /** All pages stacked top to bottom at one width, for sharing as a single picture. */
    suspend fun longImage(name: String, images: List<File>, quality: Int, width: Int = 1240): File = withContext(Dispatchers.IO) {
        val heights = images.map { file ->
            val (w, h) = Bitmaps.size(file)
            if (w <= 0) 0 else (h.toLong() * width / w).toInt()
        }
        val gap = 16
        // Keep the canvas under ~60 MP so it can't run the heap out on long documents.
        val total = heights.sum() + gap * (images.size - 1).coerceAtLeast(0)
        val scale = minOf(1f, 48_000f / total.coerceAtLeast(1))
        val w = (width * scale).toInt()
        val canvasBitmap = Bitmap.createBitmap(w, (total * scale).toInt().coerceAtLeast(1), Bitmap.Config.RGB_565)
        val canvas = Canvas(canvasBitmap)
        canvas.drawColor(Color.WHITE)
        var y = 0f
        images.forEachIndexed { i, file ->
            val bitmap = Bitmaps.decode(file, maxEdge = 2000) ?: return@forEachIndexed
            val h = heights[i] * scale
            canvas.drawBitmap(bitmap, null, android.graphics.RectF(0f, y, w.toFloat(), y + h), null)
            bitmap.recycle()
            y += h + gap * scale
        }
        val out = files.freshExportFile("$name-long.jpg")
        Bitmaps.save(canvasBitmap, out, quality)
        canvasBitmap.recycle()
        out
    }

    companion object {
        fun jpegBytes(bitmap: Bitmap, quality: Int): ByteArray =
            ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, quality, it) }.toByteArray()

        /** Page in PDF points (1/72 inch), turned landscape when the image is wider than tall. */
        fun pageBox(size: String, imageW: Int, imageH: Int): PDRectangle {
            val landscape = imageW > imageH
            val (w, h) = when (size) {
                "letter" -> 612f to 792f
                "fit" -> {
                    // Long edge at A4's long edge; the short edge follows the image.
                    val long = 842f
                    if (landscape) long to long * imageH / imageW else long * imageW / imageH to long
                }
                else -> 595f to 842f
            }
            return if (size != "fit" && landscape) PDRectangle(h, w) else PDRectangle(w, h)
        }

        /** [x, y, width, height] of an image scaled to fit inside the page minus [margin], centred. */
        fun fitRect(pageW: Float, pageH: Float, imgW: Float, imgH: Float, margin: Float): FloatArray {
            val availW = pageW - 2 * margin
            val availH = pageH - 2 * margin
            val scale = minOf(availW / imgW, availH / imgH)
            val w = imgW * scale
            val h = imgH * scale
            return floatArrayOf((pageW - w) / 2, (pageH - h) / 2, w, h)
        }
    }
}
