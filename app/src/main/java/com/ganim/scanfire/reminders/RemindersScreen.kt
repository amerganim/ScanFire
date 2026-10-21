package com.ganim.scanfire.reminders

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ganim.scanfire.data.db.DocumentEntity
import com.ganim.scanfire.ocr.TextInsights
import com.ganim.scanfire.ui.common.IconTile
import com.ganim.scanfire.ui.common.SectionLabel
import com.ganim.scanfire.ui.common.Tint
import com.ganim.scanfire.ui.common.appContainer
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import java.util.TimeZone

/** The latest future date printed on the page: on an ID or passport that's usually the expiry. */
fun suggestReminderDay(text: String?, now: Long = System.currentTimeMillis()): Long? =
    text?.lines()?.mapNotNull { TextInsights.findDate(it)?.timeInMillis }?.filter { it > now }?.maxOrNull()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemindersScreen(onBack: () -> Unit, onOpen: (DocumentEntity) -> Unit) {
    val reminders = appContainer().reminders
    val docs by reminders.all().collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    val now = System.currentTimeMillis()
    val (upcoming, past) = docs.partition { (it.remindAt ?: 0) >= now }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reminders") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            if (docs.isEmpty()) item {
                Text(
                    "No reminders yet. Open a document, like a passport or a bill, and choose Remind me from its ⋮ menu. " +
                        "ScanFire suggests the date printed on the page.",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                )
            }
            if (upcoming.isNotEmpty()) item { SectionLabel("Coming up") }
            items(upcoming, key = { it.id }) { ReminderRow(it, onOpen = { onOpen(it) }, onClear = { scope.launch { reminders.clear(it.id) } }) }
            if (past.isNotEmpty()) item { SectionLabel("Past") }
            items(past, key = { it.id }) { ReminderRow(it, onOpen = { onOpen(it) }, onClear = { scope.launch { reminders.clear(it.id) } }) }
        }
    }
}

@Composable
private fun ReminderRow(doc: DocumentEntity, onOpen: () -> Unit, onClear: () -> Unit) {
    Surface(
        onClick = onOpen,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 5.dp),
    ) {
        Row(Modifier.padding(start = 14.dp, top = 12.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            IconTile(if (doc.vault) Icons.Outlined.Lock else Icons.Outlined.Event, if (doc.vault) Tint.Violet else Tint.Ember)
            Column(Modifier.weight(1f)) {
                Text(if (doc.vault) "Vault document" else doc.name, style = MaterialTheme.typography.titleSmall)
                Text(
                    DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(doc.remindAt ?: 0)) + (doc.remindNote?.let { " · $it" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onClear) { Icon(Icons.Outlined.Close, "Remove reminder") }
        }
    }
}

/** Pick the day (suggested from the page when possible) and an optional note; asks for notification permission. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderDialog(suggestedDay: Long?, currentDay: Long?, currentNote: String?, onDismiss: () -> Unit, onSave: (day: Long, note: String) -> Unit) {
    val context = LocalContext.current
    val initial = currentDay ?: suggestedDay ?: (System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000)
    val state = rememberDatePickerState(initialSelectedDateMillis = localDayToUtc(initial))
    var note by remember { mutableStateOf(currentNote.orEmpty()) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    // A plain Dialog rather than DatePickerDialog: that one has a fixed height that squeezes the picker once a
    // note field is added. Here everything scrolls together and the buttons stay at the end.
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        ) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 12.dp)) {
                Text(
                    "Remind me on",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(start = 24.dp, top = 20.dp),
                )
                if (suggestedDay != null && currentDay == null) {
                    Text(
                        "Date found on the page",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 24.dp, top = 4.dp),
                    )
                }
                DatePicker(
                    state = state,
                    title = null,
                    headline = null,
                    showModeToggle = false,
                    colors = DatePickerDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                )
                OutlinedTextField(
                    note, { note = it },
                    label = { Text("Note (optional)") },
                    placeholder = { Text("Passport expires") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                )
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    TextButton(enabled = state.selectedDateMillis != null, onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !Reminders.canNotify(context)) {
                            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        onSave(utcToLocalDay(state.selectedDateMillis!!), note)
                    }) { Text("Remind me") }
                }
            }
        }
    }
}
/** The date picker works in UTC midnights; reminders are local days. */
private fun localDayToUtc(local: Long): Long {
    val l = Calendar.getInstance().apply { timeInMillis = local }
    return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear(); set(l.get(Calendar.YEAR), l.get(Calendar.MONTH), l.get(Calendar.DAY_OF_MONTH))
    }.timeInMillis
}

private fun utcToLocalDay(utc: Long): Long {
    val u = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utc }
    return Calendar.getInstance().apply {
        clear(); set(u.get(Calendar.YEAR), u.get(Calendar.MONTH), u.get(Calendar.DAY_OF_MONTH))
    }.timeInMillis
}
