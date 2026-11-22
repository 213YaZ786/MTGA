package com.mtga.app.feature.accounts

import com.mtga.app.ui.component.ZoneAlertDialog
import com.mtga.app.ui.component.ZoneSurface
import androidx.compose.ui.graphics.Color
import com.mtga.app.ui.component.rememberHaptics
import com.mtga.app.ui.component.plus
import com.mtga.app.ui.component.BoldButton
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ShapeDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.layout.heightIn
import com.mtga.app.ui.component.BoldIconButton
import com.mtga.app.ui.component.QuietButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mtga.app.core.model.FollowedAccount
import com.mtga.app.navigation.LocalReadableInset
import com.mtga.app.ui.component.Avatar
import com.mtga.app.ui.component.BarTitle
import com.mtga.app.ui.component.MovableAction
import com.mtga.app.ui.component.TopActionSize
import com.mtga.app.data.settings.SettingsStore
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import com.mtga.app.ui.icon.MtgaIcons
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

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
@Suppress("UNUSED_PARAMETER")
fun FoldersScreen(
    // Kept for the caller: the system back gesture does the going back now, the arrow is gone.
    onBack: () -> Unit,
    viewModel: AccountsViewModel = koinViewModel(),
    store: SettingsStore = koinInject()
) {
    val rows by viewModel.rows.collectAsState()
    val folders by viewModel.folders.collectAsState()
    var open by remember { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<String?>(null) }
    val haptics = rememberHaptics()

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
        ZoneAlertDialog(
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
                BoldButton(filled = true, onClick = {
                    haptics.done()
                    viewModel.deleteFolder(name)
                    if (open == name) open = null
                    deleting = null
                }) { Text("Delete") }
            },
            dismissButton = { QuietButton(onClick = { deleting = null }) { Text("Cancel") } }
        )
    }

    // The system gesture goes back, no bar and no arrow. The page's ground is painted under the whole app, see MainActivity.
    val settings by store.settings.collectAsState()
    val density = LocalDensity.current
    val statusTop = with(density) { WindowInsets.statusBars.getTop(density).toDp() }
    val navigationBottom = with(density) { WindowInsets.navigationBars.getBottom(density).toDp() }
    Box(Modifier.fillMaxSize()) {
        val listState = rememberLazyListState()
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            // Room under the last folder for the button: its 48 dp and the 16 dp around it.
            contentPadding = PaddingValues(
                start = 16.dp + LocalReadableInset.current,
                end = 16.dp + LocalReadableInset.current,
                top = 0.dp,
                bottom = navigationBottom + 16.dp + TopActionSize + 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // The title on the page, no zone, first in the list: it scrolls away with it.
            item(key = "title") {
                Box(
                    Modifier.fillMaxWidth().padding(top = statusTop + 16.dp, bottom = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.primary) { BarTitle("Folders") }
                }
            }
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
                        haptics.firm()
                        viewModel.setFolder(row.handle, if (row.folder == name) FollowedAccount.MAIN else name)
                    }
                )
            }
        }
        MovableAction(
            icon = MtgaIcons.Add,
            label = "New folder",
            snapFraction = settings.foldersButtonSnap,
            onSnapFraction = { fraction -> store.update { it.copy(foldersButtonSnap = fraction) } },
            onClick = { creating = true }
        )
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
    ZoneSurface(
        shape = ShapeDefaults.ExtraLarge,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                // The buttons keep a gap so they never touch: SMS's PersonActions (TileGap, 10 dp).
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    // https://github.com/androidx/androidx/blob/androidx-main/compose/animation/animation/samples/src/main/java/androidx/compose/animation/samples/AnimatedContentSamples.kt#SimpleAnimatedContentSample
                    // not ported: foo, bar, baz (the sample's own demo functions), simpleAnimatedContentSample (used here, inline)
                    // The label changes by fade through, never at once.
                    AnimatedContent(
                        targetState = when (count) {
                            0 -> "Empty"
                            1 -> "1 account"
                            else -> "$count accounts"
                        },
                        transitionSpec = {
                            (fadeIn(tween(110, delayMillis = 90)) togetherWith fadeOut(tween(90)))
                                .using(SizeTransform(clip = false))
                        },
                        label = "folderCount"
                    ) { label ->
                        Text(
                            label,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (name != FollowedAccount.MAIN) {
                    BoldButton(onClick = onRename) { Text("Rename") }
                    BoldIconButton(onClick = onDelete) {
                        Icon(MtgaIcons.Delete, contentDescription = "Delete $name", modifier = Modifier.size(20.dp))
                    }
                }
                BoldButton(onClick = onToggle) {
                    // Same fade through as the count label (SimpleAnimatedContentSample).
                    AnimatedContent(
                        targetState = open,
                        transitionSpec = {
                            (fadeIn(tween(110, delayMillis = 90)) togetherWith fadeOut(tween(90)))
                                .using(SizeTransform(clip = false))
                        },
                        label = "folderToggle"
                    ) { isOpen ->
                        Text(if (isOpen) "Close" else "Open")
                    }
                }
            }

            AnimatedVisibility(visible = open) {
                Column(Modifier.padding(bottom = 8.dp)) {
                    AnimatedVisibility(visible = rows.isEmpty()) {
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
                        // A row inside the folder's zone: a transparent tap target, never a
                        // second glass zone (Modules, "Liquid glass, as built").
                        Surface(
                            onClick = { onFile(row) },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                            shape = ButtonDefaults.textShape,
                            color = Color.Transparent,
                            contentColor = MaterialTheme.colorScheme.primary
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = ButtonDefaults.MinHeight)
                                    .padding(ButtonDefaults.TextButtonContentPadding),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Avatar(url = row.avatarUrl, name = row.name ?: row.handle, size = 28.dp)
                                Text(
                                    row.name ?: "@${row.handle}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f).padding(start = 10.dp)
                                )
                                // Same fade through (SimpleAnimatedContentSample).
                                AnimatedContent(
                                    targetState = here,
                                    transitionSpec = {
                                        (fadeIn(tween(110, delayMillis = 90)) togetherWith fadeOut(tween(90)))
                                            .using(SizeTransform(clip = false))
                                    },
                                    label = "filedHere"
                                ) { isHere ->
                                    if (isHere) {
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
    val state = com.mtga.app.ui.component.rememberDialogNameState(initial, selectAll = initial.isNotEmpty())
    val name = state.text.toString()
    ZoneAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            com.mtga.app.ui.component.DialogNameField(state, "Folder name", onDone = { if (name.isNotBlank()) onConfirm(name) })
        },
        confirmButton = {
            BoldButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank(), filled = true) { Text(confirm) }
        },
        dismissButton = { QuietButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
