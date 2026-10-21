package com.ganim.scanfire.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import android.widget.Toast
import androidx.compose.runtime.LaunchedEffect
import com.ganim.scanfire.Incoming
import com.ganim.scanfire.pdf.PdfPasswordRequired
import com.ganim.scanfire.settings.SettingsScreen
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.HideSource
import com.ganim.scanfire.privacy.RedactScreen
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.ui.platform.LocalContext
import com.ganim.scanfire.edit.SignatureScreen
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.QrCodeScanner
import com.ganim.scanfire.qr.CodesScreen
import com.ganim.scanfire.tools.ToolTile
import com.ganim.scanfire.tools.ToolsScreen
import com.ganim.scanfire.ui.common.appContainer
import androidx.compose.material.icons.outlined.Tune
import com.ganim.scanfire.ocr.OcrLanguagesScreen
import com.ganim.scanfire.ocr.OcrScreen
import com.ganim.scanfire.ocr.OcrViewModel
import com.ganim.scanfire.document.PageAction
import com.ganim.scanfire.edit.PageEditScreen
import com.ganim.scanfire.edit.PageEditViewModel
import com.ganim.scanfire.edit.ReorderPagesScreen
import com.ganim.scanfire.document.DocumentViewModel
import com.ganim.scanfire.document.PageViewerScreen
import com.ganim.scanfire.library.LibraryScreen
import com.ganim.scanfire.library.TrashScreen
import com.ganim.scanfire.scan.CaptureTarget
import com.ganim.scanfire.scan.CaptureViewModel
import com.ganim.scanfire.scan.ImportReviewScreen
import com.ganim.scanfire.scan.rememberDocumentScanner
import com.ganim.scanfire.scan.rememberImagePicker
import com.ganim.scanfire.ui.common.containerViewModel

enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    Documents("documents", "Documents", Icons.Outlined.Description),
    Tools("tools", "Tools", Icons.Outlined.GridView),
    Settings("settings", "Settings", Icons.Outlined.Settings),
}

object Routes {
    const val DOC = "doc/{docId}"
    const val PAGE = "doc/{docId}/page/{index}"
    fun doc(id: Long) = "doc/$id"
    fun page(docId: Long, index: Int) = "doc/$docId/page/$index"
    const val EDIT = "edit/{pageId}"
    fun edit(pageId: Long) = "edit/$pageId"
    const val REORDER = "doc/{docId}/reorder"
    fun reorder(docId: Long) = "doc/$docId/reorder"
    const val CODES = "codes?scan={scan}"
    fun codes(scan: Boolean) = "codes?scan=$scan"
    const val IMPORT_REVIEW = "import-review"
    const val OCR_LANGUAGES = "ocr-languages"
    const val TRASH = "trash"
    const val REDACT = "redact/{pageId}"
    fun redact(pageId: Long) = "redact/$pageId"
    const val SIGN = "sign/{pageId}"
    fun sign(pageId: Long) = "sign/$pageId"
    const val OCR = "ocr/{docId}/{pageId}"
    /** [pageId] 0 means every page of the document. */
    fun ocr(docId: Long, pageId: Long = 0) = "ocr/$docId/$pageId"
}

/** Capture entry points shared by every screen. */
class Capture(val scan: (CaptureTarget) -> Unit, val pickImages: (CaptureTarget) -> Unit)

@Composable
fun ScanFireRoot(incoming: MutableStateFlow<Incoming?> = MutableStateFlow(null)) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination
    val onTab = Tab.entries.any { tab -> current?.hierarchy?.any { it.route == tab.route } == true }

    val captureVm = containerViewModel { CaptureViewModel(it.context, it.library, it.pages, it.settings, it.pdfTools) }
    val busy by captureVm.busy.collectAsStateWithLifecycle()
    var target by remember { mutableStateOf<CaptureTarget>(CaptureTarget.NewDocument(null)) }

    // Scanner pages are already cropped and enhanced; photos go through page finding and review first.
    val scanner = rememberDocumentScanner { uris ->
        when (val t = target) {
            is CaptureTarget.NewDocument -> captureVm.newDocument(t.folderId, uris, fromScanner = true) { nav.navigate(Routes.doc(it)) }
            is CaptureTarget.AddTo -> captureVm.addToDocument(t.docId, uris, fromScanner = true)
        }
    }
    val picker = rememberImagePicker { uris -> captureVm.prepareImport(target, uris) { nav.navigate(Routes.IMPORT_REVIEW) } }
    // Special-purpose scanners for the Tools tab.
    val idScanner = rememberDocumentScanner(pageLimit = 2) { uris -> captureVm.idCard(uris) { nav.navigate(Routes.doc(it)) } }
    val bookScanner = rememberDocumentScanner { uris -> captureVm.book(uris) { nav.navigate(Routes.doc(it)) } }
    val textScanner = rememberDocumentScanner(pageLimit = 1) { uris ->
        captureVm.newDocument(null, uris, fromScanner = true) { nav.navigate(Routes.ocr(it)) }
    }
    val scanTools = remember(idScanner, bookScanner, textScanner) {
        listOf(
            ToolTile("QR & barcodes", "Scan any QR code or barcode; history stays on the phone", Icons.Outlined.QrCodeScanner) { nav.navigate(Routes.codes(scan = true)) },
            ToolTile("ID card", "Scan the front and back; both go on one page", Icons.Outlined.Badge, idScanner),
            ToolTile("Extract text", "Scan one page and get its text right away", Icons.Outlined.DocumentScanner, textScanner),
            ToolTile("Book", "Scan open-book spreads; each is split into two pages", Icons.AutoMirrored.Outlined.MenuBook, bookScanner),
        )
    }

    val capture = remember(scanner, picker) {
        Capture(scan = { target = it; scanner() }, pickImages = { target = it; picker() })
    }

    val context = LocalContext.current
    LaunchedEffect(captureVm) { captureVm.messages.collect { Toast.makeText(context, it, Toast.LENGTH_LONG).show() } }
    val pending by incoming.collectAsStateWithLifecycle()
    LaunchedEffect(pending) {
        val item = pending ?: return@LaunchedEffect
        incoming.value = null
        when (item) {
            is Incoming.Images -> captureVm.prepareImport(CaptureTarget.NewDocument(null), item.uris) { nav.navigate(Routes.IMPORT_REVIEW) }
            is Incoming.Pdf -> captureVm.importPdf(
                item.uri,
                onDone = { nav.navigate(Routes.doc(it)) },
                onError = {
                    val message = if (it is PdfPasswordRequired) "This PDF has a password. Open it from Tools → Import PDF." else "Couldn't open the PDF: ${it.message}"
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                },
            )
            Incoming.Scan -> capture.scan(CaptureTarget.NewDocument(null))
            Incoming.ScanCode -> nav.navigate(Routes.codes(scan = true))
        }
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            bottomBar = { if (onTab) TabBar(nav, current) },
        ) { padding ->
            NavHost(nav, startDestination = Tab.Documents.route, modifier = Modifier.padding(bottom = padding.calculateBottomPadding())) {
                composable(Tab.Documents.route) {
                    LibraryScreen(
                        onOpenDocument = { nav.navigate(Routes.doc(it)) },
                        onScan = { capture.scan(CaptureTarget.NewDocument(it)) },
                        onImportImages = { capture.pickImages(CaptureTarget.NewDocument(it)) },
                        onMerge = { ids, folder -> captureVm.mergeDocuments(ids, folder) { nav.navigate(Routes.doc(it)) } },
                        onOpenTrash = { nav.navigate(Routes.TRASH) },
                    )
                }
                composable(Tab.Tools.route) {
                    ToolsScreen(
                        scanTools = scanTools,
                        onImportPdf = { file, name, password ->
                            captureVm.newDocumentFromPdf(
                                null, file, name, password,
                                onDone = { nav.navigate(Routes.doc(it)) },
                                onError = { Toast.makeText(context, "Couldn't import: ${it.message}", Toast.LENGTH_LONG).show() },
                            )
                        },
                    )
                }
                composable(Tab.Settings.route) { SettingsScreen(onManageLanguages = { nav.navigate(Routes.OCR_LANGUAGES) }) }

                composable(Routes.DOC, arguments = listOf(navArgument("docId") { type = NavType.LongType })) { entry ->
                    DocumentRoute(entry.arguments!!.getLong("docId"), nav, capture)
                }
                composable(Routes.REORDER, arguments = listOf(navArgument("docId") { type = NavType.LongType })) { entry ->
                    val docId = entry.arguments!!.getLong("docId")
                    val vm = containerViewModel(key = "doc-$docId") { DocumentViewModel(docId, it.library, it.pages) }
                    ReorderPagesScreen(vm, onClose = { nav.popBackStack() })
                }
                composable(Routes.EDIT, arguments = listOf(navArgument("pageId") { type = NavType.LongType })) { entry ->
                    val pageId = entry.arguments!!.getLong("pageId")
                    val vm = containerViewModel(key = "edit-$pageId") { PageEditViewModel(pageId, it.pages) }
                    PageEditScreen(vm, onClose = { nav.popBackStack() })
                }
                composable(
                    Routes.PAGE,
                    arguments = listOf(navArgument("docId") { type = NavType.LongType }, navArgument("index") { type = NavType.IntType }),
                ) { entry ->
                    val docId = entry.arguments!!.getLong("docId")
                    val vm = containerViewModel(key = "doc-$docId") { DocumentViewModel(docId, it.library, it.pages) }
                    PageViewerScreen(
                        vm = vm,
                        startIndex = entry.arguments!!.getInt("index"),
                        onBack = { nav.popBackStack() },
                        actions = listOf(
                            PageAction("Edit", Icons.Outlined.Tune) { nav.navigate(Routes.edit(it.id)) },
                            PageAction("Text", Icons.Outlined.TextFields) { nav.navigate(Routes.ocr(docId, it.id)) },
                            PageAction("Sign", Icons.Outlined.Draw) { nav.navigate(Routes.sign(it.id)) },
                            PageAction("Redact", Icons.Outlined.HideSource) { nav.navigate(Routes.redact(it.id)) },
                        ),
                    )
                }
                composable(Routes.TRASH) { TrashScreen(onBack = { nav.popBackStack() }) }
                composable(Routes.OCR_LANGUAGES) { OcrLanguagesScreen(onBack = { nav.popBackStack() }) }
                composable(Routes.IMPORT_REVIEW) {
                    ImportReviewScreen(captureVm, onNewDocument = { nav.navigate(Routes.doc(it)) }, onLeave = { nav.popBackStack() })
                }
                composable(Routes.CODES, arguments = listOf(navArgument("scan") { type = NavType.BoolType; defaultValue = false })) { entry ->
                    CodesScreen(onBack = { nav.popBackStack() }, scanOnOpen = entry.arguments!!.getBoolean("scan"))
                }
                composable(Routes.SIGN,arguments = listOf(navArgument("pageId") { type = NavType.LongType })) { entry ->
                    SignatureScreen(entry.arguments!!.getLong("pageId"), appContainer().pages, onClose = { nav.popBackStack() })
                }
                composable(
                    Routes.OCR,
                    arguments = listOf(navArgument("docId") { type = NavType.LongType }, navArgument("pageId") { type = NavType.LongType }),
                ) { entry ->
                    val docId = entry.arguments!!.getLong("docId")
                    val pageId = entry.arguments!!.getLong("pageId")
                    val vm = containerViewModel(key = "ocr-$docId-$pageId") {
                        OcrViewModel(docId, pageId, it.library, it.pages, it.settings, it.ocr, it.translator, it.files, it.tessData)
                    }
                    OcrScreen(vm, onBack = { nav.popBackStack() }, onManageLanguages = { nav.navigate(Routes.OCR_LANGUAGES) })
                }
            }
        }
        busy?.let { BusyOverlay(it) }
    }
}

@Composable
private fun TabBar(nav: NavHostController, current: androidx.navigation.NavDestination?) {
    NavigationBar {
        Tab.entries.forEach { tab ->
            NavigationBarItem(
                selected = current?.hierarchy?.any { it.route == tab.route } == true,
                onClick = {
                    nav.navigate(tab.route) {
                        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(tab.icon, contentDescription = null) },
                label = { Text(tab.label) },
            )
        }
    }
}

@Composable
fun BusyOverlay(message: String) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Text(message, color = Color.White, modifier = Modifier.padding(top = 16.dp))
        }
    }
}
