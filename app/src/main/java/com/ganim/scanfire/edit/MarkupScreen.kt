package com.ganim.scanfire.edit

import android.graphics.Bitmap
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.BorderColor
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.ganim.scanfire.data.PageRepository
import com.ganim.scanfire.imaging.Bitmaps
import com.ganim.scanfire.ui.common.TextInputDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class MarkTool { Pen, Highlighter, Text }

/** Everything is stored in page units (0..1) so it can be drawn on screen and baked at full resolution alike. */
private sealed interface Mark {
    data class Stroke(val points: List<Offset>, val color: Color, val width: Float, val highlighter: Boolean) : Mark
    data class Label(val at: Offset, val text: String, val color: Color, val size: Float) : Mark
}

private val PenColors = listOf(Color(0xFF111111), Color(0xFF1A3DB8), Color(0xFFD32F2F), Color(0xFF2E7D32))
private val HighlightColors = listOf(Color(0xFFFFEB3B), Color(0xFF80DEEA), Color(0xFFF48FB1), Color(0xFFA5D6A7))

/** Pen, highlighter and text notes on a page, burnt into the image when saved (the original stays for re-editing). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarkupScreen(pageId: Long, pages: PageRepository, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    val page by produceState<Bitmap?>(null, pageId) {
        val p = pages.page(pageId).filterNotNull().first()
        value = withContext(Dispatchers.IO) { Bitmaps.decode(pages.imageFile(p), maxEdge = 1600) }
    }
    val marks = remember { mutableStateListOf<Mark>() }
    var tool by remember { mutableStateOf(MarkTool.Pen) }
    var penColor by remember { mutableStateOf(PenColors.first()) }
    var highlightColor by remember { mutableStateOf(HighlightColors.first()) }
    var textAt by remember { mutableStateOf<Offset?>(null) }
    var saving by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mark up") },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Cancel") } },
                actions = {
                    IconButton(enabled = marks.isNotEmpty(), onClick = { marks.removeAt(marks.lastIndex) }) { Icon(Icons.AutoMirrored.Outlined.Undo, "Undo") }
                    IconButton(enabled = marks.isNotEmpty() && !saving && page != null, onClick = {
                        saving = true
                        val bmp = page!!
                        scope.launch {
                            val overlay = withContext(Dispatchers.Default) { render(marks.toList(), bmp.width, bmp.height) }
                            pages.stamp(pageId, overlay, RectF(0f, 0f, 1f, 1f))
                            onClose()
                        }
                    }) { Icon(Icons.Filled.Check, "Save") }
                },
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        val colors = if (tool == MarkTool.Highlighter) HighlightColors else PenColors
                        val selected = if (tool == MarkTool.Highlighter) highlightColor else penColor
                        colors.forEach { c ->
                            Box(
                                Modifier.size(34.dp).background(c, CircleShape)
                                    .border(if (c == selected) 3.dp else 1.dp, if (c == selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape)
                                    .clickable { if (tool == MarkTool.Highlighter) highlightColor = c else penColor = c },
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ToolChip("Pen", Icons.Outlined.Edit, tool == MarkTool.Pen, Modifier.weight(1f)) { tool = MarkTool.Pen }
                        ToolChip("Highlight", Icons.Outlined.BorderColor, tool == MarkTool.Highlighter, Modifier.weight(1f)) { tool = MarkTool.Highlighter }
                        ToolChip("Text", Icons.Outlined.TextFields, tool == MarkTool.Text, Modifier.weight(1f)) { tool = MarkTool.Text }
                    }
                    Text(
                        when (tool) {
                            MarkTool.Text -> "Tap where the note should go."
                            else -> "Draw with your finger. Undo removes the last mark."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize().padding(12.dp), contentAlignment = Alignment.Center) {
            val bmp = page
            if (bmp == null || saving) CircularProgressIndicator() else {
                val image = remember(bmp) { bmp.asImageBitmap() }
                BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    val scale = minOf(constraints.maxWidth.toFloat() / bmp.width, constraints.maxHeight.toFloat() / bmp.height)
                    val w = bmp.width * scale
                    val h = bmp.height * scale
                    val density = LocalDensity.current
                    var current by remember { mutableStateOf<List<Offset>>(emptyList()) }
                    // Widths are a share of the page width, so they look the same on screen and when baked.
                    val penWidth = 0.004f
                    val highlightWidth = 0.025f
                    Box(Modifier.size(with(density) { w.toDp() }, with(density) { h.toDp() })) {
                        Image(image, "Page", contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
                        Canvas(
                            Modifier.fillMaxSize()
                                .pointerInput(tool) {
                                    if (tool == MarkTool.Text) detectTapGestures { textAt = Offset(it.x / w, it.y / h) }
                                    else detectDragGestures(
                                        onDragStart = { current = listOf(Offset(it.x / w, it.y / h)) },
                                        onDragEnd = {
                                            if (current.size > 1) {
                                                val hl = tool == MarkTool.Highlighter
                                                marks += Mark.Stroke(current, if (hl) highlightColor else penColor, if (hl) highlightWidth else penWidth, hl)
                                            }
                                            current = emptyList()
                                        },
                                    ) { change, _ -> change.consume(); current = current + Offset(change.position.x / w, change.position.y / h) }
                                },
                        ) {
                            val live = if (current.size > 1) {
                                val hl = tool == MarkTool.Highlighter
                                listOf(Mark.Stroke(current, if (hl) highlightColor else penColor, if (hl) highlightWidth else penWidth, hl))
                            } else emptyList()
                            (marks + live).forEach { m ->
                                when (m) {
                                    is Mark.Stroke -> drawPath(
                                        pathOf(m.points, w, h),
                                        if (m.highlighter) m.color.copy(alpha = 0.4f) else m.color,
                                        style = Stroke(m.width * w, cap = if (m.highlighter) StrokeCap.Square else StrokeCap.Round, join = StrokeJoin.Round),
                                    )
                                    is Mark.Label -> drawContext.canvas.nativeCanvas.drawText(m.text, m.at.x * w, m.at.y * h, labelPaint(m, w))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    textAt?.let { at ->
        TextInputDialog("Add a note", "", label = "Note", confirm = "Add", onDismiss = { textAt = null }) { text ->
            if (text.isNotBlank()) marks += Mark.Label(at, text.trim(), penColor, 0.035f)
            textAt = null
        }
    }
}

@Composable
private fun ToolChip(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        color = if (selected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surface,
        contentColor = if (selected) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurface,
        modifier = modifier,
    ) {
        Row(Modifier.padding(vertical = 10.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(18.dp)); Text("  $label", style = MaterialTheme.typography.labelLarge)
        }
    }
}

private fun pathOf(points: List<Offset>, w: Float, h: Float) = Path().apply {
    moveTo(points.first().x * w, points.first().y * h)
    for (i in 1 until points.size) {
        val p = points[i - 1]; val q = points[i]
        quadraticTo(p.x * w, p.y * h, (p.x + q.x) / 2 * w, (p.y + q.y) / 2 * h)
    }
}

private fun labelPaint(m: Mark.Label, pageWidth: Float) = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
    color = m.color.toArgb()
    textSize = m.size * pageWidth
    typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD)
}

/** The marks on a transparent bitmap the size of the page image, ready for [PageRepository.stamp]. */
private fun render(marks: List<Mark>, width: Int, height: Int): Bitmap {
    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bmp)
    val w = width.toFloat(); val h = height.toFloat()
    marks.forEach { m ->
        when (m) {
            is Mark.Stroke -> {
                val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    color = (if (m.highlighter) m.color.copy(alpha = 0.4f) else m.color).toArgb()
                    style = android.graphics.Paint.Style.STROKE
                    strokeWidth = m.width * w
                    strokeCap = if (m.highlighter) android.graphics.Paint.Cap.SQUARE else android.graphics.Paint.Cap.ROUND
                    strokeJoin = android.graphics.Paint.Join.ROUND
                }
                val path = android.graphics.Path()
                path.moveTo(m.points[0].x * w, m.points[0].y * h)
                for (i in 1 until m.points.size) {
                    val p = m.points[i - 1]; val q = m.points[i]
                    path.quadTo(p.x * w, p.y * h, (p.x + q.x) / 2 * w, (p.y + q.y) / 2 * h)
                }
                canvas.drawPath(path, paint)
            }
            is Mark.Label -> canvas.drawText(m.text, m.at.x * w, m.at.y * h, labelPaint(m, w))
        }
    }
    return bmp
}
