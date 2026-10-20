package com.ganim.scanfire.export

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ganim.scanfire.ui.common.appContainer

/** Like CreateDocument but with the MIME type chosen per launch. Input: (fileName, mime). */
internal class CreateTypedDocument : ActivityResultContract<Pair<String, String>, android.net.Uri?>() {
    override fun createIntent(context: android.content.Context, input: Pair<String, String>) =
        android.content.Intent(android.content.Intent.ACTION_CREATE_DOCUMENT)
            .addCategory(android.content.Intent.CATEGORY_OPENABLE)
            .setType(input.second)
            .putExtra(android.content.Intent.EXTRA_TITLE, input.first)

    override fun parseResult(resultCode: Int, intent: android.content.Intent?) =
        intent?.data.takeIf { resultCode == android.app.Activity.RESULT_OK }
}

/** Handles the export events of [vm] (share sheet, save picker, print) for the screen hosting it. */
@Composable
fun ExportEffects(vm: ExportViewModel) {
    val context = LocalContext.current
    val saver = rememberLauncherForActivityResult(CreateTypedDocument()) { vm.saveTo(context, it) }
    LaunchedEffect(vm) {
        vm.events.collect { event ->
            when (event) {
                is ExportEvent.Share -> Sharing.share(context, event.files, event.mime, event.subject)
                is ExportEvent.PickDestination -> saver.launch(event.file.name to event.mime)
                is ExportEvent.Print -> Sharing.print(context, event.pdf, event.name)
                is ExportEvent.Message -> Toast.makeText(context, event.text, Toast.LENGTH_SHORT).show()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportSheet(
    pageCount: Int,
    onDismiss: () -> Unit,
    onExport: (ExportFormat, ExportAction, pageSize: String) -> Unit,
) {
    val settings by appContainer().settings.settings.collectAsStateWithLifecycle(initialValue = null)
    var format by rememberSaveable { mutableStateOf(ExportFormat.Pdf) }
    var pageSize by remember(settings?.pdfPageSize) { mutableStateOf(settings?.pdfPageSize ?: "a4") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                if (pageCount == 1) "Export 1 page" else "Export $pageCount pages",
                style = MaterialTheme.typography.titleLarge,
            )
            Text("Format", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(ExportFormat.Pdf, ExportFormat.Jpeg, ExportFormat.LongImage).forEach {
                    FilterChip(selected = format == it, onClick = { format = it }, label = { Text(it.label) })
                }
            }
            if (format == ExportFormat.Pdf) {
                Text("Page size", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("a4" to "A4", "letter" to "Letter", "fit" to "Fit to image").forEach { (key, label) ->
                        FilterChip(selected = pageSize == key, onClick = { pageSize = key }, label = { Text(label) })
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onExport(format, ExportAction.Share, pageSize) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.Share, null, Modifier.size(18.dp)); Text("  Share")
                }
                OutlinedButton(onClick = { onExport(format, ExportAction.Save, pageSize) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.SaveAlt, null, Modifier.size(18.dp)); Text("  Save")
                }
            }
            OutlinedButton(onClick = { onExport(ExportFormat.Pdf, ExportAction.Print, pageSize) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Print, null, Modifier.size(18.dp)); Text("  Print")
            }
        }
    }
}
