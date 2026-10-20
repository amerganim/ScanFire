package com.ganim.scanfire.ocr

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.TextSnippet
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ganim.scanfire.export.Sharing
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OcrScreen(vm: OcrViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.title.ifEmpty { "Text" }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    if (state.singlePage && state.edited) {
                        IconButton(onClick = vm::saveEdit) { Icon(Icons.Outlined.Save, "Save text") }
                    }
                    IconButton(enabled = !state.running, onClick = { vm.run(force = true) }) { Icon(Icons.Outlined.Refresh, "Recognize again") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ScriptPicker(state.script, enabled = !state.running, onPick = vm::setScript)

            if (state.running) {
                val (done, total) = state.progress
                Text(if (total > 1) "Reading page ${minOf(done + 1, total)} of $total…" else "Reading text…")
                LinearProgressIndicator(progress = { if (total == 0) 0f else done / total.toFloat() }, modifier = Modifier.fillMaxWidth())
            }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            if (!state.running && state.error == null) {
                if (state.text.isBlank()) {
                    Text("No text found. Try another script, or a sharper scan.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (state.singlePage) {
                    OutlinedTextField(
                        value = state.text,
                        onValueChange = vm::edit,
                        label = { Text("Recognized text (editable)") },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 240.dp),
                    )
                } else if (state.text.isNotBlank()) {
                    Card(Modifier.fillMaxWidth()) {
                        SelectionContainer { Text(state.text, Modifier.padding(16.dp)) }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(onClick = { copy(context, state.text) }, label = { Text("Copy") }, leadingIcon = { Icon(Icons.Outlined.ContentCopy, null, Modifier.size(18.dp)) })
                    AssistChip(onClick = { Sharing.shareText(context, state.text, state.title) }, label = { Text("Share") }, leadingIcon = { Icon(Icons.Outlined.Share, null, Modifier.size(18.dp)) })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(
                        onClick = { scope.launch { Sharing.share(context, listOf(vm.exportTxt()), "text/plain", state.title) } },
                        label = { Text("TXT file") },
                        leadingIcon = { Icon(Icons.Outlined.TextSnippet, null, Modifier.size(18.dp)) },
                    )
                    AssistChip(
                        onClick = {
                            scope.launch {
                                Sharing.share(context, listOf(vm.exportDocx()), "application/vnd.openxmlformats-officedocument.wordprocessingml.document", state.title)
                            }
                        },
                        label = { Text("Word (.docx)") },
                        leadingIcon = { Icon(Icons.Outlined.Description, null, Modifier.size(18.dp)) },
                    )
                }

                if (state.text.isNotBlank()) TranslatePanel(vm, state)
            }
        }
    }
}

@Composable
private fun ScriptPicker(script: OcrScript, enabled: Boolean, onPick: (OcrScript) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(enabled = enabled, onClick = { open = true }) { Text("Script: ${script.label}", maxLines = 1, overflow = TextOverflow.Ellipsis) }
        DropdownMenu(open, onDismissRequest = { open = false }) {
            OcrScript.entries.forEach {
                DropdownMenuItem(text = { Text(it.label) }, onClick = { open = false; if (it != script) onPick(it) })
            }
        }
    }
}

@Composable
private fun TranslatePanel(vm: OcrViewModel, state: OcrState) {
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }
    val languages = vm.languages
    Spacer(Modifier.height(4.dp))
    Text("Translate", style = MaterialTheme.typography.titleMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box {
            OutlinedButton(onClick = { open = true }) { Text("To: " + (languages.firstOrNull { it.code == state.targetLanguage }?.name ?: state.targetLanguage)) }
            DropdownMenu(open, onDismissRequest = { open = false }, modifier = Modifier.heightIn(max = 360.dp)) {
                languages.forEach { lang ->
                    DropdownMenuItem(text = { Text(lang.name) }, onClick = { open = false; vm.setTarget(lang.code) })
                }
            }
        }
        Button(enabled = !state.translating, onClick = vm::translate) {
            Icon(Icons.Outlined.Translate, null, Modifier.size(18.dp)); Text("  Translate")
        }
    }
    if (state.translating) LinearProgressIndicator(Modifier.fillMaxWidth())
    state.translateStatus?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    state.translation?.let { translated ->
        Card(Modifier.fillMaxWidth()) { SelectionContainer { Text(translated, Modifier.padding(16.dp)) } }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(onClick = { copy(context, translated) }, label = { Text("Copy translation") }, leadingIcon = { Icon(Icons.Outlined.ContentCopy, null, Modifier.size(18.dp)) })
            AssistChip(onClick = { Sharing.shareText(context, translated, state.title) }, label = { Text("Share") }, leadingIcon = { Icon(Icons.Outlined.Share, null, Modifier.size(18.dp)) })
        }
    }
}

private fun copy(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("ScanFire text", text))
    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
}
