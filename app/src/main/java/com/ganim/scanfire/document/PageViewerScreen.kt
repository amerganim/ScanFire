package com.ganim.scanfire.document

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.ganim.scanfire.data.db.PageEntity

/** Page-scoped actions (edit, OCR...) supplied by later phases. */
data class PageAction(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val onClick: (PageEntity) -> Unit)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PageViewerScreen(
    vm: DocumentViewModel,
    startIndex: Int,
    onBack: () -> Unit,
    actions: List<PageAction> = emptyList(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val pages = state.pages
    val pager = rememberPagerState(initialPage = startIndex) { pages.size }

    LaunchedEffect(state.loaded, pages.size) { if (state.loaded && pages.isEmpty()) onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (pages.isEmpty()) "" else "${pager.currentPage + 1} / ${pages.size}") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
        bottomBar = {
            if (actions.isNotEmpty() && pages.isNotEmpty()) {
                BottomAppBar {
                    actions.forEach { action ->
                        Column(
                            Modifier.weight(1f).clip(MaterialTheme.shapes.medium)
                                .clickable { pages.getOrNull(pager.currentPage)?.let(action.onClick) }
                                .padding(vertical = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(action.icon, null)
                            Text(action.label, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        },
    ) { padding ->
        HorizontalPager(pager, key = { pages.getOrNull(it)?.id ?: it }, modifier = Modifier.padding(padding).fillMaxSize()) { index ->
            pages.getOrNull(index)?.let { ZoomableImage(vm.pageRepo.imageFile(it)) }
        }
    }
}

@Composable
fun ZoomableImage(file: java.io.File, modifier: Modifier = Modifier) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    Box(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = {
                    if (scale > 1f) { scale = 1f; offset = Offset.Zero } else scale = 2.5f
                })
            }
            .pointerInput(Unit) {
                // Only consumes gestures while zoomed (or pinching), so the pager can still swipe at 1x.
                awaitEachGestureZoom(
                    isZoomed = { scale > 1.01f },
                    onTransform = { pan, zoom ->
                        scale = (scale * zoom).coerceIn(1f, 6f)
                        offset = if (scale == 1f) Offset.Zero else offset + pan
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = file,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().padding(8.dp).graphicsLayer {
                scaleX = scale; scaleY = scale
                translationX = offset.x; translationY = offset.y
            },
        )
    }
}

private suspend fun PointerInputScope.awaitEachGestureZoom(
    isZoomed: () -> Boolean,
    onTransform: (Offset, Float) -> Unit,
) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        do {
            val event = awaitPointerEvent()
            val pointers = event.changes.count { it.pressed }
            if (pointers >= 2 || isZoomed()) {
                onTransform(event.calculatePan(), event.calculateZoom())
                event.changes.forEach { if (it.positionChanged()) it.consume() }
            }
        } while (event.changes.any { it.pressed })
    }
}
