package com.ganim.scanfire

import android.content.Context
import com.ganim.scanfire.data.FileStore
import com.ganim.scanfire.data.LibraryRepository
import com.ganim.scanfire.data.PageRepository
import com.ganim.scanfire.data.SettingsRepository
import com.ganim.scanfire.data.db.ScanFireDatabase

/** Holds the app-wide singletons. */
class AppContainer(val context: Context) {
    val database: ScanFireDatabase by lazy { ScanFireDatabase.build(context) }
    val files: FileStore by lazy { FileStore(context) }
    val settings: SettingsRepository by lazy { SettingsRepository(context) }
    val library: LibraryRepository by lazy { LibraryRepository(database, files) }
    val pages: PageRepository by lazy { PageRepository(context, database, files) }
}
