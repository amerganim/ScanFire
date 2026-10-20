package com.ganim.scanfire.tools

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CallMerge
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.FormatListNumbered
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ganim.scanfire.export.CreateTypedDocument
import com.ganim.scanfire.export.Sharing
import com.ganim.scanfire.ui.BusyOverlay
import com.ganim.scanfire.ui.common.containerViewModel

/** A tile on the Tools tab. Scan tools (phase 7) are passed in by the caller. */
data class ToolTile(val title: String, val description: String, val icon: ImageVector, val onClick: () -> Unit)

private val PdfTool.icon: ImageVector
    get() = when (this) {
        PdfTool.Import -> Icons.Outlined.FileOpen
        PdfTool.Merge -> Icons.Outlined.CallMerge
        PdfTool.Extract -> Icons.Outlined.ContentCut
        PdfTool.Compress -> Icons.Outlined.Compress
        PdfTool.Protect -> Icons.Outlined.Lock
        PdfTool.Unlock -> Icons.Outlined.LockOpen
        PdfTool.Watermark -> Icons.Outlined.WaterDrop
        PdfTool.PageNumbers -> Icons.Outlined.FormatListNumbered
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsScreen(
    scanTools: List<ToolTile>,
    onImportPdf: (file: java.io.File, name: String, password: String?) -> Unit,
) {
    val vm = containerViewModel { PdfToolsViewModel(it.pdfTools) }
    val step by vm.step.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var current by remember { mutableStateOf(PdfTool.Import) }

    val pickOne = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { vm.picked(current, listOf(it)) } }
    val pickMany = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { vm.picked(current, it) }
    val saver = rememberLauncherForActivityResult(CreateTypedDocument()) { uri ->
        val done = step as? ToolStep.Done
        if (uri != null && done != null) {
            runCatching { Sharing.copyTo(context, done.file, uri) }
                .onSuccess { Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show(); vm.reset() }
                .onFailure { Toast.makeText(context, "Couldn't save: ${it.message}", Toast.LENGTH_LONG).show() }
        }
    }

    fun start(tool: PdfTool) {
        current = tool
        if (tool.multiple) pickMany.launch(arrayOf("application/pdf")) else pickOne.launch(arrayOf("application/pdf"))
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(topBar = { TopAppBar(title = { Text("Tools") }) }) { padding ->
            LazyVerticalGrid(
                columns = GridCells.Adaptive(160.dp),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = padding.calculateTopPadding(), bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                if (scanTools.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) { Section("Scan") }
                    items(scanTools) { ToolCard(it.title, it.description, it.icon, it.onClick) }
                }
                item(span = { GridItemSpan(maxLineSpan) }) { Section("PDF") }
                items(PdfTool.entries) { tool -> ToolCard(tool.title, tool.description, tool.icon) { start(tool) } }
            }
        }
        (step as? ToolStep.Working)?.let { BusyOverlay(it.message) }
    }

    when (val s = step) {
        is ToolStep.Options -> OptionsDialog(s, vm, onImport = {
            vm.reset()
            vm.importFile?.let { onImportPdf(it, vm.importName, vm.importPassword) }
        })
        is ToolStep.Password -> PasswordDialog(s, onDismiss = vm::reset) { vm.submitPassword(s.tool, it) }
        is ToolStep.Done -> AlertDialog(
            onDismissRequest = vm::reset,
            title = { Text("${s.tool.title}: done") },
            text = { Text("${s.file.name}  ·  ${formatSize(s.file.length())}") },
            confirmButton = {
                TextButton(onClick = { Sharing.share(context, listOf(s.file), "application/pdf", s.file.nameWithoutExtension) }) { Text("Share") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = vm::reset) { Text("Close") }
                    TextButton(onClick = { saver.launch(s.file.name to "application/pdf") }) { Text("Save") }
                }
            },
        )
        is ToolStep.Failed -> AlertDialog(
            onDismissRequest = vm::reset,
            title = { Text("Something went wrong") },
            text = { Text(s.message) },
            confirmButton = { TextButton(onClick = vm::reset) { Text("OK") } },
        )
        else -> Unit
    }
}

@Composable
private fun Section(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 12.dp, start = 4.dp))
}

@Composable
fun ToolCard(title: String, description: String, icon: ImageVector, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, minLines = 2)
        }
    }
}

@Composable
private fun OptionsDialog(s: ToolStep.Options, vm: PdfToolsViewModel, onImport: () -> Unit) {
    var options by remember(s) { mutableStateOf(ToolOptions()) }
    var error by remember(s) { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = vm::reset,
        title = { Text(s.tool.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (s.pageCount > 0) "${s.name}  ·  ${s.pageCount} pages" else s.name, style = MaterialTheme.typography.bodyMedium)
                when (s.tool) {
                    PdfTool.Extract -> OutlinedTextField(
                        options.ranges, { options = options.copy(ranges = it); error = null },
                        label = { Text("Pages, e.g. 1-3, 5") }, singleLine = true, isError = error != null,
                        supportingText = error?.let { { Text(it) } },
                    )
                    PdfTool.Protect, PdfTool.Unlock -> OutlinedTextField(
                        options.password, { options = options.copy(password = it); error = null },
                        label = { Text(if (s.tool == PdfTool.Protect) "New password" else "Current password") },
                        singleLine = true, visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        isError = error != null, supportingText = error?.let { { Text(it) } },
                    )
                    PdfTool.Watermark -> OutlinedTextField(
                        options.text, { options = options.copy(text = it); error = null },
                        label = { Text("Watermark text") }, singleLine = true, isError = error != null,
                        supportingText = error?.let { { Text(it) } },
                    )
                    PdfTool.Compress -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("Strong", "Balanced", "Light").forEachIndexed { i, label ->
                                FilterChip(selected = options.compression == i, onClick = { options = options.copy(compression = i) }, label = { Text(label) })
                            }
                        }
                        Text("Pages become images, so text in the PDF stops being selectable.", style = MaterialTheme.typography.bodySmall)
                    }
                    PdfTool.Merge -> Text("Files are joined in the order you selected them.")
                    else -> Unit
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (s.tool == PdfTool.Import) { onImport(); return@TextButton }
                error = vm.validate(s.tool, options, s.pageCount)
                if (error == null) vm.run(s.tool, options)
            }) { Text(if (s.tool == PdfTool.Import) "Import" else "Run") }
        },
        dismissButton = { TextButton(onClick = vm::reset) { Text("Cancel") } },
    )
}

@Composable
private fun PasswordDialog(s: ToolStep.Password, onDismiss: () -> Unit, onSubmit: (String) -> Unit) {
    var password by remember(s) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Password needed") },
        text = {
            OutlinedTextField(
                password, { password = it },
                label = { Text("PDF password") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                isError = s.wrongPassword,
                supportingText = if (s.wrongPassword) { { Text("Wrong password, try again") } } else null,
            )
        },
        confirmButton = { TextButton(enabled = password.isNotEmpty(), onClick = { onSubmit(password) }) { Text("Open") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

fun formatSize(bytes: Long): String = when {
    bytes >= 1_000_000 -> "%.1f MB".format(bytes / 1_000_000.0)
    bytes >= 1_000 -> "${bytes / 1_000} KB"
    else -> "$bytes B"
}
