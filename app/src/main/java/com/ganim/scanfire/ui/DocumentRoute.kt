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
import com.ganim.scanfire.export.ExportSheet
import com.ganim.scanfire.export.ExportViewModel
import com.ganim.scanfire.ui.common.containerViewModel

/** The document screen plus everything it launches: capture, export, and per-phase tools. */
@Composable
fun DocumentRoute(docId: Long, nav: NavHostController, capture: Capture) {
    val vm = containerViewModel(key = "doc-$docId") { DocumentViewModel(docId, it.library, it.pages) }
    val exportVm = containerViewModel(key = "export-$docId") { ExportViewModel(it.library, it.pages, it.settings, it.exporter) }
    val state by vm.state.collectAsStateWithLifecycle()
    val exportBusy by exportVm.busy.collectAsStateWithLifecycle()
    var exportPages by rememberSaveable { mutableStateOf<LongArray?>(null) }
    ExportEffects(exportVm)

    Box(Modifier.fillMaxSize()) {
        DocumentScreen(
            vm = vm,
            onBack = { nav.popBackStack() },
            onOpenPage = { nav.navigate(Routes.page(docId, it)) },
            onScanMore = { capture.scan(CaptureTarget.AddTo(docId)) },
            onImportMore = { capture.pickImages(CaptureTarget.AddTo(docId)) },
            bottomActions = listOf(
                DocumentAction("Text", Icons.Outlined.TextFields) { nav.navigate(Routes.ocr(docId)) },
                DocumentAction("Export", Icons.Outlined.IosShare) { exportPages = LongArray(0) },
            ),
            menuActions = listOf(
                DocumentAction("Reorder pages", Icons.Outlined.SwapVert) { nav.navigate(Routes.reorder(docId)) },
            ),
            selectionActions = listOf(
                DocumentAction("Export selected", Icons.Outlined.IosShare) { exportPages = state.selection.toLongArray() },
            ),
        )
        if (exportBusy) BusyOverlay("Preparing export…")
    }

    exportPages?.let { ids ->
        ExportSheet(
            pageCount = if (ids.isEmpty()) state.pages.size else ids.size,
            onDismiss = { exportPages = null },
        ) { format, action, pageSize ->
            exportPages = null
            exportVm.export(docId, ids.toSet(), format, action, pageSize)
        }
    }
}
