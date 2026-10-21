package com.ganim.scanfire.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("settings")

enum class SortOrder { Updated, Created, Name }

data class Settings(
    val sortOrder: SortOrder = SortOrder.Updated,
    val gridView: Boolean = false,
    /** 0 system, 1 light, 2 dark. */
    val themeMode: Int = 0,
    val dynamicColor: Boolean = false,
    /** Applied to imported photos. Magic colour is what makes a phone photo look scanned; Original stays one tap away. */
    val defaultFilter: String = "magic",
    /** "a4", "letter", "fit" (page sized to the image). */
    val pdfPageSize: String = "a4",
    /** JPEG quality used inside PDFs and image exports, 40..100. */
    val exportQuality: Int = 85,
    val pdfMargin: Boolean = true,
    val appLock: Boolean = false,
    /** OCR language key (see OcrLanguage); "auto" picks the best of Latin and the downloaded languages. */
    val ocrScript: String = "auto",
    /** The first-run welcome has been seen. */
    val welcomed: Boolean = false,
)

class SettingsRepository(private val context: Context) {
    private object Keys {
        val sort = stringPreferencesKey("sort")
        val grid = booleanPreferencesKey("grid")
        val theme = intPreferencesKey("theme")
        val dynamic = booleanPreferencesKey("dynamic")
        val filter = stringPreferencesKey("default_filter")
        val pageSize = stringPreferencesKey("pdf_page_size")
        val quality = intPreferencesKey("export_quality")
        val margin = booleanPreferencesKey("pdf_margin")
        val lock = booleanPreferencesKey("app_lock")
        val ocrScript = stringPreferencesKey("ocr_script")
        val welcomed = booleanPreferencesKey("welcomed")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { it.toSettings() }

    private fun Preferences.toSettings() = Settings(
        sortOrder = this[Keys.sort]?.let { runCatching { SortOrder.valueOf(it) }.getOrNull() } ?: SortOrder.Updated,
        gridView = this[Keys.grid] ?: false,
        themeMode = this[Keys.theme] ?: 0,
        dynamicColor = this[Keys.dynamic] ?: false,
        defaultFilter = this[Keys.filter] ?: "magic",
        pdfPageSize = this[Keys.pageSize] ?: "a4",
        exportQuality = this[Keys.quality] ?: 85,
        pdfMargin = this[Keys.margin] ?: true,
        appLock = this[Keys.lock] ?: false,
        ocrScript = this[Keys.ocrScript] ?: "auto",
        welcomed = this[Keys.welcomed] ?: false,
    )

    suspend fun setSort(order: SortOrder) = context.dataStore.edit { it[Keys.sort] = order.name }
    suspend fun setGrid(grid: Boolean) = context.dataStore.edit { it[Keys.grid] = grid }
    suspend fun setTheme(mode: Int) = context.dataStore.edit { it[Keys.theme] = mode }
    suspend fun setDynamicColor(on: Boolean) = context.dataStore.edit { it[Keys.dynamic] = on }
    suspend fun setDefaultFilter(filter: String) = context.dataStore.edit { it[Keys.filter] = filter }
    suspend fun setPdfPageSize(size: String) = context.dataStore.edit { it[Keys.pageSize] = size }
    suspend fun setExportQuality(q: Int) = context.dataStore.edit { it[Keys.quality] = q.coerceIn(40, 100) }
    suspend fun setPdfMargin(on: Boolean) = context.dataStore.edit { it[Keys.margin] = on }
    suspend fun setAppLock(on: Boolean) = context.dataStore.edit { it[Keys.lock] = on }
    suspend fun setOcrScript(script: String) = context.dataStore.edit { it[Keys.ocrScript] = script }
    suspend fun setWelcomed() = context.dataStore.edit { it[Keys.welcomed] = true }
}
