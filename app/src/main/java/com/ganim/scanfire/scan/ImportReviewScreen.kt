package com.ganim.scanfire.scan

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.CropFree
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ganim.scanfire.imaging.Quad
import kotlin.math.hypot

/**
 * Shows each imported photo with the page ScanFire found outlined. Drag a corner to fix it;
 * "Whole photo" keeps everything. Adding straightens each page and applies the default filter.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportReviewScreen(vm: CaptureViewModel, onNewDocument: (Long) -> Unit, onLeave: () -> Unit) {
    val review by vm.review.collectAsStateWithLifecycle()
    val items = review?.items.orEmpty()
    val pager = rememberPagerState { items.size }

    // The review was finished or cancelled (or the process restarted): leave.
    LaunchedEffect(review) { if (review == null) onLeave() }
    BackHandler { vm.cancelImport() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (items.size > 1) "Check pages  ${pager.currentPage + 1} / ${items.size}" else "Check page") },
                navigationIcon = { IconButton(onClick = vm::cancelImport) { Icon(Icons.Filled.Close, "Cancel") } },
                actions = {
                    TextButton(enabled = items.isNotEmpty(), onClick = { vm.finishImport(onNewDocument) }) { Text(if (items.size == 1) "Add page" else "Add ${items.size} pages") }
                },
            )
        },
        bottomBar = {
            val item = items.getOrNull(pager.currentPage)
            if (item != null) {
                Surface(tonalElevation = 3.dp) {
                    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            if (item.detected == null) "No page edge found. Drag the corners onto the page, or keep the whole photo."
                            else "Drag the corners if the outline is off.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = item.detected != null && item.quad == item.detected,
                                enabled = item.detected != null,
                                onClick = { item.detected?.let { vm.setQuad(pager.currentPage, it) } },
                                label = { Text("Auto") },
                                leadingIcon = { Icon(Icons.Outlined.AutoFixHigh, null) },
                            )
                            FilterChip(
                                selected = item.quad.isFull,
                                onClick = { vm.setQuad(pager.currentPage, Quad.FULL) },
                                label = { Text("Whole photo") },
                                leadingIcon = { Icon(Icons.Outlined.CropFree, null) },
                            )
                            if (items.size > 1) {
                                TextButton(onClick = { vm.setAllQuads(full = item.quad.isFull) }) {
                                    Text(if (item.quad.isFull) "Whole photo for all" else "Auto for all")
                                }
                            }
                        }
                    }
                }
            }
        },
    ) { padding ->
        HorizontalPager(pager, modifier = Modifier.padding(padding).fillMaxSize(), userScrollEnabled = items.size > 1) { index ->
            val item = items.getOrNull(index) ?: return@HorizontalPager
            val preview by produceState<Bitmap?>(null, item.uri) { value = vm.preview(item.uri) }
            Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                val bitmap = preview
                if (bitmap == null) CircularProgressIndicator()
                else CornerEditor(bitmap, item.quad) { vm.setQuad(index, it) }
            }
        }
    }
}

/** The photo with the page outline; each corner can be dragged. Outside the page is dimmed. */
@Composable
fun CornerEditor(bitmap: Bitmap, quad: Quad, onChange: (Quad) -> Unit) {
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    val current by rememberUpdatedState(quad)
    val primary = MaterialTheme.colorScheme.primary
    val touch = with(LocalDensity.current) { 40.dp.toPx() }
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val boxW = constraints.maxWidth.toFloat()
        val boxH = constraints.maxHeight.toFloat()
        val scale = minOf(boxW / bitmap.width, boxH / bitmap.height)
        val imgW = bitmap.width * scale
        val imgH = bitmap.height * scale
        val origin = Offset((boxW - imgW) / 2, (boxH - imgH) / 2)
        fun corner(q: Quad, i: Int) = Offset(origin.x + q.x(i) * imgW, origin.y + q.y(i) * imgH)
        var dragging by remember { mutableStateOf(-1) }

        Image(image, "Photo", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        Canvas(
            Modifier.fillMaxSize().pointerInput(imgW, imgH) {
                detectDragGestures(
                    onDragStart = { p ->
                        dragging = (0 until 4).minByOrNull { val c = corner(current, it); hypot(c.x - p.x, c.y - p.y) }
                            ?.takeIf { val c = corner(current, it); hypot(c.x - p.x, c.y - p.y) < touch * 2 } ?: -1
                    },
                    onDragEnd = { dragging = -1 },
                    onDragCancel = { dragging = -1 },
                ) { change, drag ->
                    if (dragging < 0) return@detectDragGestures
                    change.consume()
                    val q = current
                    onChange(q.moved(dragging, q.x(dragging) + drag.x / imgW, q.y(dragging) + drag.y / imgH))
                }
            },
        ) {
            val path = Path().apply {
                moveTo(corner(quad, 0).x, corner(quad, 0).y)
                for (i in 1..3) lineTo(corner(quad, i).x, corner(quad, i).y)
                close()
            }
            val dim = Path().apply {
                fillType = PathFillType.EvenOdd
                addRect(androidx.compose.ui.geometry.Rect(origin, androidx.compose.ui.geometry.Size(imgW, imgH)))
                addPath(path)
            }
            drawPath(dim, Color.Black.copy(alpha = 0.45f))
            drawPath(path, primary, style = Stroke(width = 3.dp.toPx()))
            for (i in 0 until 4) {
                val c = corner(quad, i)
                val r = if (i == dragging) 16.dp.toPx() else 12.dp.toPx()
                drawCircle(Color.White, r, c)
                drawCircle(primary, r, c, style = Stroke(3.dp.toPx()))
            }
        }
    }
}
