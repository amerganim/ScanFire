package com.ganim.scanfire.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.ganim.scanfire.data.FileStore
import com.ganim.scanfire.export.Exporter
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.util.Matrix
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.atan2
import kotlin.math.hypot

class PdfPasswordRequired : Exception("This PDF is password-protected")

/** Every PDF operation works on local copies in the cache; inputs are never modified. */
class PdfTools(private val context: Context, private val files: FileStore) {

    private val memory get() = MemoryUsageSetting.setupTempFileOnly()

    /** Copies a picked document into the cache so PdfBox and PdfRenderer get a seekable file. */
    suspend fun copyIn(uri: Uri): File = withContext(Dispatchers.IO) {
        val out = files.newTempFile("pdf")
        context.contentResolver.openInputStream(uri)?.use { input -> out.outputStream().use { input.copyTo(it) } }
            ?: error("Couldn't read the file")
        out
    }

    fun displayName(uri: Uri): String {
        val name = context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
        return name?.substringBeforeLast('.')?.ifBlank { null } ?: "Document"
    }

    private fun load(file: File, password: String?): PDDocument = try {
        PDDocument.load(file, password ?: "", memory)
    } catch (e: InvalidPasswordException) {
        throw PdfPasswordRequired()
    }

    suspend fun pageCount(file: File, password: String? = null): Int = withContext(Dispatchers.IO) {
        load(file, password).use { it.numberOfPages }
    }

    suspend fun isEncrypted(file: File): Boolean = withContext(Dispatchers.IO) {
        try { PDDocument.load(file, "", memory).use { it.isEncrypted } } catch (e: InvalidPasswordException) { true }
    }

    suspend fun merge(inputs: List<File>, name: String): File = withContext(Dispatchers.IO) {
        val out = files.freshExportFile("$name.pdf")
        PDFMergerUtility().apply {
            destinationFileName = out.path
            inputs.forEach { addSource(it) }
            mergeDocuments(memory)
        }
        out
    }

    suspend fun extract(input: File, indices: List<Int>, name: String, password: String? = null): File = withContext(Dispatchers.IO) {
        val out = files.freshExportFile("$name.pdf")
        load(input, password).use { src ->
            PDDocument().use { dst ->
                indices.forEach { dst.importPage(src.getPage(it)) }
                dst.save(out)
            }
        }
        out
    }

    suspend fun protect(input: File, password: String, name: String, currentPassword: String? = null): File = withContext(Dispatchers.IO) {
        val out = files.freshExportFile("$name.pdf")
        load(input, currentPassword).use { doc ->
            doc.isAllSecurityToBeRemoved = false
            val policy = StandardProtectionPolicy(password, password, AccessPermission()).apply { encryptionKeyLength = 128 }
            doc.protect(policy)
            doc.save(out)
        }
        out
    }

    suspend fun unlock(input: File, password: String, name: String): File = withContext(Dispatchers.IO) {
        val out = files.freshExportFile("$name.pdf")
        load(input, password).use { doc ->
            doc.isAllSecurityToBeRemoved = true
            doc.save(out)
        }
        out
    }

    /** Diagonal, translucent text across every page. Standard PDF fonts only cover Latin-1, so other characters are dropped. */
    suspend fun watermark(input: File, text: String, name: String, password: String? = null): File = withContext(Dispatchers.IO) {
        val out = files.freshExportFile("$name.pdf")
        val font = PDType1Font.HELVETICA_BOLD
        val safe = latin1(text).ifBlank { "CONFIDENTIAL" }
        load(input, password).use { doc ->
            doc.isAllSecurityToBeRemoved = true
            for (page in doc.pages) {
                val box = page.mediaBox
                val diagonal = hypot(box.width, box.height)
                val size = (diagonal * 0.7f / (font.getStringWidth(safe) / 1000f)).coerceIn(18f, 120f)
                val textWidth = font.getStringWidth(safe) / 1000f * size
                val angle = atan2(box.height, box.width).toDouble()
                PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                    cs.setGraphicsStateParameters(PDExtendedGraphicsState().apply { nonStrokingAlphaConstant = 0.18f })
                    cs.setNonStrokingColor(0.55f, 0.1f, 0.05f)
                    cs.beginText()
                    cs.setFont(font, size)
                    // Start so the text's centre lands on the page centre, along the diagonal.
                    val cx = box.lowerLeftX + box.width / 2
                    val cy = box.lowerLeftY + box.height / 2
                    val sx = cx - (textWidth / 2) * Math.cos(angle).toFloat() + (size / 3) * Math.sin(angle).toFloat()
                    val sy = cy - (textWidth / 2) * Math.sin(angle).toFloat() - (size / 3) * Math.cos(angle).toFloat()
                    cs.setTextMatrix(Matrix.getRotateInstance(angle, sx, sy))
                    cs.showText(safe)
                    cs.endText()
                }
            }
            doc.save(out)
        }
        out
    }

    /** "n / N" centred at the bottom of every page. */
    suspend fun pageNumbers(input: File, name: String, password: String? = null): File = withContext(Dispatchers.IO) {
        val out = files.freshExportFile("$name.pdf")
        val font = PDType1Font.HELVETICA
        load(input, password).use { doc ->
            doc.isAllSecurityToBeRemoved = true
            val total = doc.numberOfPages
            doc.pages.forEachIndexed { i, page ->
                val box = page.mediaBox
                val label = "${i + 1} / $total"
                val size = 10f
                val w = font.getStringWidth(label) / 1000f * size
                PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                    cs.beginText()
                    cs.setFont(font, size)
                    cs.setNonStrokingColor(0.25f, 0.25f, 0.25f)
                    cs.newLineAtOffset(box.lowerLeftX + (box.width - w) / 2, box.lowerLeftY + 18f)
                    cs.showText(label)
                    cs.endText()
                }
            }
            doc.save(out)
        }
        out
    }

    /** Rasterises each page at [dpi] and re-encodes it as JPEG: big savings for scanned PDFs, at the cost of selectable text. */
    suspend fun compress(input: File, name: String, dpi: Int, quality: Int, password: String? = null): File = withContext(Dispatchers.IO) {
        val out = files.freshExportFile("$name.pdf")
        val plain = decryptedCopy(input, password)
        PDDocument().use { dst ->
            renderPages(plain, dpi, maxEdge = 4000) { bitmap, widthPt, heightPt ->
                val image = JPEGFactory.createFromByteArray(dst, Exporter.jpegBytes(bitmap, quality))
                val page = PDPage(PDRectangle(widthPt, heightPt))
                dst.addPage(page)
                PDPageContentStream(dst, page).use { it.drawImage(image, 0f, 0f, widthPt, heightPt) }
            }
            dst.save(out)
        }
        if (plain != input) plain.delete()
        out
    }

    /** Renders pages to bitmaps (white background) and hands each to [onPage] with the page size in points. */
    suspend fun renderPages(
        file: File,
        dpi: Int,
        maxEdge: Int,
        password: String? = null,
        onPage: suspend (Bitmap, Float, Float) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val plain = decryptedCopy(file, password)
        try {
            ParcelFileDescriptor.open(plain, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                PdfRenderer(fd).use { renderer ->
                    for (i in 0 until renderer.pageCount) {
                        renderer.openPage(i).use { page ->
                            val scale = minOf(dpi / 72f, maxEdge.toFloat() / maxOf(page.width, page.height))
                            val bitmap = Bitmap.createBitmap((page.width * scale).toInt().coerceAtLeast(1), (page.height * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                            bitmap.eraseColor(Color.WHITE)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            onPage(bitmap, page.width.toFloat(), page.height.toFloat())
                            bitmap.recycle()
                        }
                    }
                }
            }
        } finally {
            if (plain != file) plain.delete()
        }
    }

    /** PdfRenderer can't open encrypted files, so they are decrypted into a temporary copy first. */
    private fun decryptedCopy(file: File, password: String?): File = load(file, password).use { doc ->
        if (!doc.isEncrypted) return file
        val out = files.newTempFile("pdf")
        doc.isAllSecurityToBeRemoved = true
        doc.save(out)
        out
    }

    companion object {
        fun latin1(text: String): String = text.filter { it.code in 32..126 || it.code in 160..255 }
    }
}
