package com.ganim.scanfire.security

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ganim.scanfire.data.db.PageEntity
import com.ganim.scanfire.document.ZoomableImage
import com.ganim.scanfire.scan.findActivity
import com.ganim.scanfire.ui.BusyOverlay
import com.ganim.scanfire.ui.common.BarItem
import com.ganim.scanfire.ui.common.ConfirmDialog
import com.ganim.scanfire.ui.common.InkActionBar
import com.ganim.scanfire.ui.common.IconTile
import com.ganim.scanfire.ui.common.Tint
import com.ganim.scanfire.ui.common.appContainer
import com.ganim.scanfire.ui.common.formatDate
import kotlinx.coroutines.launch

/** Unlocked until ScanFire goes to the background; shared by the vault list and the vault viewer. */
object VaultSession {
    var unlocked by mutableStateOf(false)
}

/**
 * Locks the vault when the activity stops (home button, app switch, screen off). This must watch the activity,
 * not the screen: moving from the vault list to a document stops the list's own back-stack entry too.
 */
@Composable
private fun LockVaultWhenAppStops() {
    val activity = LocalContext.current.findActivity() as? androidx.lifecycle.LifecycleOwner ?: return
    androidx.compose.runtime.DisposableEffect(activity) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) VaultSession.unlocked = false
        }
        activity.lifecycle.addObserver(observer)
        onDispose { activity.lifecycle.removeObserver(observer) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultScreen(onBack: () -> Unit, onOpen: (Long) -> Unit) {
    val context = LocalContext.current
    val vault = appContainer().vault
    val docs by vault.documents().collectAsStateWithLifecycle(initialValue = null)
    var error by remember { mutableStateOf<String?>(null) }
    LockVaultWhenAppStops()

    fun unlock() {
        val activity = context.findActivity() as? FragmentActivity ?: return
        if (!AppLock.available(activity)) {
            error = "Set up a screen lock or fingerprint on your phone to use the vault."
            return
        }
        AppLock.prompt(activity, "Open private vault", onSuccess = { VaultSession.unlocked = true; error = null }, onFail = { error = it })
    }
    LaunchedEffect(Unit) { if (!VaultSession.unlocked) unlock() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Private vault") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        if (!VaultSession.unlocked) {
            Column(
                Modifier.padding(padding).fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                IconTile(Icons.Outlined.Lock, Tint.Violet, size = 72)
                Spacer(Modifier.height(20.dp))
                Text("Locked", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(8.dp))
                Text(
                    error ?: "Use your fingerprint, face or screen lock to see what's inside.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(24.dp))
                Button(onClick = ::unlock) { Text("Unlock") }
            }
            return@Scaffold
        }
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    "Documents here are encrypted on this phone and hidden from your library and search. " +
                        "They aren't included in backups, so move a document out before switching phones.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
            val list = docs
            when {
                list == null -> Unit
                list.isEmpty() -> item {
                    Text(
                        "The vault is empty. Open a document and choose Move to vault from its ⋮ menu.",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(top = 32.dp).fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                }
                else -> items(list, key = { it.id }) { doc ->
                    Surface(
                        onClick = { onOpen(doc.id) },
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shadowElevation = 1.dp,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconTile(Icons.Outlined.Lock, Tint.Violet)
                            Column(Modifier.weight(1f)) {
                                Text(doc.name, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    (if (doc.pageCount == 1) "1 page" else "${doc.pageCount} pages") + " · " + formatDate(context, doc.updatedAt),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Read-only viewer: pages are decrypted into memory one at a time and never written out as plain files. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultDocumentScreen(docId: Long, onBack: () -> Unit, onMovedOut: (Long) -> Unit) {
    val container = appContainer()
    val vault = container.vault
    val scope = rememberCoroutineScope()
    LockVaultWhenAppStops()
    // Locked while away: go back to the vault list, which asks again.
    LaunchedEffect(VaultSession.unlocked) { if (!VaultSession.unlocked) onBack() }

    val doc by container.library.document(docId).collectAsStateWithLifecycle(initialValue = null)
    val pages by produceState<List<PageEntity>?>(null, docId) { value = vault.pages(docId) }
    var busy by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val list = pages.orEmpty()
    val pager = rememberPagerState { list.size }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(doc?.name.orEmpty(), style = MaterialTheme.typography.titleMedium, maxLines = 1)
                            if (list.isNotEmpty()) Text("${pager.currentPage + 1} of ${list.size}", style = MaterialTheme.typography.bodySmall)
                        }
                    },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                )
            },
            bottomBar = {
                InkActionBar(
                    listOf(
                        BarItem("Delete", Icons.Outlined.DeleteForever) { confirmDelete = true },
                        BarItem("Move out", Icons.Outlined.LockOpen, highlighted = true) {
                            busy = "Decrypting…"
                            scope.launch { vault.moveOut(docId); busy = null; onMovedOut(docId) }
                        },
                    ),
                )
            },
        ) { padding ->
            if (pages == null) {
                Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else {
                HorizontalPager(pager, key = { list[it].id }, modifier = Modifier.padding(padding).fillMaxSize()) { index ->
                    val bytes by produceState<ByteArray?>(null, list[index].id) { value = runCatching { vault.read(list[index]) }.getOrNull() }
                    bytes?.let { ZoomableImage(it) } ?: Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
            }
        }
        busy?.let { BusyOverlay(it) }
    }

    if (confirmDelete) {
        ConfirmDialog(
            "Delete for good?",
            "Vault documents skip the Recycle bin. This can't be undone.",
            confirm = "Delete",
            onDismiss = { confirmDelete = false },
        ) {
            confirmDelete = false
            scope.launch { container.library.deleteForever(listOf(docId)); onBack() }
        }
    }
}
