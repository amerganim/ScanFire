package com.ganim.scanfire.export

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import androidx.core.content.FileProvider
import java.io.File

object Sharing {
    fun uriFor(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.files", file)

    /** Opens the system share sheet for one or more files of the same type. */
    fun share(context: Context, files: List<File>, mime: String, subject: String? = null) {
        if (files.isEmpty()) return
        val uris = files.map { uriFor(context, it) }
        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris.first())
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        }.apply {
            type = mime
            subject?.let { putExtra(Intent.EXTRA_SUBJECT, it) }
            clipData = ClipData.newRawUri(null, uris.first()).apply { uris.drop(1).forEach { addItem(ClipData.Item(it)) } }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, null))
    }

    fun shareText(context: Context, text: String, subject: String? = null) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            subject?.let { putExtra(Intent.EXTRA_SUBJECT, it) }
        }
        context.startActivity(Intent.createChooser(intent, null))
    }

    /** Copies an export into a location the user picked with the system file picker (any drive provider works). */
    fun copyTo(context: Context, file: File, target: Uri) {
        context.contentResolver.openOutputStream(target, "w")?.use { out -> file.inputStream().use { it.copyTo(out) } }
            ?: error("Couldn't open the destination")
    }

    /** Hands a finished PDF to Android's print framework (any printer or "Save as PDF"). */
    fun print(context: Context, pdf: File, jobName: String) {
        val manager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
        manager.print(jobName, PdfPrintAdapter(pdf, jobName), PrintAttributes.Builder().build())
    }

    private class PdfPrintAdapter(private val pdf: File, private val name: String) : PrintDocumentAdapter() {
        override fun onLayout(
            oldAttributes: PrintAttributes?, newAttributes: PrintAttributes?, cancellationSignal: CancellationSignal?,
            callback: LayoutResultCallback, extras: android.os.Bundle?,
        ) {
            if (cancellationSignal?.isCanceled == true) { callback.onLayoutCancelled(); return }
            val info = PrintDocumentInfo.Builder("$name.pdf").setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).build()
            callback.onLayoutFinished(info, oldAttributes != newAttributes)
        }

        override fun onWrite(pages: Array<out PageRange>?, destination: ParcelFileDescriptor, cancellationSignal: CancellationSignal?, callback: WriteResultCallback) {
            runCatching {
                ParcelFileDescriptor.AutoCloseOutputStream(destination).use { out -> pdf.inputStream().use { it.copyTo(out) } }
            }.onSuccess { callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES)) }
                .onFailure { callback.onWriteFailed(it.message) }
        }
    }
}
