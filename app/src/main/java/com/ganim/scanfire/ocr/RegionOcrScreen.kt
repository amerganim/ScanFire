package com.ganim.scanfire.ocr

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.widget.Toast
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.ganim.scanfire.export.Sharing
import com.ganim.scanfire.imaging.Bitmaps
import com.ganim.scanfire.ui.common.appContainer
import com.ganim.scanfire.ui.theme.Ember
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

private enum class Handle { TopLeft, TopRight, BottomLeft, BottomRight, Move }

/** Drag a box over part of a page and read just that part: a phone number, an address, one paragraph. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegionOcrScreen(pageId: Long, onClose: () -> Unit) {
    val container = appContainer()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pageFile by produceState<java.io.File?>(null, pageId) {
        value = container.pages.page(pageId).filterNotNull().first().let(container.pages::imageFile)
    }
    val preview by produceState<Bitmap?>(null, pageFile) {
        val f = pageFile ?: return@produceState
        value = withContext(Dispatchers.IO) { Bitmaps.decode(f, maxEdge = 1600) }
    }
    var area by remember { mutableStateOf(RectF(0.1f, 0.3f, 0.9f, 0.5f)) }
    var reading by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Read part of the page") },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Close") } },
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val text = result
                    when {
                        reading -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            CircularProgressIndicator(Modifier.size(22.dp)); Text("Reading…")
                        }
                        text != null -> {
                            Box(Modifier.heightIn(max = 180.dp).verticalScroll(rememberScrollState())) {
                                SelectionContainer {
                                    Text(text.ifBlank { "No text in this area. Try a bigger box." }, style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                            if (text.isNotBlank()) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { copy(context, text) }) { Icon(Icons.Outlined.ContentCopy, null, Modifier.size(18.dp)); Text("  Copy") }
                                OutlinedButton(onClick = { Sharing.shareText(context, text, "Text") }) { Icon(Icons.Outlined.Share, null, Modifier.size(18.dp)); Text("  Share") }
                            }
                        }
                        else -> Text("Drag the corners around the text you want.", style = MaterialTheme.typography.bodyMedium)
                    }
                    Button(
                        enabled = !reading && pageFile != null,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            val file = pageFile ?: return@Button
                            reading = true
                            scope.launch {
                                val language = OcrLanguage.of(container.settings.settings.firstOrNull()?.ocrScript ?: "auto")
                                result = runCatching {
                                    container.ocr.recognizeRegion(file, language, container.files.newTempFile("png"), area.left, area.top, area.right, area.bottom)
                                }.getOrElse { e ->
                                    if (e is OcrLanguageMissing) "Download ${e.language.label} in Settings → More languages first." else "Couldn't read this area: ${e.message}"
                                }
                                reading = false
                            }
                        },
                    ) { Text(if (result == null) "Read this area" else "Read again") }
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
            val bitmap = preview
            if (bitmap == null) CircularProgressIndicator() else AreaPicker(bitmap, area) { area = it; result = null }
        }
    }
}

@Composable
private fun AreaPicker(page: Bitmap, area: RectF, onArea: (RectF) -> Unit) {
    val image = remember(page) { page.asImageBitmap() }
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val scale = minOf(constraints.maxWidth.toFloat() / page.width, constraints.maxHeight.toFloat() / page.height)
        val w = page.width * scale
        val h = page.height * scale
        val density = LocalDensity.current
        val grab = with(density) { 36.dp.toPx() }
        var rect by remember { mutableStateOf(area) }
        var handle by remember { mutableStateOf(Handle.Move) }
        Box(Modifier.size(with(density) { w.toDp() }, with(density) { h.toDp() })) {
            Image(image, "Page", contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
            Canvas(
                Modifier.fillMaxSize().pointerInput(w, h) {
                    detectDragGestures(
                        onDragStart = { p ->
                            val corners = listOf(
                                Handle.TopLeft to Offset(rect.left * w, rect.top * h),
                                Handle.TopRight to Offset(rect.right * w, rect.top * h),
                                Handle.BottomLeft to Offset(rect.left * w, rect.bottom * h),
                                Handle.BottomRight to Offset(rect.right * w, rect.bottom * h),
                            )
                            handle = corners.firstOrNull { (_, c) -> abs(c.x - p.x) < grab && abs(c.y - p.y) < grab }?.first ?: Handle.Move
                        },
                        onDragEnd = { onArea(rect) },
                    ) { change, drag ->
                        change.consume()
                        val dx = drag.x / w
                        val dy = drag.y / h
                        val min = 0.04f
                        rect = when (handle) {
                            Handle.TopLeft -> RectF((rect.left + dx).coerceIn(0f, rect.right - min), (rect.top + dy).coerceIn(0f, rect.bottom - min), rect.right, rect.bottom)
                            Handle.TopRight -> RectF(rect.left, (rect.top + dy).coerceIn(0f, rect.bottom - min), (rect.right + dx).coerceIn(rect.left + min, 1f), rect.bottom)
                            Handle.BottomLeft -> RectF((rect.left + dx).coerceIn(0f, rect.right - min), rect.top, rect.right, (rect.bottom + dy).coerceIn(rect.top + min, 1f))
                            Handle.BottomRight -> RectF(rect.left, rect.top, (rect.right + dx).coerceIn(rect.left + min, 1f), (rect.bottom + dy).coerceIn(rect.top + min, 1f))
                            Handle.Move -> {
                                val l = (rect.left + dx).coerceIn(0f, 1f - rect.width())
                                val t = (rect.top + dy).coerceIn(0f, 1f - rect.height())
                                RectF(l, t, l + rect.width(), t + rect.height())
                            }
                        }
                    }
                },
            ) {
                val l = rect.left * w; val t = rect.top * h; val r = rect.right * w; val b = rect.bottom * h
                // Dim everything outside the box.
                val shade = Color.Black.copy(alpha = 0.5f)
                drawRect(shade, Offset.Zero, Size(size.width, t))
                drawRect(shade, Offset(0f, b), Size(size.width, size.height - b))
                drawRect(shade, Offset(0f, t), Size(l, b - t))
                drawRect(shade, Offset(r, t), Size(size.width - r, b - t))
                drawRoundRect(Ember, Offset(l, t), Size(r - l, b - t), CornerRadius(6f), style = Stroke(3.dp.toPx()))
                listOf(Offset(l, t), Offset(r, t), Offset(l, b), Offset(r, b)).forEach { drawCircle(Color.White, 9.dp.toPx(), it); drawCircle(Ember, 6.dp.toPx(), it) }
            }
        }
    }
}

private fun copy(context: Context, text: String) {
    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("ScanFire text", text))
    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
}
