package com.ganim.scanfire.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.ganim.scanfire.document.DocumentAction
import com.ganim.scanfire.document.DocumentScreen
import com.ganim.scanfire.document.DocumentViewModel
import com.ganim.scanfire.export.ExportEffects
import com.ganim.scanfire.export.ExportFormat
import com.ganim.scanfire.export.ExportSheet
import com.ganim.scanfire.export.ExportViewModel
import com.ganim.scanfire.scan.CaptureTarget
import com.ganim.scanfire.ui.common.containerViewModel
import com.ganim.scanfire.ui.common.appContainer
import com.ganim.scanfire.ocr.OcrLanguage
import com.ganim.scanfire.ocr.TextInsights
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import android.widget.Toast
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import coil3.SingletonImageLoader
import com.ganim.scanfire.data.db.ExpenseEntity
import com.ganim.scanfire.expenses.ExpenseDialog
import com.ganim.scanfire.expenses.ExpenseParser
import com.ganim.scanfire.reminders.ReminderDialog
import com.ganim.scanfire.reminders.suggestReminderDay
import com.ganim.scanfire.scan.findActivity
import com.ganim.scanfire.security.AppLock
import com.ganim.scanfire.ui.common.ConfirmDialog

/** The document screen plus everything it launches: capture, export, and per-phase tools. */
@Composable
fun DocumentRoute(docId: Long, nav: NavHostController, capture: Capture) {
    val container = appContainer()
    val vm = containerViewModel(key = "doc-$docId") { DocumentViewModel(docId, it.library, it.pages) }
    val exportVm = containerViewModel(key = "export-$docId") { ExportViewModel(it.library, it.pages, it.settings, it.exporter, it.ocr) }
    val state by vm.state.collectAsStateWithLifecycle()
    val exportBusy by exportVm.busy.collectAsStateWithLifecycle()
    var exportPages by rememberSaveable { mutableStateOf<LongArray?>(null) }
    ExportEffects(exportVm)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf<String?>(null) }
    var confirmVault by remember { mutableStateOf(false) }
    var reminder by remember { mutableStateOf<Long?>(null) } // suggested day, or 0 for none
    var expense by remember { mutableStateOf<ExpenseEntity?>(null) }

    /** The first page's text, recognised now if it hasn't been yet. */
    suspend fun firstPageText(): String? {
        val page = vm.pageRepo.pagesNow(docId).firstOrNull() ?: return null
        return page.ocrText ?: runCatching {
            container.ocr.recognize(vm.pageRepo.imageFile(page), OcrLanguage.of(container.settings.settings.first().ocrScript))
                .also { vm.pageRepo.setOcrText(page.id, it) }
        }.getOrNull()
    }

    Box(Modifier.fillMaxSize()) {
        DocumentScreen(
            vm = vm,
            onBack = { nav.popBackStack() },
            onOpenPage = { nav.navigate(Routes.page(docId, it)) },
            onScanMore = { capture.scan(CaptureTarget.AddTo(docId)) },
            onImportMore = { capture.pickImages(CaptureTarget.AddTo(docId)) },
            bottomActions = listOf(
                DocumentAction("Share", Icons.Outlined.IosShare) { exportPages = LongArray(0) },
                DocumentAction("Get text", Icons.Outlined.TextFields) { nav.navigate(Routes.ocr(docId)) },
            ),
            menuActions = listOf(
                DocumentAction("Reorder pages", Icons.Outlined.SwapVert) { nav.navigate(Routes.reorder(docId)) },
                DocumentAction("Remind me", Icons.Outlined.Event) {
                    busy = "Looking for a date…"
                    scope.launch { reminder = suggestReminderDay(firstPageText()) ?: 0L; busy = null }
                },
                DocumentAction("Add to expenses", Icons.Outlined.ReceiptLong) {
                    busy = "Reading the receipt…"
                    scope.launch {
                        expense = container.expenses.forDocument(docId) ?: run {
                            val g = ExpenseParser.guess(firstPageText().orEmpty())
                            ExpenseEntity(
                                documentId = docId,
                                amountMinor = g.amountMinor ?: 0,
                                currency = g.currency,
                                date = g.date ?: (state.document?.createdAt ?: System.currentTimeMillis()),
                                title = g.title ?: state.document?.name.orEmpty(),
                            )
                        }
                        busy = null
                    }
                },
                DocumentAction("Move to vault", Icons.Outlined.Lock) {
                    val activity = context.findActivity() as? FragmentActivity
                    if (activity == null || !AppLock.available(activity)) {
                        Toast.makeText(context, "Set up a screen lock or fingerprint on your phone to use the vault", Toast.LENGTH_LONG).show()
                    } else confirmVault = true
                },
            ),
            suggestNames = {
                firstPageText()?.let { TextInsights.suggestNames(it, state.document?.createdAt ?: System.currentTimeMillis()) }.orEmpty()
            },
            selectionActions = listOf(
                DocumentAction("Export selected", Icons.Outlined.IosShare) { exportPages = state.selection.toLongArray() },
            ),
        )
        (exportBusy ?: busy)?.let { BusyOverlay(it) }
    }

    if (confirmVault) {
        ConfirmDialog(
            "Move to private vault?",
            "The pages are encrypted on this phone and the document disappears from your library and search. " +
                "Open it again from Private vault with your fingerprint or screen lock. Vault documents aren't included in backups.",
            confirm = "Move to vault",
            onDismiss = { confirmVault = false },
        ) {
            confirmVault = false
            busy = "Encrypting…"
            scope.launch {
                container.vault.moveIn(docId)
                // Drop decoded thumbnails so nothing of the document stays in memory.
                SingletonImageLoader.get(context).memoryCache?.clear()
                busy = null
                Toast.makeText(context, "Moved to the vault", Toast.LENGTH_SHORT).show()
                nav.popBackStack()
            }
        }
    }
    reminder?.let { suggested ->
        val doc = state.document
        ReminderDialog(
            suggestedDay = suggested.takeIf { it > 0 },
            currentDay = doc?.remindAt,
            currentNote = doc?.remindNote,
            onDismiss = { reminder = null },
        ) { day, note ->
            reminder = null
            scope.launch {
                container.reminders.set(docId, day, note)
                Toast.makeText(context, "ScanFire will remind you on " + java.text.DateFormat.getDateInstance().format(java.util.Date(day)), Toast.LENGTH_LONG).show()
            }
        }
    }
    expense?.let { e ->
        ExpenseDialog(
            initial = e,
            onDismiss = { expense = null },
            onDelete = if (e.id != 0L) ({ expense = null; scope.launch { container.expenses.delete(e.id) } }) else null,
        ) { saved ->
            expense = null
            scope.launch { container.expenses.save(saved); Toast.makeText(context, "Added to expenses", Toast.LENGTH_SHORT).show() }
        }
    }

    exportPages?.let { ids ->
        ExportSheet(
            pageCount = if (ids.isEmpty()) state.pages.size else ids.size,
            onDismiss = { exportPages = null },
        ) { format, action, pageSize, searchable ->
            exportPages = null
            exportVm.export(docId, ids.toSet(), format, action, pageSize, searchable && format == ExportFormat.Pdf)
        }
    }
}
