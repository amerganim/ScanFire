package com.ganim.scanfire.settings

import android.content.Intent
import android.os.Process
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ganim.scanfire.BuildConfig
import com.ganim.scanfire.data.Backup
import com.ganim.scanfire.data.Settings
import com.ganim.scanfire.imaging.PageFilter
import com.ganim.scanfire.ocr.OcrLanguage
import com.ganim.scanfire.scan.findActivity
import com.ganim.scanfire.tools.formatSize
import com.ganim.scanfire.ui.BusyOverlay
import com.ganim.scanfire.ui.common.appContainer
import com.ganim.scanfire.ui.common.GroupCard
import com.ganim.scanfire.ui.common.GroupDivider
import com.ganim.scanfire.ui.common.GroupRow
import com.ganim.scanfire.ui.common.IconTile
import com.ganim.scanfire.ui.common.ScreenHeader
import com.ganim.scanfire.ui.common.SectionLabel
import com.ganim.scanfire.ui.common.Tint
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import com.ganim.scanfire.security.AppLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class Choice(val key: String, val label: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onManageLanguages: () -> Unit) {
    val container = appContainer()
    val repo = container.settings
    val settings by repo.settings.collectAsStateWithLifecycle(initialValue = Settings())
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var picker by remember { mutableStateOf<Triple<String, List<Choice>, Pair<String, (String) -> Unit>>?>(null) }
    var busy by remember { mutableStateOf<String?>(null) }
    var confirmRestore by remember { mutableStateOf<android.net.Uri?>(null) }
    var showAbout by remember { mutableStateOf(false) }
    val backup = remember { Backup(context, container.database) }

    val storage by produceState(0L, busy) {
        value = withContext(Dispatchers.IO) { container.files.docsRoot.walkTopDown().filter { it.isFile }.sumOf { it.length() } }
    }

    val backupTarget = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = "Creating backup…"
        scope.launch {
            val result = runCatching { backup.create(uri) }
            busy = null
            Toast.makeText(context, if (result.isSuccess) "Backup saved" else "Backup failed: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
        }
    }
    val restoreSource = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> confirmRestore = uri }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 28.dp)) {
            ScreenHeader("Settings")
            PrivacyBanner()

            SectionLabel("Appearance")
            GroupCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Theme", style = MaterialTheme.typography.bodyLarge)
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        listOf("Automatic", "Light", "Dark").forEachIndexed { i, label ->
                            SegmentedButton(
                                selected = settings.themeMode == i,
                                onClick = { scope.launch { repo.setTheme(i) } },
                                shape = SegmentedButtonDefaults.itemShape(i, 3),
                                // No checkmark: the filled segment already shows the choice, and labels stay unclipped.
                                icon = {},
                            ) { Text(label, maxLines = 1) }
                        }
                    }
                }
                GroupDivider()
                SwitchRow("Wallpaper colours", "Use your phone's colours (Android 12+)", settings.dynamicColor) { scope.launch { repo.setDynamicColor(it) } }
            }

            SectionLabel("Scanning")
            GroupCard {
                val filters = PageFilter.entries.map { Choice(it.key, it.label) }
                GroupRow("Filter for imported photos", PageFilter.of(settings.defaultFilter).label, onClick = {
                    picker = Triple("Filter for imported photos", filters, settings.defaultFilter to { k -> scope.launch { repo.setDefaultFilter(k) } })
                })
                GroupDivider()
                val installed by container.tessData.installed.collectAsStateWithLifecycle()
                val languages = (listOf<OcrLanguage>(OcrLanguage.Auto) + OcrLanguage.mlKit + OcrLanguage.tesseract.filter { it.code in installed }).map { Choice(it.key, it.label) }
                GroupRow("Text language", OcrLanguage.of(settings.ocrScript).label, onClick = {
                    picker = Triple("Text language", languages, settings.ocrScript to { k -> scope.launch { repo.setOcrScript(k) } })
                })
                GroupDivider()
                GroupRow(
                    "More languages",
                    "${OcrLanguage.tesseract.size} more, like Bengali, Arabic, Tamil and Russian · ${installed.size} downloaded",
                    onClick = onManageLanguages,
                )
            }

            SectionLabel("PDF and sharing")
            GroupCard {
                val sizes = listOf(Choice("a4", "A4"), Choice("letter", "Letter"), Choice("fit", "Same as the photo"))
                GroupRow("Page size", sizes.first { it.key == settings.pdfPageSize }.label, onClick = {
                    picker = Triple("Page size", sizes, settings.pdfPageSize to { k -> scope.launch { repo.setPdfPageSize(k) } })
                })
                GroupDivider()
                SwitchRow("Page margins", "White border around each page", settings.pdfMargin) { scope.launch { repo.setPdfMargin(it) } }
                GroupDivider()
                QualityRow(settings.exportQuality) { scope.launch { repo.setExportQuality(it) } }
            }

            SectionLabel("Privacy and security")
            GroupCard {
                SwitchRow("App lock", "Fingerprint, face or screen lock to open ScanFire", settings.appLock) { on ->
                    val activity = context.findActivity() as? FragmentActivity ?: return@SwitchRow
                    if (on && !AppLock.available(activity)) {
                        Toast.makeText(context, "Set up a screen lock or fingerprint on your phone first", Toast.LENGTH_LONG).show()
                    } else {
                        // Confirm it's really the owner before changing the lock either way.
                        AppLock.prompt(activity, if (on) "Turn on app lock" else "Turn off app lock", onSuccess = { scope.launch { repo.setAppLock(on) } })
                    }
                }
            }

            SectionLabel("Backup")
            GroupCard {
                GroupRow("Back up everything", "One ZIP file with all documents (${formatSize(storage)}). Keep it on Drive, a computer or a USB stick.", onClick = {
                    backupTarget.launch("ScanFire-backup-" + SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) + ".zip")
                })
                GroupDivider()
                GroupRow("Restore from backup", "Replaces everything in ScanFire with the backup", onClick = {
                    restoreSource.launch(arrayOf("application/zip", "application/octet-stream"))
                })
            }

            SectionLabel("About")
            GroupCard {
                GroupRow("ScanFire ${BuildConfig.VERSION_NAME}", "Free and open source. No ads, no account, no tracking.", onClick = { showAbout = true })
                GroupDivider()
                GroupRow("Privacy policy", null, onClick = {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(PRIVACY_POLICY_URL))) }
                })
            }
        }
        busy?.let { BusyOverlay(it) }
    }

    picker?.let { (title, choices, current) ->
        AlertDialog(
            onDismissRequest = { picker = null },
            title = { Text(title) },
            text = {
                Column {
                    choices.forEach { c ->
                        Row(Modifier.fillMaxWidth().clickable { current.second(c.key); picker = null }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = c.key == current.first, onClick = { current.second(c.key); picker = null })
                            Text(c.label)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { picker = null }) { Text("Close") } },
        )
    }

    confirmRestore?.let { uri ->
        AlertDialog(
            onDismissRequest = { confirmRestore = null },
            title = { Text("Restore backup?") },
            text = { Text("Everything currently in ScanFire will be replaced. ScanFire restarts to finish.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmRestore = null
                    busy = "Restoring…"
                    scope.launch {
                        val ok = runCatching { backup.stage(uri) }.getOrDefault(false)
                        if (!ok) {
                            busy = null
                            Toast.makeText(context, "That file isn't a ScanFire backup", Toast.LENGTH_LONG).show()
                            return@launch
                        }
                        // Restart so the staged files are swapped in before the database opens.
                        context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
                            context.startActivity(Intent.makeRestartActivityTask(it.component))
                        }
                        Process.killProcess(Process.myPid())
                    }
                }) { Text("Restore") }
            },
            dismissButton = { TextButton(onClick = { confirmRestore = null }) { Text("Cancel") } },
        )
    }

    if (showAbout) {
        AlertDialog(
            onDismissRequest = { showAbout = false },
            title = { Text("About ScanFire") },
            text = {
                Text(
                    "ScanFire keeps your documents on this phone. It has no servers, collects no data, and shows no ads.\n\n" +
                        "Scanning, text recognition, code scanning and translation use Google ML Kit on-device models, delivered through " +
                        "Google Play services. Translation downloads a language model once per language.\n\n" +
                        "Open-source libraries: AndroidX and Jetpack Compose (Apache 2.0), PdfBox-Android (Apache 2.0), Coil (Apache 2.0), " +
                        "Reorderable by Calvin Liang (Apache 2.0), Kotlin coroutines (Apache 2.0).\n\nScanFire itself is MIT licensed.",
                )
            },
            confirmButton = { TextButton(onClick = { showAbout = false }) { Text("OK") } },
        )
    }
}

const val PRIVACY_POLICY_URL = "https://amerganim.github.io/scanfire-privacy/"

@Composable
private fun PrivacyBanner() {
    val dark = com.ganim.scanfire.ui.theme.LocalIsDark.current
    val onBanner = if (dark) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSecondary
    Surface(
        color = if (dark) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.secondary,
        contentColor = onBanner,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
    ) {
        Row(Modifier.padding(18.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            IconTile(Icons.Outlined.Shield, Tint.Ember, size = 44)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Your documents stay on this phone", style = MaterialTheme.typography.titleSmall)
                Text("No account, no cloud, no ads. Nothing is uploaded.", style = MaterialTheme.typography.bodySmall, color = onBanner.copy(alpha = 0.75f))
            }
        }
    }
}
@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    GroupRow(title, subtitle, onClick = { onChange(!checked) }, trailing = { Switch(checked = checked, onCheckedChange = onChange) })
}

@Composable
private fun QualityRow(quality: Int, onChange: (Int) -> Unit) {
    var value by remember(quality) { mutableFloatStateOf(quality.toFloat()) }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Image quality: ${value.toInt()}%", style = MaterialTheme.typography.bodyLarge)
        Text("Lower makes smaller PDFs and images", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Slider(value = value, onValueChange = { value = it }, onValueChangeFinished = { onChange(value.toInt()) }, valueRange = 40f..100f, steps = 11)
    }
}
