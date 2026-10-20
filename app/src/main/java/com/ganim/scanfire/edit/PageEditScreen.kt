package com.ganim.scanfire.edit

import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.RotateLeft
import androidx.compose.material.icons.automirrored.outlined.RotateRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.FilterVintage
import androidx.compose.material3.Checkbox
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ganim.scanfire.imaging.PageFilter
import kotlin.math.abs
import kotlin.math.hypot

private enum class EditMode { Filter, Crop }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageEditScreen(vm: PageEditViewModel, onClose: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    var mode by remember { mutableStateOf(EditMode.Filter) }
    var applyAll by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit page") },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Cancel") } },
                actions = {
                    IconButton(
                        enabled = !state.saving && (state.changed || applyAll),
                        onClick = { vm.save(applyAll, onClose) },
                    ) { Icon(Icons.Filled.Check, "Save") }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    when (mode) {
                        EditMode.Filter -> FilterStrip(state, vm::setFilter)
                        EditMode.Crop -> Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.Center) {
                            TextButton(onClick = vm::resetCrop) { Text("Reset crop") }
                        }
                    }
                    if (mode == EditMode.Filter) {
                        Row(Modifier.clickable { applyAll = !applyAll }.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = applyAll, onCheckedChange = { applyAll = it })
                            Text("Apply this filter to all pages", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        ToolButton("Filters", Icons.Outlined.FilterVintage, mode == EditMode.Filter) { mode = EditMode.Filter }
                        ToolButton("Crop", Icons.Outlined.Crop, mode == EditMode.Crop) { mode = EditMode.Crop }
                        ToolButton("Left", Icons.AutoMirrored.Outlined.RotateLeft, false) { vm.rotate(-90) }
                        ToolButton("Right", Icons.AutoMirrored.Outlined.RotateRight, false) { vm.rotate(90) }
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
            when {
                state.saving || state.preview == null -> CircularProgressIndicator()
                mode == EditMode.Crop && state.rotatedSource != null -> CropEditor(
                    image = remember(state.rotatedSource) { state.rotatedSource!!.asImageBitmap() },
                    crop = state.crop,
                    onCrop = vm::setCrop,
                    onCropEnd = vm::commitCrop,
                )
                else -> Image(
                    bitmap = remember(state.preview) { state.preview!!.asImageBitmap() },
                    contentDescription = "Preview",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun ToolButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) }, leadingIcon = { Icon(icon, null, Modifier.size(18.dp)) })
}

@Composable
private fun FilterStrip(state: EditState, onFilter: (String) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(PageFilter.entries) { f ->
            val selected = state.filter == f.key
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onFilter(f.key) }) {
                val thumb = state.thumbnails[f]
                Box(
                    Modifier.size(72.dp, 92.dp).clip(RoundedCornerShape(8.dp))
                        .border(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (thumb != null) {
                        Image(remember(thumb) { thumb.asImageBitmap() }, f.label, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    } else {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                }
                Text(
                    f.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

private enum class Handle { TopLeft, TopRight, BottomLeft, BottomRight, Left, Top, Right, Bottom, Move }

/** Drag the corners or edges, or drag inside to move the frame. [crop] is normalised to the image. */
@Composable
private fun CropEditor(image: ImageBitmap, crop: RectF, onCrop: (RectF) -> Unit, onCropEnd: () -> Unit) {
    val currentCrop by rememberUpdatedState(crop)
    val primary = MaterialTheme.colorScheme.primary
    val touch = with(LocalDensity.current) { 32.dp.toPx() }
    val minSize = 0.08f

    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val boxW = constraints.maxWidth.toFloat()
        val boxH = constraints.maxHeight.toFloat()
        val scale = minOf(boxW / image.width, boxH / image.height)
        val imgW = image.width * scale
        val imgH = image.height * scale
        val origin = Offset((boxW - imgW) / 2, (boxH - imgH) / 2)
        fun toScreen(c: RectF) = Rect(origin.x + c.left * imgW, origin.y + c.top * imgH, origin.x + c.right * imgW, origin.y + c.bottom * imgH)

        var handle by remember { mutableStateOf<Handle?>(null) }

        Image(image, "Page to crop", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        Canvas(
            Modifier.fillMaxSize().pointerInput(imgW, imgH) {
                detectDragGestures(
                    onDragStart = { p -> handle = pickHandle(toScreen(currentCrop), p, touch) },
                    onDragEnd = { handle = null; onCropEnd() },
                    onDragCancel = { handle = null; onCropEnd() },
                ) { change, drag ->
                    change.consume()
                    val h = handle ?: return@detectDragGestures
                    val dx = drag.x / imgW
                    val dy = drag.y / imgH
                    val c = RectF(currentCrop)
                    when (h) {
                        Handle.Move -> {
                            val w = c.width(); val ht = c.height()
                            c.left = (c.left + dx).coerceIn(0f, 1f - w); c.top = (c.top + dy).coerceIn(0f, 1f - ht)
                            c.right = c.left + w; c.bottom = c.top + ht
                        }
                        else -> {
                            if (h == Handle.TopLeft || h == Handle.BottomLeft || h == Handle.Left) c.left = (c.left + dx).coerceIn(0f, c.right - minSize)
                            if (h == Handle.TopRight || h == Handle.BottomRight || h == Handle.Right) c.right = (c.right + dx).coerceIn(c.left + minSize, 1f)
                            if (h == Handle.TopLeft || h == Handle.TopRight || h == Handle.Top) c.top = (c.top + dy).coerceIn(0f, c.bottom - minSize)
                            if (h == Handle.BottomLeft || h == Handle.BottomRight || h == Handle.Bottom) c.bottom = (c.bottom + dy).coerceIn(c.top + minSize, 1f)
                        }
                    }
                    onCrop(c)
                }
            },
        ) {
            val r = toScreen(crop)
            // Dim everything outside the frame.
            val dim = Path().apply {
                fillType = PathFillType.EvenOdd
                addRect(Rect(origin, Size(imgW, imgH)))
                addRect(r)
            }
            drawPath(dim, Color.Black.copy(alpha = 0.55f))
            drawRect(primary, r.topLeft, r.size, style = Stroke(width = 2.dp.toPx()))
            // Rule-of-thirds guides.
            for (i in 1..2) {
                val x = r.left + r.width * i / 3; val y = r.top + r.height * i / 3
                drawLine(Color.White.copy(alpha = 0.5f), Offset(x, r.top), Offset(x, r.bottom), 1.dp.toPx())
                drawLine(Color.White.copy(alpha = 0.5f), Offset(r.left, y), Offset(r.right, y), 1.dp.toPx())
            }
            listOf(r.topLeft, r.topRight, r.bottomLeft, r.bottomRight).forEach {
                drawCircle(Color.White, 10.dp.toPx(), it)
                drawCircle(primary, 10.dp.toPx(), it, style = Stroke(3.dp.toPx()))
            }
        }
    }
}

private fun pickHandle(r: Rect, p: Offset, touch: Float): Handle? {
    val corners = listOf(Handle.TopLeft to r.topLeft, Handle.TopRight to r.topRight, Handle.BottomLeft to r.bottomLeft, Handle.BottomRight to r.bottomRight)
    corners.minByOrNull { (_, c) -> hypot(c.x - p.x, c.y - p.y) }?.let { (h, c) -> if (hypot(c.x - p.x, c.y - p.y) < touch * 1.5f) return h }
    val inX = p.x in (r.left - touch)..(r.right + touch)
    val inY = p.y in (r.top - touch)..(r.bottom + touch)
    if (inY && abs(p.x - r.left) < touch) return Handle.Left
    if (inY && abs(p.x - r.right) < touch) return Handle.Right
    if (inX && abs(p.y - r.top) < touch) return Handle.Top
    if (inX && abs(p.y - r.bottom) < touch) return Handle.Bottom
    return if (r.contains(p)) Handle.Move else null
}
