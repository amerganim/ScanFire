package com.ganim.scanfire.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.ganim.scanfire.data.db.FolderEntity

@Composable
fun TextInputDialog(
    title: String,
    initial: String = "",
    label: String = "Name",
    confirm: String = "Save",
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember { mutableStateOf(TextFieldValue(initial, TextRange(0, initial.length))) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text(label) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        },
        confirmButton = {
            TextButton(enabled = value.text.isNotBlank(), onClick = { onConfirm(value.text.trim()) }) { Text(confirm) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirm: String = "Delete",
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Picks a destination folder; `null` means the top level. [excluded] hides a folder being moved. */
@Composable
fun FolderPickerDialog(
    folders: List<FolderEntity>,
    excluded: Set<Long> = emptySet(),
    onDismiss: () -> Unit,
    onPick: (Long?) -> Unit,
) {
    val byParent = remember(folders) { folders.groupBy { it.parentId } }
    // Depth-first so nested folders appear under their parent, indented.
    val rows = remember(folders, excluded) {
        buildList {
            fun walk(parent: Long?, depth: Int) {
                byParent[parent].orEmpty().forEach {
                    if (it.id in excluded) return@forEach
                    add(it to depth)
                    walk(it.id, depth + 1)
                }
            }
            walk(null, 0)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Move to") },
        text = {
            LazyColumn(Modifier.heightIn(max = 400.dp)) {
                item {
                    PickerRow(depth = 0, name = "Documents (top level)", home = true) { onPick(null) }
                }
                items(rows, key = { it.first.id }) { (folder, depth) ->
                    PickerRow(depth = depth, name = folder.name) { onPick(folder.id) }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun PickerRow(depth: Int, name: String, home: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = (depth * 20).dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(if (home) Icons.Outlined.Home else Icons.Outlined.Folder, contentDescription = null)
        Text(name, Modifier.padding(start = 12.dp))
    }
}
