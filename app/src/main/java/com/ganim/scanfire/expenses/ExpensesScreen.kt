package com.ganim.scanfire.expenses

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ganim.scanfire.data.db.ExpenseEntity
import com.ganim.scanfire.export.Sharing
import com.ganim.scanfire.ui.common.IconTile
import com.ganim.scanfire.ui.common.Tint
import com.ganim.scanfire.ui.common.appContainer
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.text.DateFormat
import java.text.DateFormatSymbols
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val ExpenseCategories = listOf("Food", "Shopping", "Bills", "Transport", "Health", "Home", "Other")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpensesScreen(onBack: () -> Unit, onOpenDocument: (Long) -> Unit) {
    val container = appContainer()
    val repo = container.expenses
    val items by repo.all().collectAsStateWithLifecycle(initialValue = emptyList())
    val months = remember(items) { ExpenseParser.byMonth(items) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<ExpenseEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Expenses") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    if (items.isNotEmpty()) IconButton(onClick = {
                        val file = container.files.freshExportFile("ScanFire expenses.csv")
                        file.writeText(ExpenseParser.csv(items))
                        Sharing.share(context, listOf(file), "text/csv", "ScanFire expenses")
                    }) { Icon(Icons.Outlined.IosShare, "Export as spreadsheet (CSV)") }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { editing = ExpenseEntity(documentId = null, amountMinor = 0, currency = ExpenseParser.localCurrency(), date = System.currentTimeMillis(), title = "") },
                icon = { Icon(Icons.Outlined.Add, null) },
                text = { Text("Add by hand") },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 96.dp)) {
            if (items.isEmpty()) item {
                Column(Modifier.fillMaxWidth().padding(top = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    IconTile(Icons.Outlined.ReceiptLong, Tint.Green, size = 64)
                    Text(
                        "Keep track of what you spend. Scan a receipt, then choose Add to expenses from its ⋮ menu: " +
                            "ScanFire reads the total and the date for you.",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
            }
            months.forEach { month ->
                item(key = "m${month.year}-${month.month}") {
                    Column(Modifier.padding(top = 18.dp, bottom = 8.dp)) {
                        Text(DateFormatSymbols.getInstance().months[month.month] + " " + month.year, style = MaterialTheme.typography.titleMedium)
                        Text(
                            month.totals.entries.joinToString("  +  ") { (cur, sum) -> ExpenseParser.format(sum, cur) },
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                item(key = "c${month.year}-${month.month}") {
                    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow, shadowElevation = 1.dp) {
                        Column {
                            month.items.forEachIndexed { i, e ->
                                if (i > 0) HorizontalDivider(Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                                Row(
                                    Modifier.fillMaxWidth().clickable { editing = e }.padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(e.title.ifEmpty { "Receipt" }, style = MaterialTheme.typography.bodyLarge)
                                        Text(
                                            DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(e.date)) + (if (e.category.isNotEmpty()) " · ${e.category}" else ""),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Text(ExpenseParser.format(e.amountMinor, e.currency), style = MaterialTheme.typography.titleSmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    editing?.let { e ->
        ExpenseDialog(
            initial = e,
            onDismiss = { editing = null },
            onOpenDocument = e.documentId?.let { id -> { editing = null; onOpenDocument(id) } },
            onDelete = if (e.id != 0L) ({ editing = null; scope.launch { repo.delete(e.id) } }) else null,
        ) { saved -> editing = null; scope.launch { repo.save(saved) } }
    }
}

/** Add or edit one expense. Amount, date and title arrive pre-filled from the receipt when possible. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExpenseDialog(
    initial: ExpenseEntity,
    onDismiss: () -> Unit,
    onOpenDocument: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onSave: (ExpenseEntity) -> Unit,
) {
    val day = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false } }
    var title by remember { mutableStateOf(initial.title) }
    var amount by remember { mutableStateOf(if (initial.amountMinor > 0) BigDecimal.valueOf(initial.amountMinor, 2).toPlainString() else "") }
    var currency by remember { mutableStateOf(initial.currency) }
    var date by remember { mutableStateOf(day.format(Date(initial.date))) }
    var category by remember { mutableStateOf(initial.category) }
    val minor = ExpenseParser.toMinor(amount)
    val parsedDate = runCatching { day.parse(date)?.time }.getOrNull()
    val valid = minor != null && minor > 0 && parsedDate != null && currency.length in 1..5

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id == 0L) "Add to expenses" else "Expense") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("What for") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        amount, { amount = it },
                        label = { Text("Amount") }, singleLine = true, isError = amount.isNotEmpty() && minor == null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(currency, { currency = it.uppercase().take(5) }, label = { Text("Currency") }, singleLine = true, modifier = Modifier.weight(0.7f))
                }
                OutlinedTextField(
                    date, { date = it },
                    label = { Text("Date (year-month-day)") }, singleLine = true, isError = parsedDate == null,
                    modifier = Modifier.fillMaxWidth(),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ExpenseCategories.forEach { c ->
                        FilterChip(selected = category == c, onClick = { category = if (category == c) "" else c }, label = { Text(c) })
                    }
                }
                if (onOpenDocument != null || onDelete != null) Row {
                    onOpenDocument?.let { TextButton(onClick = it) { Text("Open receipt") } }
                    onDelete?.let { TextButton(onClick = it) { Text("Delete", color = MaterialTheme.colorScheme.error) } }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                onSave(initial.copy(title = title.trim(), amountMinor = minor!!, currency = currency.trim(), date = parsedDate!!, category = category))
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
