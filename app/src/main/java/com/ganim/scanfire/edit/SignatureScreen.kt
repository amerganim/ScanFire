package com.ganim.scanfire.edit

import android.graphics.Bitmap
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.ganim.scanfire.data.PageRepository
import com.ganim.scanfire.imaging.Bitmaps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private val InkColors = listOf(Color(0xFF111111), Color(0xFF1A3DB8), Color(0xFFB3261E))

/** Draw a signature, then drag and pinch it into place on the page, and burn it in. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignatureScreen(pageId: Long, pages: PageRepository, onClose: () -> Unit) {
    var signature by remember { mutableStateOf<Bitmap?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val pageImage by produceState<Bitmap?>(null, pageId) {
        val page = pages.page(pageId).filterNotNull().first()
        value = withContext(Dispatchers.IO) { Bitmaps.decode(pages.imageFile(page), maxEdge = 1600) }
    }
    var placement by remember { mutableStateOf(RectF(0.55f, 0.78f, 0.92f, 0.9f)) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (signature == null) "Draw your signature" else "Place signature") },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Cancel") } },
                actions = {
                    if (signature != null) {
                        IconButton(enabled = !saving, onClick = {
                            saving = true
                            scope.launch {
                                pages.stamp(pageId, signature!!, placement)
                                onClose()
                            }
                        }) { Icon(Icons.Filled.Check, "Apply signature") }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
            when {
                saving -> CircularProgressIndicator()
                signature == null -> SignaturePad(onDone = { sig ->
                    signature = sig
                    // Start the stamp at the signature's own aspect ratio, in the lower right.
                    val page = pageImage
                    if (page != null) {
                        val w = 0.36f
                        val h = (w * page.width / page.height) * sig.height / sig.width
                        placement = RectF(0.58f, 0.86f - h, 0.58f + w, 0.86f)
                    }
                })
                pageImage == null -> CircularProgressIndicator()
                else -> PlaceSignature(pageImage!!, signature!!, placement) { placement = it }
            }
        }
    }
}

@Composable
private fun SignaturePad(onDone: (Bitmap) -> Unit) {
    val strokes = remember { mutableStateListOf<List<Offset>>() }
    var current by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var color by remember { mutableStateOf(InkColors.first()) }
    var padSize by remember { mutableStateOf(androidx.compose.ui.geometry.Size.Zero) }
    val strokeWidth = with(LocalDensity.current) { 4.dp.toPx() }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(
            Modifier.fillMaxWidth().aspectRatio(2.2f).clip(RoundedCornerShape(12.dp)).background(Color.White)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { current = listOf(it) },
                        onDragEnd = { strokes += current; current = emptyList() },
                    ) { change, _ -> change.consume(); current = current + change.position }
                },
        ) {
            padSize = size
            (strokes + listOf(current)).forEach { pts -> if (pts.size > 1) drawPath(pathOf(pts), color, style = Stroke(strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            InkColors.forEach { c ->
                Box(
                    Modifier.size(32.dp).clip(CircleShape).background(c)
                        .border(if (c == color) 3.dp else 0.dp, MaterialTheme.colorScheme.primary, CircleShape)
                        .clickable { color = c },
                )
            }
            OutlinedButton(onClick = { strokes.clear() }) { Text("Clear") }
            Button(enabled = strokes.isNotEmpty(), onClick = { renderSignature(strokes, padSize, color, strokeWidth)?.let(onDone) }) { Text("Use") }
        }
        Text("Tap a colour, sign in the white box, then tap Use.", style = MaterialTheme.typography.bodySmall)
    }
}

private fun pathOf(points: List<Offset>) = Path().apply {
    moveTo(points.first().x, points.first().y)
    // Midpoint quadratic smoothing makes finger strokes look like ink rather than polylines.
    for (i in 1 until points.size) {
        val p = points[i - 1]; val q = points[i]
        quadraticTo(p.x, p.y, (p.x + q.x) / 2, (p.y + q.y) / 2)
    }
}

/** Draws the strokes onto a transparent bitmap trimmed to the ink, at 2x for crisp stamping. */
private fun renderSignature(strokes: List<List<Offset>>, size: androidx.compose.ui.geometry.Size, color: Color, width: Float): Bitmap? {
    val all = strokes.flatten()
    if (all.isEmpty() || size.width <= 0f) return null
    val pad = width * 2
    val left = (all.minOf { it.x } - pad).coerceAtLeast(0f)
    val top = (all.minOf { it.y } - pad).coerceAtLeast(0f)
    val right = (all.maxOf { it.x } + pad).coerceAtMost(size.width)
    val bottom = (all.maxOf { it.y } + pad).coerceAtMost(size.height)
    val scale = 2f
    val bmp = Bitmap.createBitmap(((right - left) * scale).roundToInt().coerceAtLeast(1), ((bottom - top) * scale).roundToInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bmp)
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color.toArgb(); style = android.graphics.Paint.Style.STROKE
        strokeWidth = width * scale; strokeCap = android.graphics.Paint.Cap.ROUND; strokeJoin = android.graphics.Paint.Join.ROUND
    }
    strokes.filter { it.size > 1 }.forEach { pts ->
        val path = android.graphics.Path()
        path.moveTo((pts[0].x - left) * scale, (pts[0].y - top) * scale)
        for (i in 1 until pts.size) {
            val p = pts[i - 1]; val q = pts[i]
            path.quadTo((p.x - left) * scale, (p.y - top) * scale, ((p.x + q.x) / 2 - left) * scale, ((p.y + q.y) / 2 - top) * scale)
        }
        canvas.drawPath(path, paint)
    }
    return bmp
}

@Composable
private fun PlaceSignature(page: Bitmap, signature: Bitmap, placement: RectF, onPlacement: (RectF) -> Unit) {
    val pageBitmap = remember(page) { page.asImageBitmap() }
    val sigBitmap = remember(signature) { signature.asImageBitmap() }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Drag to move, pinch to resize, then tap ✓.", style = MaterialTheme.typography.bodySmall)
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            val scale = minOf(constraints.maxWidth.toFloat() / page.width, constraints.maxHeight.toFloat() / page.height)
            val w = page.width * scale
            val h = page.height * scale
            val density = LocalDensity.current
            var rect by remember { mutableStateOf(placement) }
            Box(Modifier.size(with(density) { w.toDp() }, with(density) { h.toDp() })) {
                Image(pageBitmap, "Page", contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
                Image(
                    sigBitmap, "Signature", contentScale = ContentScale.FillBounds,
                    modifier = Modifier
                        .offset { IntOffset((rect.left * w).roundToInt(), (rect.top * h).roundToInt()) }
                        .size(with(density) { (rect.width() * w).toDp() }, with(density) { (rect.height() * h).toDp() })
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
                        .pointerInput(w, h) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                val r = RectF(rect)
                                val cw = (r.width() * zoom).coerceIn(0.08f, 1f)
                                val ch = r.height() * (cw / r.width())
                                val cx = r.centerX() + pan.x / w
                                val cy = r.centerY() + pan.y / h
                                val left = (cx - cw / 2).coerceIn(0f, 1f - cw)
                                val top = (cy - ch / 2).coerceIn(0f, 1f - ch)
                                rect = RectF(left, top, left + cw, top + ch)
                                onPlacement(rect)
                            }
                        },
                )
            }
        }
    }
}
