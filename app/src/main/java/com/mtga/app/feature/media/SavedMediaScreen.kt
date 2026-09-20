package com.mtga.app.feature.media

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import android.text.format.Formatter
import com.mtga.app.navigation.LocalReadableInset
import com.mtga.app.ui.component.FloatingTopBar
import com.mtga.app.ui.icon.MtgaIcons
import org.koin.androidx.compose.koinViewModel

/**
 * What the app has saved so posts can be read with no network, and a way to
 * delete any of it.
 *
 * The files sit in the app's own folder, which Android 11 and later keep out
 * of the Files app, so a shortcut to the folder would lead nowhere on a modern
 * phone. This screen is that shortcut: the same list, reachable, and it knows
 * which account each file came from.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedMediaScreen(
    onBack: () -> Unit,
    onOpenPost: (String) -> Unit,
    viewModel: SavedMediaViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            FloatingTopBar(
                title = { Text("Saved media") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(MtgaIcons.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (state.groups.isEmpty()) {
                Text(
                    if (state.loading) {
                        "Reading the folder"
                    } else {
                        "Nothing saved yet. Turn on automatic saving in Settings, Media."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(32.dp)
                )
                return@Box
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = LocalReadableInset.current,
                    end = LocalReadableInset.current,
                    bottom = 24.dp
                )
            ) {
                item(key = "total") {
                    Text(
                        "${state.fileCount} files, " +
                            Formatter.formatShortFileSize(context, state.totalBytes),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
                    )
                }

                items(state.groups, key = { it.handle }) { group ->
                    AccountGroup(
                        group = group,
                        onOpenPost = onOpenPost,
                        onDeletePost = viewModel::deletePost,
                        onDeleteAccount = { viewModel.deleteAccount(group.handle) }
                    )
                }
            }
        }
    }
}

@Composable
private fun AccountGroup(
    group: SavedMediaGroup,
    onOpenPost: (String) -> Unit,
    onDeletePost: (String) -> Unit,
    onDeleteAccount: () -> Unit
) {
    val context = LocalContext.current

    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("@${group.handle}", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "${group.files} files, " +
                            Formatter.formatShortFileSize(context, group.bytes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = onDeleteAccount) { Text("Delete all") }
            }

            group.posts.forEach { post ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = { onOpenPost(post.postId) }) {
                        Text(
                            "${post.files} file${if (post.files == 1) "" else "s"}, " +
                                Formatter.formatShortFileSize(context, post.bytes)
                        )
                    }
                    IconButton(onClick = { onDeletePost(post.postId) }) {
                        Icon(MtgaIcons.Delete, contentDescription = "Delete this post's media")
                    }
                }
            }
        }
    }
}
