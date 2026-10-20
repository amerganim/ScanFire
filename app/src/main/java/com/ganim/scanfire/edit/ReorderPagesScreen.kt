package com.ganim.scanfire.edit

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.ganim.scanfire.document.DocumentViewModel
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState

/** Long-press a page and drag it to its new place; Save writes the order. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReorderPagesScreen(vm: DocumentViewModel, onClose: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    var order by remember { mutableStateOf(state.pages) }
    var touched by remember { mutableStateOf(false) }
    LaunchedEffect(state.pages) { if (!touched) order = state.pages }

    val gridState = rememberLazyGridState()
    val reorderState = rememberReorderableLazyGridState(gridState) { from, to ->
        touched = true
        order = order.toMutableList().apply { add(to.index, removeAt(from.index)) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reorder pages") },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Cancel") } },
                actions = {
                    IconButton(enabled = touched, onClick = { vm.reorder(order.map { it.id }); onClose() }) {
                        Icon(Icons.Filled.Check, "Save order")
                    }
                },
            )
        },
    ) { padding ->
        Text(
            "Long-press a page, then drag it.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(padding).padding(horizontal = 16.dp),
        )
        LazyVerticalGrid(
            columns = GridCells.Adaptive(110.dp),
            state = gridState,
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = padding.calculateTopPadding() + 32.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            itemsIndexed(order, key = { _, p -> p.id }) { index, page ->
                ReorderableItem(reorderState, key = page.id) { dragging ->
                    val elevation by animateDpAsState(if (dragging) 12.dp else 0.dp, label = "lift")
                    Box(
                        Modifier
                            .longPressDraggableHandle()
                            .fillMaxWidth()
                            .aspectRatio(0.72f)
                            .shadow(elevation, RoundedCornerShape(8.dp))
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        AsyncImage(vm.pageRepo.imageFile(page), "Page ${index + 1}", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(4.dp))
                        Surface(
                            color = Color.Black.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.align(Alignment.BottomCenter).padding(6.dp),
                        ) {
                            Text("${index + 1}", color = Color.White, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                        }
                    }
                }
            }
        }
    }
}
