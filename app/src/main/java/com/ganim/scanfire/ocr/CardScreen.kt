package com.ganim.scanfire.ocr

import android.content.ContentValues
import android.content.Intent
import android.provider.ContactsContract
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ganim.scanfire.ui.common.appContainer
import kotlinx.coroutines.flow.first

/**
 * Reads a scanned business card and pre-fills Android's "new contact" screen. ScanFire needs no
 * contacts permission: the system contacts app does the saving after the user checks the fields.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardScreen(docId: Long, onBack: () -> Unit) {
    val container = appContainer()
    val context = LocalContext.current
    var loading by remember { mutableStateOf(true) }
    var name by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var company by remember { mutableStateOf("") }
    var phones by remember { mutableStateOf("") }
    var emails by remember { mutableStateOf("") }
    var websites by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }

    LaunchedEffect(docId) {
        val page = container.pages.pagesNow(docId).firstOrNull()
        if (page != null) {
            val language = OcrLanguage.of(container.settings.settings.first().ocrScript)
            val text = page.ocrText ?: runCatching { container.ocr.recognize(container.pages.imageFile(page), language) }.getOrDefault("")
                .also { container.pages.setOcrText(page.id, it) }
            val card = CardParser.parse(text)
            name = card.name; title = card.title; company = card.company; address = card.address
            phones = card.phones.joinToString("\n"); emails = card.emails.joinToString("\n"); websites = card.websites.joinToString("\n")
            if (card.name.isNotBlank()) container.library.renameDocument(docId, "Card · ${card.name}")
        }
        loading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Business card") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (loading) {
                CircularProgressIndicator()
                Text("Reading the card…")
                return@Column
            }
            Text("Check the details, then add them to your contacts.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Field("Name", name) { name = it }
            Field("Job title", title) { title = it }
            Field("Company", company) { company = it }
            Field("Phone numbers (one per line)", phones, multi = true) { phones = it }
            Field("Emails (one per line)", emails, multi = true) { emails = it }
            Field("Websites (one per line)", websites, multi = true) { websites = it }
            Field("Address", address, multi = true) { address = it }
            Button(
                enabled = listOf(name, phones, emails).any { it.isNotBlank() },
                onClick = {
                    val intent = Intent(ContactsContract.Intents.Insert.ACTION).apply {
                        type = ContactsContract.RawContacts.CONTENT_TYPE
                        putExtra(ContactsContract.Intents.Insert.NAME, name.trim())
                        putExtra(ContactsContract.Intents.Insert.JOB_TITLE, title.trim())
                        putExtra(ContactsContract.Intents.Insert.COMPANY, company.trim())
                        putExtra(ContactsContract.Intents.Insert.POSTAL, address.trim())
                        // Every phone, email and website as its own data row.
                        val rows = ArrayList<ContentValues>()
                        lines(phones).forEach { rows += ContentValues().apply { put(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE); put(ContactsContract.CommonDataKinds.Phone.NUMBER, it) } }
                        lines(emails).forEach { rows += ContentValues().apply { put(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE); put(ContactsContract.CommonDataKinds.Email.ADDRESS, it) } }
                        lines(websites).forEach { rows += ContentValues().apply { put(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Website.CONTENT_ITEM_TYPE); put(ContactsContract.CommonDataKinds.Website.URL, it) } }
                        putParcelableArrayListExtra(ContactsContract.Intents.Insert.DATA, rows)
                    }
                    runCatching { context.startActivity(intent) }.onFailure { Toast.makeText(context, "No contacts app found", Toast.LENGTH_LONG).show() }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Icon(Icons.Outlined.PersonAdd, null, Modifier.size(18.dp)); Text("  Add to contacts") }
            Text("The card is also kept in your documents.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun lines(s: String) = s.lines().map { it.trim() }.filter { it.isNotEmpty() }

@Composable
private fun Field(label: String, value: String, multi: Boolean = false, onChange: (String) -> Unit) {
    OutlinedTextField(value, onChange, label = { Text(label) }, singleLine = !multi, minLines = if (multi) 1 else 1, modifier = Modifier.fillMaxWidth())
}
