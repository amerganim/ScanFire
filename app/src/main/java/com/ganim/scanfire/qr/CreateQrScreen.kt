package com.ganim.scanfire.qr

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.ganim.scanfire.export.Sharing
import com.ganim.scanfire.ui.common.appContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class QrKind { Text, Wifi }

/** Make a QR code for text, a link, or Wi-Fi details; share it as an image. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateQrScreen(onBack: () -> Unit, initialText: String = "") {
    val context = LocalContext.current
    val files = appContainer().files
    val scope = rememberCoroutineScope()
    var kind by rememberSaveable { mutableStateOf(QrKind.Text) }
    var text by rememberSaveable { mutableStateOf(initialText) }
    var ssid by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var security by rememberSaveable { mutableStateOf(QrMaker.WifiSecurity.WPA) }
    var hidden by rememberSaveable { mutableStateOf(false) }

    val payload = when (kind) {
        QrKind.Text -> text.trim()
        QrKind.Wifi -> if (ssid.isBlank()) "" else QrMaker.wifiPayload(ssid, password, security, hidden)
    }
    val qr: Bitmap? = remember(payload) {
        if (payload.isEmpty()) null
        else runCatching { Bitmap.createBitmap(QrMaker.pixels(payload, 720), 720, 720, Bitmap.Config.ARGB_8888) }.getOrNull()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Create QR code") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = kind == QrKind.Text, onClick = { kind = QrKind.Text }, label = { Text("Text or link") })
                FilterChip(selected = kind == QrKind.Wifi, onClick = { kind = QrKind.Wifi }, label = { Text("Wi-Fi") })
            }
            when (kind) {
                QrKind.Text -> OutlinedTextField(text, { text = it }, label = { Text("Text, link, phone number…") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                QrKind.Wifi -> {
                    OutlinedTextField(ssid, { ssid = it }, label = { Text("Network name (SSID)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    if (security != QrMaker.WifiSecurity.None) {
                        OutlinedTextField(
                            password, { password = it }, label = { Text("Password") }, singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        QrMaker.WifiSecurity.entries.forEach { s -> FilterChip(selected = security == s, onClick = { security = s }, label = { Text(s.label) }) }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = hidden, onCheckedChange = { hidden = it })
                        Text("Hidden network")
                    }
                    Text("Anyone who scans this joins your Wi-Fi without typing the password.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (qr != null) {
                Image(
                    qr.asImageBitmap(), "QR code", filterQuality = FilterQuality.None,
                    modifier = Modifier.widthIn(max = 320.dp).fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp)).background(Color.White),
                )
                Button(onClick = {
                    scope.launch {
                        val out = withContext(Dispatchers.IO) {
                            files.freshExportFile("ScanFire-QR.png").also { f -> f.outputStream().use { qr.compress(Bitmap.CompressFormat.PNG, 100, it) } }
                        }
                        Sharing.share(context, listOf(out), "image/png", "QR code")
                    }
                }) { Icon(Icons.Outlined.Share, null, Modifier.size(18.dp)); Text("  Share image") }
            } else {
                Text("Type something to make a code.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
