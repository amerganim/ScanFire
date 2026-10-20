package com.ganim.scanfire

import android.app.Application
import com.ganim.scanfire.data.Backup
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class ScanFireApp : Application() {
    /** Manual dependency container; small enough that a DI framework would only add weight. */
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        // A restore staged in the previous run is swapped in before anything opens the database.
        Backup.applyPendingRestore(this)
        PDFBoxResourceLoader.init(applicationContext)
        container = AppContainer(this)
    }
}
