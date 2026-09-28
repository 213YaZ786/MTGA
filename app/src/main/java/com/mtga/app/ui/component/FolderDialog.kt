package com.mtga.app.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp

/**
 * Which folder, with the option of a new one.
 *
 * A dialog and a row per choice, the way every other choice in MTGA is made.
 * [everything] is the row that means no folder at all, which on Home is every
 * account. Filing an account passes null, since an account always lands in a
 * folder. A new name that differs only in case from an existing folder opens
 * that folder, see FolderNames.
 */
@Composable
fun FolderDialog(
    title: String,
    folders: List<String>,
    selected: String?,
    everything: String?,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    var creating by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (creating) "New folder" else title) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                if (creating) {
                    // 0.6.51 of LinkedOut said here that a folder disappears
                    // with its last account. It has not since folders got
                    // their own file, and the sentence made people afraid to
                    // empty one.
                    Text(
                        "An empty folder stays until you delete it in Accounts, Folders.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    TextField(
                        value = name,
                        onValueChange = { name = it },
                        singleLine = true,
                        placeholder = { Text("Folder name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    everything?.let { Choice(it, selected == null) { onSelect(null) } }
                    folders.forEach { folder -> Choice(folder, selected == folder) { onSelect(folder) } }
                    TextButton(onClick = { creating = true }) { Text("New folder") }
                }
            }
        },
        confirmButton = {
            if (creating) {
                TextButton(onClick = { onSelect(name.trim()) }, enabled = name.isNotBlank()) { Text("Create") }
            } else {
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        },
        dismissButton = {
            if (creating) TextButton(onClick = { creating = false }) { Text("Back") }
        }
    )
}

@Composable
private fun Choice(label: String, chosen: Boolean, onChoose: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val choose = {
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        onChoose()
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = chosen, onClick = choose)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = chosen, onClick = choose)
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 8.dp))
    }
}
