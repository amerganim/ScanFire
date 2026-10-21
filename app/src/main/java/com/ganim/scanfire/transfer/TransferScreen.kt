package com.ganim.scanfire.transfer

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ganim.scanfire.AppContainer
import com.ganim.scanfire.export.PdfOptions
import com.ganim.scanfire.ui.common.appContainer
import com.ganim.scanfire.ui.common.formatDate
import com.ganim.scanfire.ui.common.safeFileName
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.net.Inet4Address

/**
 * Send documents to a computer on the same Wi-Fi: the phone serves a small web page while this
 * screen is open. Nothing goes through the internet or any cloud.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransferScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val container = appContainer()
    val view = LocalView.current
    var address by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        val ip = wifiAddress(context)
        val server = TransferServer(route = { path -> route(container, context, path) })
        if (ip == null) {
            error = "Connect the phone to Wi-Fi first. The computer must be on the same network."
        } else {
            runCatching { server.start() }
                .onSuccess { address = "http://$ip:${server.port}/${server.token}/" }
                .onFailure { error = "Couldn't start: ${it.message}" }
        }
        view.keepScreenOn = true
        onDispose {
            server.stop()
            view.keepScreenOn = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Send to computer") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Stop and go back") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val url = address
            if (url == null) {
                Icon(Icons.Outlined.WifiOff, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.error)
                Text(error ?: "Starting…", textAlign = TextAlign.Center)
            } else {
                Icon(Icons.Outlined.Computer, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                Text("On your computer, open a web browser and type:", textAlign = TextAlign.Center)
                Card(Modifier.fillMaxWidth()) {
                    SelectionContainer {
                        Text(
                            url, Modifier.padding(16.dp).fillMaxWidth(),
                            style = MaterialTheme.typography.titleLarge, fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                        )
                    }
                }
                Text(
                    "Then download any document as PDF or images. The computer must be on the same Wi-Fi. " +
                        "Keep this screen open; leaving it stops sharing. The code at the end keeps others on the network out.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** The phone's IPv4 address on Wi-Fi (or Ethernet/hotspot), if any. */
private fun wifiAddress(context: Context): String? {
    val cm = context.getSystemService(ConnectivityManager::class.java)
    val networks = cm.allNetworks.filter {
        val caps = cm.getNetworkCapabilities(it)
        caps != null && (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))
    }
    return networks.firstNotNullOfOrNull { n ->
        cm.getLinkProperties(n)?.linkAddresses?.map { it.address }?.firstOrNull { it is Inet4Address && !it.isLoopbackAddress }?.hostAddress
    }
}

/** Serves the document list and on-demand PDF / ZIP downloads. Runs on the server's worker threads. */
private fun route(container: AppContainer, context: Context, path: String): Reply = runBlocking {
    val docs = container.library.allDocuments().first()
    val s = container.settings.settings.first()
    when {
        path == "/" -> Reply.Html(page(context, docs))
        path.startsWith("/thumb/") -> {
            val id = path.removePrefix("/thumb/").removeSuffix(".jpg").toLongOrNull() ?: return@runBlocking Reply.NotFound
            val doc = docs.firstOrNull { it.id == id } ?: return@runBlocking Reply.NotFound
            val cover = doc.coverFile ?: return@runBlocking Reply.NotFound
            Reply.FileDownload(container.files.file(id, cover), "image/jpeg", "cover.jpg", inline = true)
        }
        path.startsWith("/doc/") -> {
            val file = path.removePrefix("/doc/")
            val id = file.substringBefore('.').toLongOrNull() ?: return@runBlocking Reply.NotFound
            val doc = docs.firstOrNull { it.id == id } ?: return@runBlocking Reply.NotFound
            val images = container.pages.pagesNow(id).map(container.pages::imageFile)
            val name = safeFileName(doc.name)
            when (file.substringAfter('.')) {
                "pdf" -> Reply.FileDownload(container.exporter.pdf(name, images, PdfOptions(s.pdfPageSize, s.pdfMargin, s.exportQuality)), "application/pdf", "$name.pdf")
                "zip" -> Reply.FileDownload(container.exporter.zip(name, images, s.exportQuality), "application/zip", "$name.zip")
                else -> Reply.NotFound
            }
        }
        else -> Reply.NotFound
    }
}

private fun page(context: Context, docs: List<com.ganim.scanfire.data.db.DocumentSummary>): String {
    val rows = if (docs.isEmpty()) "<p class=muted>No documents yet.</p>" else docs.joinToString("") { d ->
        val name = TransferServer.escape(d.name)
        val pages = if (d.pageCount == 1) "1 page" else "${d.pageCount} pages"
        """<div class=doc><img src="thumb/${d.id}.jpg" alt="" loading=lazy><div class=info><b>$name</b><span class=muted>$pages · ${TransferServer.escape(formatDate(context, d.updatedAt))}</span>
           <span><a href="doc/${d.id}.pdf">PDF</a> <a href="doc/${d.id}.zip">Images (ZIP)</a></span></div></div>"""
    }
    return """<!doctype html><html><head><meta charset=utf-8><meta name=viewport content="width=device-width,initial-scale=1"><title>ScanFire</title>
<style>body{font-family:system-ui,sans-serif;margin:0;padding:24px;background:#fffbf8;color:#231917}h1{color:#e8461e;margin-top:0}
.doc{display:flex;gap:16px;align-items:center;padding:12px;border-radius:12px;background:#fff;box-shadow:0 1px 3px #0002;margin:10px 0;max-width:720px}
.doc img{width:64px;height:84px;object-fit:cover;border-radius:6px;background:#eee}.info{display:flex;flex-direction:column;gap:4px}
.muted{color:#77574d;font-size:14px}a{display:inline-block;margin-right:12px;padding:6px 12px;border-radius:8px;background:#e8461e;color:#fff;text-decoration:none}
@media(prefers-color-scheme:dark){body{background:#1a1210;color:#f1dfda}.doc{background:#2a201d}.muted{color:#d8c2bc}}</style></head>
<body><h1>ScanFire</h1><p class=muted>${docs.size} documents. Files are made on the phone when you click.</p>$rows</body></html>"""
}
