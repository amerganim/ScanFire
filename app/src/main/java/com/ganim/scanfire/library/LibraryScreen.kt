package com.ganim.scanfire.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.CallMerge
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.ViewList
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.ganim.scanfire.data.LibraryRepository
import com.ganim.scanfire.data.SortOrder
import com.ganim.scanfire.data.db.DocumentSummary
import com.ganim.scanfire.data.db.FolderSummary
import com.ganim.scanfire.ui.common.ConfirmDialog
import com.ganim.scanfire.ui.common.FolderPickerDialog
import com.ganim.scanfire.ui.common.TextInputDialog
import com.ganim.scanfire.ui.common.appContainer
import com.ganim.scanfire.ui.common.containerViewModel
import com.ganim.scanfire.ui.common.formatDate

private sealed interface LibraryDialog {
    data object NewFolder : LibraryDialog
    data class RenameFolder(val folder: FolderSummary) : LibraryDialog
    data class MoveFolder(val folder: FolderSummary) : LibraryDialog
    data class DeleteFolder(val folder: FolderSummary) : LibraryDialog
    data class RenameDocument(val doc: DocumentSummary) : LibraryDialog
    data class MoveDocuments(val ids: Set<Long>) : LibraryDialog
    data class DeleteDocuments(val ids: Set<Long>) : LibraryDialog
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onOpenDocument: (Long) -> Unit,
    onScan: (folderId: Long?) -> Unit,
    onImportImages: (folderId: Long?) -> Unit,
    onMerge: (docIds: List<Long>, folderId: Long?) -> Unit = { _, _ -> },
    onOpenTrash: () -> Unit = {},
) {
    val vm = containerViewModel { LibraryViewModel(it.library, it.settings) }
    val state by vm.state.collectAsStateWithLifecycle()
    val allFolders by vm.allFolders.collectAsStateWithLifecycle()
    var dialog by remember { mutableStateOf<LibraryDialog?>(null) }
    val selecting = state.selection.isNotEmpty()

    BackHandler(enabled = selecting || state.searching || state.path.isNotEmpty()) {
        when {
            selecting -> vm.clearSelection()
            state.searching -> vm.setSearching(false)
            else -> vm.goUp()
        }
    }

    Scaffold(
        topBar = {
            when {
                selecting -> SelectionBar(
                    count = state.selection.size,
                    onClose = vm::clearSelection,
                    onSelectAll = vm::selectAll,
                    onMerge = if (state.selection.size >= 2) {
                        { onMerge(state.documents.filter { it.id in state.selection }.map { it.id }, state.currentFolderId); vm.clearSelection() }
                    } else null,
                    onMove = { dialog = LibraryDialog.MoveDocuments(state.selection) },
                    onDelete = { dialog = LibraryDialog.DeleteDocuments(state.selection) },
                )
                state.searching -> SearchBar(state.query, vm::setQuery) { vm.setSearching(false) }
                else -> TopAppBar(
                    title = { Text(state.path.lastOrNull()?.name ?: "ScanFire") },
                    navigationIcon = {
                        if (state.path.isNotEmpty()) {
                            IconButton(onClick = { vm.goUp() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                        }
                    },
                    actions = {
                        IconButton(onClick = { vm.setSearching(true) }) { Icon(Icons.Outlined.Search, "Search") }
                        IconButton(onClick = { dialog = LibraryDialog.NewFolder }) { Icon(Icons.Outlined.CreateNewFolder, "New folder") }
                        IconButton(onClick = onOpenTrash) { Icon(Icons.Outlined.DeleteOutline, "Recycle bin") }
                        SortMenu(state.settings.sortOrder, vm::setSort)
                        IconButton(onClick = { vm.toggleGrid() }) {
                            Icon(
                                if (state.settings.gridView) Icons.Outlined.ViewList else Icons.Outlined.GridView,
                                if (state.settings.gridView) "List view" else "Grid view",
                            )
                        }
                    },
                )
            }
        },
        floatingActionButton = {
            if (!selecting) {
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SmallFloatingActionButton(onClick = { onImportImages(state.currentFolderId) }) {
                        Icon(Icons.Outlined.Image, "Import images")
                    }
                    ExtendedFloatingActionButton(
                        onClick = { onScan(state.currentFolderId) },
                        icon = { Icon(Icons.Outlined.CameraAlt, null) },
                        text = { Text("Scan") },
                    )
                }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (state.path.isNotEmpty() && !state.searching) Breadcrumbs(state, vm::goToDepth)
            when {
                !state.loaded -> Unit
                state.isEmpty -> EmptyLibrary(searching = state.searching, inFolder = state.path.isNotEmpty())
                else -> LibraryGrid(
                    state = state,
                    onFolder = { if (!selecting) vm.openFolder(it) },
                    onFolderAction = { folder, action ->
                        dialog = when (action) {
                            "rename" -> LibraryDialog.RenameFolder(folder)
                            "move" -> LibraryDialog.MoveFolder(folder)
                            else -> LibraryDialog.DeleteFolder(folder)
                        }
                    },
                    onDocument = { doc -> if (selecting) vm.toggleSelect(doc.id) else onOpenDocument(doc.id) },
                    onDocumentLong = { doc -> vm.toggleSelect(doc.id) },
                    onDocumentAction = { doc, action ->
                        dialog = when (action) {
                            "rename" -> LibraryDialog.RenameDocument(doc)
                            "move" -> LibraryDialog.MoveDocuments(setOf(doc.id))
                            else -> LibraryDialog.DeleteDocuments(setOf(doc.id))
                        }
                    },
                )
            }
        }
    }

    when (val d = dialog) {
        null -> Unit
        LibraryDialog.NewFolder -> TextInputDialog("New folder", confirm = "Create", onDismiss = { dialog = null }) {
            vm.createFolder(it); dialog = null
        }
        is LibraryDialog.RenameFolder -> TextInputDialog("Rename folder", d.folder.name, onDismiss = { dialog = null }) {
            vm.renameFolder(d.folder.id, it); dialog = null
        }
        is LibraryDialog.MoveFolder -> FolderPickerDialog(allFolders, excluded = setOf(d.folder.id), onDismiss = { dialog = null }) {
            vm.moveFolder(d.folder.id, it); dialog = null
        }
        is LibraryDialog.DeleteFolder -> ConfirmDialog(
            "Delete folder?",
            "\"${d.folder.name}\" and its subfolders will be deleted. Documents inside move to the Recycle bin for ${LibraryRepository.TRASH_DAYS} days.",
            onDismiss = { dialog = null },
        ) { vm.deleteFolder(d.folder.id); dialog = null }
        is LibraryDialog.RenameDocument -> TextInputDialog("Rename document", d.doc.name, onDismiss = { dialog = null }) {
            vm.renameDocument(d.doc.id, it); dialog = null
        }
        is LibraryDialog.MoveDocuments -> FolderPickerDialog(allFolders, onDismiss = { dialog = null }) {
            vm.moveDocuments(d.ids, it); dialog = null
        }
        is LibraryDialog.DeleteDocuments -> ConfirmDialog(
            if (d.ids.size == 1) "Delete document?" else "Delete ${d.ids.size} documents?",
            "You can restore from the Recycle bin for ${LibraryRepository.TRASH_DAYS} days.",
            confirm = "Move to bin",
            onDismiss = { dialog = null },
        ) { vm.deleteDocuments(d.ids); dialog = null }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionBar(count: Int, onClose: () -> Unit, onSelectAll: () -> Unit, onMerge: (() -> Unit)?, onMove: () -> Unit, onDelete: () -> Unit) {
    TopAppBar(
        title = { Text("$count selected") },
        navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Clear selection") } },
        actions = {
            IconButton(onClick = onSelectAll) { Icon(Icons.Outlined.SelectAll, "Select all") }
            if (onMerge != null) IconButton(onClick = onMerge) { Icon(Icons.Outlined.CallMerge, "Merge into new document") }
            IconButton(onClick = onMove) { Icon(Icons.Outlined.DriveFileMove, "Move") }
            IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, "Delete") }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchBar(query: String, onQuery: (String) -> Unit, onClose: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    TopAppBar(
        navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Close search") } },
        title = {
            TextField(
                value = query,
                onValueChange = onQuery,
                placeholder = { Text("Search names and text") },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        },
        actions = {
            if (query.isNotEmpty()) IconButton(onClick = { onQuery("") }) { Icon(Icons.Filled.Close, "Clear") }
        },
    )
}

@Composable
private fun SortMenu(current: SortOrder, onSort: (SortOrder) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.AutoMirrored.Filled.Sort, "Sort") }
        DropdownMenu(open, onDismissRequest = { open = false }) {
            listOf(SortOrder.Updated to "Last modified", SortOrder.Created to "Date created", SortOrder.Name to "Name").forEach { (order, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = { onSort(order); open = false },
                    trailingIcon = { if (order == current) Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary) },
                )
            }
        }
    }
}

@Composable
private fun Breadcrumbs(state: LibraryState, onDepth: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = { onDepth(0) }) { Text("Documents") }
        state.path.forEachIndexed { i, folder ->
            Icon(Icons.Outlined.ChevronRight, null, Modifier.size(18.dp))
            TextButton(onClick = { onDepth(i + 1) }, enabled = i < state.path.lastIndex) { Text(folder.name) }
        }
    }
}

@Composable
private fun EmptyLibrary(searching: Boolean, inFolder: Boolean) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            if (searching) Icons.Outlined.Search else Icons.Outlined.Description,
            null,
            Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            when {
                searching -> "No matching documents"
                inFolder -> "This folder is empty"
                else -> "No documents yet"
            },
            style = MaterialTheme.typography.titleMedium,
        )
        if (!searching) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Tap Scan to capture a document, or import photos from your gallery.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LibraryGrid(
    state: LibraryState,
    onFolder: (FolderSummary) -> Unit,
    onFolderAction: (FolderSummary, String) -> Unit,
    onDocument: (DocumentSummary) -> Unit,
    onDocumentLong: (DocumentSummary) -> Unit,
    onDocumentAction: (DocumentSummary, String) -> Unit,
) {
    val grid = state.settings.gridView
    LazyVerticalGrid(
        columns = if (grid) GridCells.Adaptive(150.dp) else GridCells.Fixed(1),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 160.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(if (grid) 12.dp else 4.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(state.folders, key = { "f${it.id}" }, span = { GridItemSpan(if (grid) 1 else maxLineSpan) }) { folder ->
            FolderRow(folder, onClick = { onFolder(folder) }, onAction = { onFolderAction(folder, it) })
        }
        items(state.documents, key = { "d${it.id}" }) { doc ->
            val selected = doc.id in state.selection
            if (grid) {
                DocumentCard(doc, selected, { onDocument(doc) }, { onDocumentLong(doc) }, { onDocumentAction(doc, it) })
            } else {
                DocumentRow(doc, selected, { onDocument(doc) }, { onDocumentLong(doc) }, { onDocumentAction(doc, it) })
            }
        }
    }
}

@Composable
private fun ItemMenu(onAction: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Outlined.MoreVert, "More") }
        DropdownMenu(open, onDismissRequest = { open = false }) {
            listOf("rename" to "Rename", "move" to "Move", "delete" to "Delete").forEach { (key, label) ->
                DropdownMenuItem(text = { Text(label) }, onClick = { open = false; onAction(key) })
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderRow(folder: FolderSummary, onClick: () -> Unit, onAction: (String) -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).combinedClickable(onClick = onClick),
    ) {
        Row(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Folder, null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f).padding(start = 16.dp)) {
                Text(folder.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (folder.itemCount == 1) "1 item" else "${folder.itemCount} items",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ItemMenu(onAction)
        }
    }
}

@Composable
private fun Thumbnail(doc: DocumentSummary, modifier: Modifier) {
    val files = appContainer().files
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        if (doc.coverFile != null) {
            AsyncImage(
                model = files.file(doc.id, doc.coverFile),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(Icons.Outlined.Description, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun pagesLabel(n: Int) = if (n == 1) "1 page" else "$n pages"

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DocumentRow(doc: DocumentSummary, selected: Boolean, onClick: () -> Unit, onLong: () -> Unit, onAction: (String) -> Unit) {
    val context = LocalContext.current
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).combinedClickable(onClick = onClick, onLongClick = onLong),
    ) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box {
                Thumbnail(doc, Modifier.width(56.dp).aspectRatio(0.75f).clip(RoundedCornerShape(6.dp)))
                if (selected) SelectedBadge(Modifier.align(Alignment.TopEnd))
            }
            Column(Modifier.weight(1f).padding(start = 16.dp)) {
                Text(doc.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Text(
                    "${formatDate(context, doc.updatedAt)}  ·  ${pagesLabel(doc.pageCount)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ItemMenu(onAction)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DocumentCard(doc: DocumentSummary, selected: Boolean, onClick: () -> Unit, onLong: () -> Unit, onAction: (String) -> Unit) {
    val context = LocalContext.current
    Column(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .then(if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)) else Modifier)
            .combinedClickable(onClick = onClick, onLongClick = onLong),
    ) {
        Box {
            Thumbnail(doc, Modifier.fillMaxWidth().aspectRatio(0.75f).clip(RoundedCornerShape(12.dp)))
            if (selected) SelectedBadge(Modifier.align(Alignment.TopEnd).padding(6.dp))
            Surface(
                color = Color.Black.copy(alpha = 0.55f),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.align(Alignment.BottomStart).padding(6.dp),
            ) {
                Text(pagesLabel(doc.pageCount), color = Color.White, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(start = 4.dp, top = 4.dp)) {
                Text(doc.name, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(formatDate(context, doc.updatedAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            ItemMenu(onAction)
        }
    }
}

@Composable
private fun SelectedBadge(modifier: Modifier) {
    Icon(
        Icons.Filled.CheckCircle,
        "Selected",
        tint = MaterialTheme.colorScheme.primary,
        modifier = modifier.background(MaterialTheme.colorScheme.surface, RoundedCornerShape(50)),
    )
}
