package com.ganim.scanfire.ocr

import android.widget.Toast
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ganim.scanfire.tools.formatSize
import com.ganim.scanfire.ui.common.appContainer
import kotlinx.coroutines.launch

/** Download or remove Tesseract languages. ML Kit's five scripts are always there. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OcrLanguagesScreen(onBack: () -> Unit) {
    val tessData = appContainer().tessData
    val installed by tessData.installed.collectAsStateWithLifecycle()
    val progress by tessData.progress.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // Downloads outlive this screen's composition only as long as the app process; that's fine for a few MB.
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    val shown = OcrLanguage.tesseract.filter {
        query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) || it.native.contains(query.trim(), ignoreCase = true)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Text recognition languages") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Text(
                    "Always available (Google ML Kit): " + OcrLanguage.mlKit.joinToString { it.script.label.substringBefore(" (") } +
                        ". Latin alone covers English, Spanish, French, German, Portuguese, Italian, Indonesian, Malay, Swahili and many more.\n\n" +
                        "Download others below. Each is a one-time download from the free Tesseract project, then works offline.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search languages") },
                    leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
                HorizontalDivider(Modifier.padding(top = 12.dp))
            }
            items(shown, key = { it.code }) { lang ->
                val have = lang.code in installed
                val pct = progress[lang.code]
                ListItem(
                    headlineContent = { Text(lang.label) },
                    supportingContent = {
                        Text(
                            when {
                                pct != null -> "Downloading… ${(pct * 100).toInt()}%"
                                have -> "Downloaded · ${formatSize(tessData.sizeOf(lang.code))}"
                                else -> "Not downloaded"
                            },
                        )
                    },
                    leadingContent = { if (have) Icon(Icons.Outlined.CheckCircle, null, tint = MaterialTheme.colorScheme.primary) },
                    trailingContent = {
                        when {
                            pct != null -> CircularProgressIndicator(progress = { pct }, modifier = Modifier.size(24.dp))
                            have -> IconButton(onClick = { tessData.delete(lang.code) }) { Icon(Icons.Outlined.Delete, "Remove ${lang.name}") }
                            else -> IconButton(onClick = {
                                scope.launch {
                                    runCatching {
                                        tessData.download(lang.code)
                                        if (lang.code != "eng") tessData.download("eng") // helps with mixed pages
                                    }.onFailure { Toast.makeText(context, "Download failed: ${it.message}", Toast.LENGTH_LONG).show() }
                                }
                            }) { Icon(Icons.Outlined.Download, "Download ${lang.name}") }
                        }
                    },
                )
            }
        }
    }
}
