package com.ganim.scanfire.document

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.ganim.scanfire.data.db.PageEntity
import com.ganim.scanfire.ui.common.BarItem
import com.ganim.scanfire.ui.common.ConfirmDialog
import com.ganim.scanfire.ui.common.InkActionBar
import com.ganim.scanfire.ui.common.formatDate
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.material.icons.outlined.DocumentScanner
import com.ganim.scanfire.ui.common.TextInputDialog

/** Extra actions contributed by later phases (export, OCR, PDF tools) without this screen knowing them. */
data class DocumentAction(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val onClick: () -> Unit)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentScreen(
    vm: DocumentViewModel,
    onBack: () -> Unit,
    onOpenPage: (index: Int) -> Unit,
    onScanMore: () -> Unit,
    onImportMore: () -> Unit,
    bottomActions: List<DocumentAction> = emptyList(),
    menuActions: List<DocumentAction> = emptyList(),
    selectionActions: List<DocumentAction> = emptyList(),
    /** Name ideas from the document's content (see TextInsights.suggestNames). */
    suggestNames: suspend () -> List<String> = { emptyList() },
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var renaming by remember { mutableStateOf(false) }
    var confirmDeleteDoc by remember { mutableStateOf(false) }
    var confirmDeletePages by remember { mutableStateOf(false) }
    val selecting = state.selection.isNotEmpty()

    // The document was deleted elsewhere, or its last page went: leave the screen.
    LaunchedEffect(state.loaded, state.document) { if (state.loaded && state.document == null) onBack() }
    BackHandler(enabled = selecting) { vm.clearSelection() }

    Scaffold(
        topBar = {
            if (selecting) {
                TopAppBar(
                    title = { Text("${state.selection.size} selected") },
                    navigationIcon = { IconButton(onClick = vm::clearSelection) { Icon(Icons.Filled.Close, "Clear selection") } },
                    actions = {
                        IconButton(onClick = vm::selectAll) { Icon(Icons.Outlined.SelectAll, "Select all") }
                        selectionActions.forEach { IconButton(onClick = it.onClick) { Icon(it.icon, it.label) } }
                        IconButton(onClick = { confirmDeletePages = true }) { Icon(Icons.Outlined.Delete, "Delete pages") }
                    },
                )
            } else {
                TopAppBar(
                    title = {},
                    colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                    actions = {
                        IconButton(onClick = { renaming = true }) { Icon(Icons.Outlined.Edit, "Rename") }
                        var open by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { open = true }) { Icon(Icons.Outlined.MoreVert, "More") }
                            DropdownMenu(open, onDismissRequest = { open = false }) {
                                menuActions.forEach { action ->
                                    DropdownMenuItem(
                                        text = { Text(action.label) },
                                        leadingIcon = { Icon(action.icon, null) },
                                        onClick = { open = false; action.onClick() },
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("Delete document") },
                                    leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                                    onClick = { open = false; confirmDeleteDoc = true },
                                )
                            }
                        }
                    },
                )
            }
        },
        bottomBar = {
            if (!selecting) {
                InkActionBar(
                    bottomActions.map { BarItem(it.label, it.icon, onClick = it.onClick) } +
                        BarItem("Scan more", Icons.Outlined.DocumentScanner, highlighted = true, onClick = onScanMore),
                )
            }
        },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(104.dp),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            if (!selecting) item(span = { GridItemSpan(maxLineSpan) }) {
                val doc = state.document
                val context = androidx.compose.ui.platform.LocalContext.current
                Column(Modifier.padding(bottom = 6.dp)) {
                    Text(
                        doc?.name.orEmpty(),
                        style = MaterialTheme.typography.headlineSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).combinedClickableCompat { renaming = true },
                    )
                    val count = state.pages.size
                    Text(
                        (if (count == 1) "1 page" else "$count pages") + (doc?.let { " · " + formatDate(context, it.updatedAt) } ?: ""),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            itemsIndexed(state.pages, key = { _, p -> p.id }) { index, page ->
                PageTile(
                    page = page,
                    number = index + 1,
                    selected = page.id in state.selection,
                    file = vm.pageRepo.imageFile(page),
                    onClick = { if (selecting) vm.toggleSelect(page.id) else onOpenPage(index) },
                    onLongClick = { vm.toggleSelect(page.id) },
                )
            }
            if (!selecting) item {
                Surface(
                    onClick = onImportMore,
                    shape = MaterialTheme.shapes.medium,
                    color = Color.Transparent,
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth().aspectRatio(0.72f),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Icon(Icons.Outlined.AddPhotoAlternate, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Add photos", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }
        }
    }

    if (renaming) {
        val ideas by produceState<List<String>?>(null) { value = runCatching { suggestNames() }.getOrDefault(emptyList()) }
        TextInputDialog("Rename document", state.document?.name.orEmpty(), suggestions = ideas, onDismiss = { renaming = false }) {
            vm.rename(it); renaming = false
        }
    }
    if (confirmDeleteDoc) {
        ConfirmDialog("Delete document?", "It moves to the Recycle bin, where you can restore it for 30 days.", confirm = "Move to bin", onDismiss = { confirmDeleteDoc = false }) {
            confirmDeleteDoc = false
            vm.deleteDocument(onBack)
        }
    }
    if (confirmDeletePages) {
        val all = state.selection.size == state.pages.size
        ConfirmDialog(
            if (state.selection.size == 1) "Delete page?" else "Delete ${state.selection.size} pages?",
            if (all) "Every page is selected, so the whole document moves to the Recycle bin." else "Deleted pages can't be brought back.",
            onDismiss = { confirmDeletePages = false },
        ) {
            confirmDeletePages = false
            if (all) vm.deleteDocument(onBack) else vm.deletePages(state.selection)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.combinedClickableCompat(onClick: () -> Unit) = combinedClickable(onClick = onClick)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PageTile(page: PageEntity, number: Int, selected: Boolean, file: java.io.File, onClick: () -> Unit, onLongClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(0.72f)
            .shadow(1.dp, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)) else Modifier)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        AsyncImage(model = file, contentDescription = "Page $number", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(4.dp))
        Surface(
            color = Color.Black.copy(alpha = 0.6f),
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier.align(Alignment.BottomCenter).padding(6.dp),
        ) {
            Text("$number", color = Color.White, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
        }
        if (selected) {
            Icon(
                Icons.Filled.CheckCircle,
                "Selected",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).background(MaterialTheme.colorScheme.surface, RoundedCornerShape(50)),
            )
        }
    }
}
