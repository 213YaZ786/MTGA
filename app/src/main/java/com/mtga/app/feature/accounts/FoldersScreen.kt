package com.mtga.app.feature.accounts

import com.mtga.app.ui.component.plus
import com.mtga.app.ui.component.BoldButton
import com.mtga.app.ui.theme.zone
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mtga.app.core.model.FollowedAccount
import com.mtga.app.navigation.LocalReadableInset
import com.mtga.app.ui.component.Avatar
import com.mtga.app.ui.component.FloatingTopBar
import com.mtga.app.ui.icon.MtgaIcons
import org.koin.androidx.compose.koinViewModel

/**
 * Folders, and what is in them.
 *
 * A folder exists as soon as it is created, empty, and Home can show it at
 * once. One folder holds an account, never two, so filing is moving: opening
 * a folder lists every account you follow, a tap moves one in, a second tap
 * sends it back to Main. Main is where an account lands when it has been put
 * nowhere, so it can be neither renamed nor deleted.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoldersScreen(
    onBack: () -> Unit,
    viewModel: AccountsViewModel = koinViewModel()
) {
    val rows by viewModel.rows.collectAsState()
    val folders by viewModel.folders.collectAsState()
    var open by remember { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<String?>(null) }
    val haptics = LocalHapticFeedback.current

    if (creating) {
        NameDialog(
            title = "New folder",
            initial = "",
            confirm = "Create",
            onConfirm = { name ->
                creating = false
                // Written now, empty, so Home can offer it before anything is
                // filed in it, and opened so filing can start right away.
                open = viewModel.createFolder(name)
            },
            onDismiss = { creating = false }
        )
    }

    renaming?.let { from ->
        NameDialog(
            title = "Rename $from",
            initial = from,
            confirm = "Rename",
            onConfirm = { name ->
                // The store answers with the name the folder really has now,
                // which after a merge is the other folder's spelling. LinkedOut
                // kept the typed name open and showed a folder that no longer
                // existed.
                val renamed = viewModel.renameFolder(from, name)
                if (open == from && renamed != null) open = renamed
                renaming = null
            },
            onDismiss = { renaming = null }
        )
    }

    deleting?.let { name ->
        val count = rows.count { it.folder == name }
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete $name?") },
            text = {
                Text(
                    if (count == 0) {
                        "Nothing is filed here."
                    } else {
                        "Its ${if (count == 1) "account goes" else "$count accounts go"} back " +
                            "to ${FollowedAccount.MAIN}. Nothing is unfollowed."
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.deleteFolder(name)
                    if (open == name) open = null
                    deleting = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } }
        )
    }

    Scaffold(
        topBar = {
            FloatingTopBar(
                title = { Text("Folders") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(MtgaIcons.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    IconButton(onClick = { creating = true }) {
                        Icon(MtgaIcons.Add, contentDescription = "New folder")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp + LocalReadableInset.current,
                end = 16.dp + LocalReadableInset.current,
                top = 8.dp,
                bottom = 24.dp
            ).plus(padding),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(folders, key = { it }) { name ->
                FolderZone(
                    name = name,
                    rows = rows,
                    open = open == name,
                    onToggle = { open = if (open == name) null else name },
                    onRename = { renaming = name },
                    onDelete = { deleting = name },
                    onFile = { row ->
                        // Filing is moving, so it answers like something taken.
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.setFolder(row.handle, if (row.folder == name) FollowedAccount.MAIN else name)
                    }
                )
            }
        }
    }
}

@Composable
private fun FolderZone(
    name: String,
    rows: List<AccountRow>,
    open: Boolean,
    onToggle: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onFile: (AccountRow) -> Unit
) {
    val count = rows.count { it.folder == name }
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.zone,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        when (count) {
                            0 -> "Empty"
                            1 -> "1 account"
                            else -> "$count accounts"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (name != FollowedAccount.MAIN) {
                    BoldButton(onClick = onRename) { Text("Rename") }
                    IconButton(onClick = onDelete) {
                        Icon(MtgaIcons.Delete, contentDescription = "Delete $name", modifier = Modifier.size(20.dp))
                    }
                }
                BoldButton(onClick = onToggle) { Text(if (open) "Close" else "Open") }
            }

            AnimatedVisibility(visible = open) {
                Column(Modifier.padding(bottom = 8.dp)) {
                    if (rows.isEmpty()) {
                        Text(
                            "Follow an account first, then file it here.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(16.dp)
                        )
                    }
                    rows.forEach { row ->
                        val here = row.folder == name
                        TextButton(
                            onClick = { onFile(row) },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                        ) {
                            Avatar(url = row.avatarUrl, name = row.name ?: row.handle, size = 28.dp)
                            Text(
                                row.name ?: "@${row.handle}",
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f).padding(start = 10.dp)
                            )
                            if (here) {
                                Icon(MtgaIcons.Check, contentDescription = "Filed here", modifier = Modifier.size(18.dp))
                            } else {
                                Text(
                                    row.folder,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NameDialog(
    title: String,
    initial: String,
    confirm: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            TextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                placeholder = { Text("Folder name") },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) { Text(confirm) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
