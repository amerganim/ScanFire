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
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.ui.platform.LocalContext
import com.ganim.scanfire.edit.SignatureScreen
import com.ganim.scanfire.tools.ToolsScreen
import com.ganim.scanfire.ui.common.appContainer
import androidx.compose.material.icons.outlined.Tune
import com.ganim.scanfire.ocr.OcrScreen
import com.ganim.scanfire.ocr.OcrViewModel
import com.ganim.scanfire.document.PageAction
import com.ganim.scanfire.edit.PageEditScreen
import com.ganim.scanfire.edit.PageEditViewModel
import com.ganim.scanfire.edit.ReorderPagesScreen
import com.ganim.scanfire.document.DocumentViewModel
import com.ganim.scanfire.document.PageViewerScreen
import com.ganim.scanfire.library.LibraryScreen
import com.ganim.scanfire.scan.CaptureViewModel
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
    const val SIGN = "sign/{pageId}"
    fun sign(pageId: Long) = "sign/$pageId"
    const val OCR = "ocr/{docId}/{pageId}"
    /** [pageId] 0 means every page of the document. */
    fun ocr(docId: Long, pageId: Long = 0) = "ocr/$docId/$pageId"
}

/** Where captured images go: a new document in a folder, or the end of an existing document. */
sealed interface CaptureTarget {
    data class NewDocument(val folderId: Long?) : CaptureTarget
    data class AddTo(val docId: Long) : CaptureTarget
}

/** Capture entry points shared by every screen. */
class Capture(val scan: (CaptureTarget) -> Unit, val pickImages: (CaptureTarget) -> Unit)

@Composable
fun ScanFireRoot() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination
    val onTab = Tab.entries.any { tab -> current?.hierarchy?.any { it.route == tab.route } == true }

    val captureVm = containerViewModel { CaptureViewModel(it.library, it.pages, it.settings, it.pdfTools) }
    val busy by captureVm.busy.collectAsStateWithLifecycle()
    var target by remember { mutableStateOf<CaptureTarget>(CaptureTarget.NewDocument(null)) }

    fun deliver(uris: List<android.net.Uri>, fromScanner: Boolean) {
        when (val t = target) {
            is CaptureTarget.NewDocument -> captureVm.newDocument(t.folderId, uris, fromScanner) { nav.navigate(Routes.doc(it)) }
            is CaptureTarget.AddTo -> captureVm.addToDocument(t.docId, uris, fromScanner)
        }
    }

    val scanner = rememberDocumentScanner { deliver(it, fromScanner = true) }
    val picker = rememberImagePicker { deliver(it, fromScanner = false) }
    val capture = remember(scanner, picker) {
        Capture(scan = { target = it; scanner() }, pickImages = { target = it; picker() })
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
                    )
                }
                composable(Tab.Tools.route) {
                    val context = LocalContext.current
                    ToolsScreen(
                        scanTools = emptyList(),
                        onImportPdf = { file, name, password ->
                            captureVm.newDocumentFromPdf(
                                null, file, name, password,
                                onDone = { nav.navigate(Routes.doc(it)) },
                                onError = { Toast.makeText(context, "Couldn't import: ${it.message}", Toast.LENGTH_LONG).show() },
                            )
                        },
                    )
                }
                composable(Tab.Settings.route) { Placeholder("Settings") }

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
                        ),
                    )
                }
                composable(Routes.SIGN, arguments = listOf(navArgument("pageId") { type = NavType.LongType })) { entry ->
                    SignatureScreen(entry.arguments!!.getLong("pageId"), appContainer().pages, onClose = { nav.popBackStack() })
                }
                composable(
                    Routes.OCR,
                    arguments = listOf(navArgument("docId") { type = NavType.LongType }, navArgument("pageId") { type = NavType.LongType }),
                ) { entry ->
                    val docId = entry.arguments!!.getLong("docId")
                    val pageId = entry.arguments!!.getLong("pageId")
                    val vm = containerViewModel(key = "ocr-$docId-$pageId") {
                        OcrViewModel(docId, pageId, it.library, it.pages, it.settings, it.ocr, it.translator, it.files)
                    }
                    OcrScreen(vm, onBack = { nav.popBackStack() })
                }
            }
        }
        if (busy) BusyOverlay("Adding pages…")
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

@Composable
private fun Placeholder(name: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(name) }
}
