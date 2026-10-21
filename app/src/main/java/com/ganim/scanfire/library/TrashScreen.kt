package com.ganim.scanfire.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.ganim.scanfire.data.LibraryRepository
import com.ganim.scanfire.data.db.DocumentSummary
import com.ganim.scanfire.ui.common.ConfirmDialog
import com.ganim.scanfire.ui.common.appContainer
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(onBack: () -> Unit) {
    val container = appContainer()
    val items by container.library.trash().collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    var confirm by remember { mutableStateOf<List<Long>?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recycle bin") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    if (items.isNotEmpty()) TextButton(onClick = { confirm = items.map { it.id } }) { Text("Empty") }
                },
            )
        },
    ) { padding ->
        if (items.isEmpty()) {
            Column(Modifier.padding(padding).fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Outlined.DeleteOutline, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(16.dp))
                Text("The recycle bin is empty", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Deleted documents stay here for ${LibraryRepository.TRASH_DAYS} days.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                items(items, key = { it.id }) { doc ->
                    TrashRow(
                        doc,
                        onRestore = { scope.launch { container.library.restore(listOf(doc.id)) } },
                        onDelete = { confirm = listOf(doc.id) },
                    )
                }
            }
        }
    }

    confirm?.let { ids ->
        ConfirmDialog(
            if (ids.size == 1) "Delete forever?" else "Empty the recycle bin?",
            if (ids.size == 1) "This document will be gone for good." else "All ${ids.size} documents will be gone for good.",
            confirm = "Delete forever",
            onDismiss = { confirm = null },
        ) {
            confirm = null
            scope.launch { container.library.deleteForever(ids) }
        }
    }
}

@Composable
private fun TrashRow(doc: DocumentSummary, onRestore: () -> Unit, onDelete: () -> Unit) {
    val files = appContainer().files
    val daysLeft = doc.deletedAt?.let { LibraryRepository.TRASH_DAYS - ((System.currentTimeMillis() - it) / 86_400_000L).toInt() }
    ListItem(
        leadingContent = {
            AsyncImage(
                model = doc.coverFile?.let { files.file(doc.id, it) },
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.width(44.dp).aspectRatio(0.75f).clip(RoundedCornerShape(6.dp)),
            )
        },
        headlineContent = { Text(doc.name, maxLines = 2) },
        supportingContent = {
            Text("${if (doc.pageCount == 1) "1 page" else "${doc.pageCount} pages"} · ${if (daysLeft != null && daysLeft <= 1) "deleted tomorrow" else "$daysLeft days left"}")
        },
        trailingContent = {
            Row {
                IconButton(onClick = onRestore) { Icon(Icons.Outlined.RestoreFromTrash, "Restore ${doc.name}") }
                IconButton(onClick = onDelete) { Icon(Icons.Outlined.DeleteForever, "Delete ${doc.name} forever") }
            }
        },
    )
}
