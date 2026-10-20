package com.ganim.scanfire.qr

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ganim.scanfire.data.db.CodeScanDao
import com.ganim.scanfire.data.db.CodeScanEntity
import com.ganim.scanfire.export.Sharing
import com.ganim.scanfire.ui.common.containerViewModel
import com.ganim.scanfire.ui.common.formatDate
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CodesViewModel(private val dao: CodeScanDao) : ViewModel() {
    val history = dao.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun add(barcode: Barcode, onSaved: (CodeScanEntity) -> Unit) = viewModelScope.launch {
        val value = barcode.rawValue ?: barcode.displayValue ?: return@launch
        val entity = CodeScanEntity(value = value, format = formatName(barcode.format), type = typeName(barcode.valueType))
        onSaved(entity.copy(id = dao.insert(entity)))
    }

    fun delete(id: Long) = viewModelScope.launch { dao.delete(id) }
    fun clear() = viewModelScope.launch { dao.clear() }

    companion object {
        fun typeName(type: Int) = when (type) {
            Barcode.TYPE_URL -> "Link"
            Barcode.TYPE_WIFI -> "Wi-Fi"
            Barcode.TYPE_EMAIL -> "Email"
            Barcode.TYPE_PHONE -> "Phone"
            Barcode.TYPE_SMS -> "SMS"
            Barcode.TYPE_CONTACT_INFO -> "Contact"
            Barcode.TYPE_GEO -> "Location"
            Barcode.TYPE_CALENDAR_EVENT -> "Event"
            Barcode.TYPE_ISBN -> "ISBN"
            Barcode.TYPE_PRODUCT -> "Product"
            else -> "Text"
        }

        fun formatName(format: Int) = when (format) {
            Barcode.FORMAT_QR_CODE -> "QR"
            Barcode.FORMAT_DATA_MATRIX -> "Data Matrix"
            Barcode.FORMAT_AZTEC -> "Aztec"
            Barcode.FORMAT_PDF417 -> "PDF417"
            Barcode.FORMAT_EAN_13 -> "EAN-13"
            Barcode.FORMAT_EAN_8 -> "EAN-8"
            Barcode.FORMAT_UPC_A -> "UPC-A"
            Barcode.FORMAT_UPC_E -> "UPC-E"
            Barcode.FORMAT_CODE_128 -> "Code 128"
            Barcode.FORMAT_CODE_39 -> "Code 39"
            Barcode.FORMAT_CODE_93 -> "Code 93"
            Barcode.FORMAT_CODABAR -> "Codabar"
            Barcode.FORMAT_ITF -> "ITF"
            else -> "Barcode"
        }
    }
}

/** Scans QR codes and barcodes with Google's on-device code scanner and keeps a local history. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CodesScreen(onBack: () -> Unit, scanOnOpen: Boolean) {
    val vm = containerViewModel { CodesViewModel(it.database.codeScans()) }
    val history by vm.history.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var shown by remember { mutableStateOf<CodeScanEntity?>(null) }
    var confirmClear by remember { mutableStateOf(false) }

    fun scan() {
        val options = GmsBarcodeScannerOptions.Builder().enableAutoZoom().build()
        GmsBarcodeScanning.getClient(context, options).startScan()
            .addOnSuccessListener { barcode -> vm.add(barcode) { shown = it } }
            .addOnFailureListener { Toast.makeText(context, "Scanner unavailable: ${it.message}", Toast.LENGTH_LONG).show() }
    }

    var launched by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { if (scanOnOpen && !launched) { launched = true; scan() } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("QR & barcodes") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    if (history.isNotEmpty()) IconButton(onClick = { confirmClear = true }) { Icon(Icons.Outlined.DeleteSweep, "Clear history") }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = ::scan, icon = { Icon(Icons.Outlined.QrCodeScanner, null) }, text = { Text("Scan code") })
        },
    ) { padding ->
        if (history.isEmpty()) {
            Column(Modifier.padding(padding).fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Outlined.QrCodeScanner, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(16.dp))
                Text("Scanned codes appear here", style = MaterialTheme.typography.titleMedium)
                Text("History stays on this phone only.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
                items(history, key = { it.id }) { item ->
                    ListItem(
                        headlineContent = { Text(item.value, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                        supportingContent = { Text("${item.type} · ${item.format} · ${formatDate(context, item.createdAt)}") },
                        trailingContent = { IconButton(onClick = { vm.delete(item.id) }) { Icon(Icons.Outlined.Delete, "Delete") } },
                        modifier = Modifier.clickable { shown = item },
                    )
                }
            }
        }
    }

    shown?.let { item ->
        AlertDialog(
            onDismissRequest = { shown = null },
            title = { Text("${item.type} (${item.format})") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SelectionContainer { Text(item.value) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistChip(onClick = { copy(context, item.value) }, label = { Text("Copy") }, leadingIcon = { Icon(Icons.Outlined.ContentCopy, null, Modifier.size(18.dp)) })
                        AssistChip(onClick = { Sharing.shareText(context, item.value) }, label = { Text("Share") }, leadingIcon = { Icon(Icons.Outlined.Share, null, Modifier.size(18.dp)) })
                    }
                }
            },
            confirmButton = {
                openIntent(item)?.let { intent ->
                    TextButton(onClick = {
                        runCatching { context.startActivity(intent) }.onFailure { Toast.makeText(context, "No app can open this", Toast.LENGTH_SHORT).show() }
                    }) { Icon(Icons.Outlined.OpenInNew, null, Modifier.size(18.dp)); Text("  Open") }
                }
            },
            dismissButton = { TextButton(onClick = { shown = null }) { Text("Close") } },
        )
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear history?") },
            text = { Text("All ${history.size} scanned codes will be removed.") },
            confirmButton = { TextButton(onClick = { vm.clear(); confirmClear = false }) { Text("Clear") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}

/** Only links, phone numbers, emails and map locations get an Open button; nothing runs automatically. */
private fun openIntent(item: CodeScanEntity): Intent? {
    val v = item.value.trim()
    val uri = when {
        v.startsWith("http://", true) || v.startsWith("https://", true) -> Uri.parse(v)
        v.startsWith("tel:", true) || v.startsWith("mailto:", true) || v.startsWith("geo:", true) || v.startsWith("sms:", true) -> Uri.parse(v)
        item.type == "Link" -> Uri.parse("https://$v")
        else -> return null
    }
    return Intent(Intent.ACTION_VIEW, uri)
}

private fun copy(context: Context, text: String) {
    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("ScanFire code", text))
    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
}
