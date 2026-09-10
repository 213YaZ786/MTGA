package com.mtga.app.feature.timeline

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mtga.app.core.media.MediaDownloader
import com.mtga.app.ui.component.PostCard
import com.mtga.app.ui.icon.MtgaIcons
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineScreen(
    onOpenDiagnostics: () -> Unit,
    onOpenAccounts: () -> Unit,
    viewModel: TimelineViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()
    val uriHandler = LocalUriHandler.current
    val downloader: MediaDownloader = koinInject()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Timeline") },
                actions = {
                    IconButton(onClick = viewModel::refresh, enabled = !state.loading) {
                        if (state.loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(MtgaIcons.Refresh, contentDescription = "Refresh")
                        }
                    }
                }
            )
        }
    ) { padding ->
        when {
            state.followedCount == 0 -> EmptyState(
                title = "Nothing followed yet",
                message = "Add a few handles and they will all appear here in one stream.",
                actionLabel = "Open Accounts",
                onAction = onOpenAccounts,
                modifier = Modifier.padding(padding)
            )

            state.isEmpty && state.errors.isNotEmpty() -> EmptyState(
                title = "Nothing could be loaded",
                message = "Every followed account failed to fetch. Diagnostics will say which " +
                    "layer failed and why.",
                actionLabel = "Open Diagnostics",
                onAction = onOpenDiagnostics,
                modifier = Modifier.padding(padding)
            )

            state.posts.isEmpty() && state.loading -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            else -> {
                val listState = rememberLazyListState()

                // Prefetch a page before the reader actually hits the bottom,
                // so scrolling stays continuous instead of stalling.
                val shouldLoadMore by remember {
                    derivedStateOf {
                        val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                        last >= state.posts.size - LOAD_MORE_THRESHOLD
                    }
                }
                LaunchedEffect(shouldLoadMore, state.canLoadMore) {
                    if (shouldLoadMore) viewModel.loadMore()
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(padding)
                ) {
                if (state.errors.isNotEmpty()) {
                    item {
                        PartialFailureNotice(
                            failed = state.errors.keys.toList(),
                            onOpenDiagnostics = onOpenDiagnostics
                        )
                    }
                }

                items(state.posts, key = { it.id }) { post ->
                    PostCard(
                        post = post,
                        onClick = { uriHandler.openUri(post.permalink) },
                        onOpenLink = { uriHandler.openUri(it) },
                        onDownload = { downloader.download(it, post.authorHandle) }
                    )
                }

                item { TimelineFooter(state = state, onLoadMore = viewModel::loadMore) }
                }
            }
        }
    }
}

@Composable
private fun TimelineFooter(state: TimelineUiState, onLoadMore: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        when {
            state.loadingMore -> CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp
            )
            state.canLoadMore -> TextButton(onClick = onLoadMore) { Text("Load older posts") }
            else -> Text(
                "That is as far back as these instances will go.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private const val LOAD_MORE_THRESHOLD = 5

/**
 * Partial failure is the normal case with a fragile upstream, so it gets a
 * quiet line rather than a blocking error. The posts that did load stay
 * readable above everything.
 */
@Composable
private fun PartialFailureNotice(failed: List<String>, onOpenDiagnostics: () -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            text = if (failed.size == 1) {
                "@${failed.first()} could not be loaded. Showing what was cached."
            } else {
                "${failed.size} accounts could not be loaded. Showing what was cached."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        TextButton(onClick = onOpenDiagnostics) { Text("Why") }
    }
}

@Composable
private fun EmptyState(
    title: String,
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)
        )
        TextButton(onClick = onAction) { Text(actionLabel) }
    }
}
