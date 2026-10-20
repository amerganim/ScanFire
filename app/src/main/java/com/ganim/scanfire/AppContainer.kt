package com.ganim.scanfire

import android.content.Context
import com.ganim.scanfire.data.FileStore
import com.ganim.scanfire.data.LibraryRepository
import com.ganim.scanfire.data.PageRepository
import com.ganim.scanfire.data.SettingsRepository
import com.ganim.scanfire.data.db.ScanFireDatabase
import com.ganim.scanfire.export.Exporter
import com.ganim.scanfire.ocr.OcrEngine
import com.ganim.scanfire.ocr.TessData
import com.ganim.scanfire.ocr.Translator
import com.ganim.scanfire.pdf.PdfTools

/** Holds the app-wide singletons. */
class AppContainer(val context: Context) {
    val database: ScanFireDatabase by lazy { ScanFireDatabase.build(context) }
    val files: FileStore by lazy { FileStore(context) }
    val settings: SettingsRepository by lazy { SettingsRepository(context) }
    val library: LibraryRepository by lazy { LibraryRepository(database, files) }
    val pages: PageRepository by lazy { PageRepository(context, database, files) }
    val exporter: Exporter by lazy { Exporter(files) }
    val tessData: TessData by lazy { TessData(context) }
    val ocr: OcrEngine by lazy { OcrEngine(tessData) }
    val translator: Translator by lazy { Translator() }
    val pdfTools: PdfTools by lazy { PdfTools(context, files) }
}
