package com.ganim.scanfire.privacy

import android.graphics.Bitmap
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.ganim.scanfire.imaging.Bitmaps
import com.ganim.scanfire.ocr.OcrLanguage
import com.ganim.scanfire.ocr.OcrLanguageMissing
import com.ganim.scanfire.ui.common.appContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class RedactBox(val rect: RectF, val label: String, var on: Boolean = true)

/** Finds personal data on a page and blacks out what the user keeps selected. Permanent once applied. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RedactScreen(pageId: Long, onClose: () -> Unit) {
    val container = appContainer()
    val scope = rememberCoroutineScope()
    var page by remember { mutableStateOf<Bitmap?>(null) }
    val boxes = remember { mutableStateListOf<RedactBox>() }
    var status by remember { mutableStateOf("Looking for personal data…") }
    var working by remember { mutableStateOf(true) }

    LaunchedEffect(pageId) {
        val entity = container.pages.page(pageId).filterNotNull().first()
        val file = container.pages.imageFile(entity)
        page = withContext(Dispatchers.IO) { Bitmaps.decode(file, maxEdge = 1600) }
        val language = OcrLanguage.of(container.settings.settings.first().ocrScript)
        val found = try {
            Redactor.find(container.ocr.words(file, language))
        } catch (e: OcrLanguageMissing) {
            status = "Download ${e.language.name} for text recognition first (Settings → languages). You can still drag to black out areas."
            emptyList()
        } catch (e: Exception) {
            status = "Couldn't read the page (${e.message}). You can still drag to black out areas."
            emptyList()
        }
        found.forEach { span ->
            val l = span.words.minOf { it.left }; val t = span.words.minOf { it.top }
            val r = span.words.maxOf { it.right }; val b = span.words.maxOf { it.bottom }
            boxes += RedactBox(RectF(l, t, r, b), span.kind.label)
        }
        if (found.isNotEmpty()) status = "Found ${found.size}: " + found.groupingBy { it.kind.label }.eachCount().entries.joinToString { "${it.value} ${it.key.lowercase()}" } + ". Tap a box to keep it; drag to black out more."
        else if (status.startsWith("Looking")) status = "Nothing personal found. Drag across anything you want blacked out."
        working = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Redact") },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Cancel") } },
                actions = {
                    IconButton(enabled = !working && boxes.any { it.on }, onClick = {
                        working = true
                        scope.launch {
                            container.pages.redact(pageId, boxes.filter { it.on }.map { it.rect })
                            onClose()
                        }
                    }) { Icon(Icons.Filled.Check, "Black out selected") }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(status, style = MaterialTheme.typography.bodyMedium)
                    Text("Blacking out is permanent for this page.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize().padding(12.dp)) {
            val bitmap = page
            if (bitmap == null || working && boxes.isEmpty() && status.startsWith("Looking")) {
                CircularProgressIndicator(Modifier.padding(32.dp))
            }
            if (bitmap != null) RedactCanvas(bitmap, boxes)
        }
    }
}

@Composable
private fun RedactCanvas(bitmap: Bitmap, boxes: MutableList<RedactBox>) {
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    var draft by remember { mutableStateOf<RectF?>(null) }
    val primary = MaterialTheme.colorScheme.primary
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val scale = minOf(constraints.maxWidth.toFloat() / bitmap.width, constraints.maxHeight.toFloat() / bitmap.height)
        val w = bitmap.width * scale; val h = bitmap.height * scale
        val ox = (constraints.maxWidth - w) / 2; val oy = (constraints.maxHeight - h) / 2
        fun norm(p: Offset) = Offset(((p.x - ox) / w).coerceIn(0f, 1f), ((p.y - oy) / h).coerceIn(0f, 1f))

        Image(image, "Page", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        Canvas(
            Modifier.fillMaxSize()
                .pointerInput(w, h) {
                    detectTapGestures { p ->
                        val n = norm(p)
                        val i = boxes.indexOfFirst { it.rect.contains(n.x, n.y) }
                        if (i >= 0) boxes[i] = boxes[i].copy(on = !boxes[i].on)
                    }
                }
                .pointerInput(w, h) {
                    var start = Offset.Zero
                    detectDragGestures(
                        onDragStart = { start = norm(it); draft = RectF(start.x, start.y, start.x, start.y) },
                        onDragEnd = {
                            draft?.takeIf { it.width() > 0.01f && it.height() > 0.005f }?.let { boxes += RedactBox(it, "Area") }
                            draft = null
                        },
                        onDragCancel = { draft = null },
                    ) { change, _ ->
                        val n = norm(change.position)
                        draft = RectF(minOf(start.x, n.x), minOf(start.y, n.y), maxOf(start.x, n.x), maxOf(start.y, n.y))
                    }
                },
        ) {
            fun rect(r: RectF) = Offset(ox + r.left * w, oy + r.top * h) to Size(r.width() * w, r.height() * h)
            boxes.forEach { b ->
                // Preview with the same side padding that applying uses (PageRepository.redact).
                val padX = b.rect.height() * 0.45f * h / w
                val (o, s) = rect(RectF(b.rect.left - padX, b.rect.top, b.rect.right + padX, b.rect.bottom))
                if (b.on) drawRect(Color.Black, o, s) else drawRect(primary, o, s, style = Stroke(2.dp.toPx()))
            }
            draft?.let { val (o, s) = rect(it); drawRect(Color.Black.copy(alpha = 0.6f), o, s) }
        }
    }
}
