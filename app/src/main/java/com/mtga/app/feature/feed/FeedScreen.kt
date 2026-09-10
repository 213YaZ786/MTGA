package com.mtga.app.feature.feed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
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
import com.mtga.app.core.media.MediaDownloader
import org.koin.compose.koinInject
import androidx.compose.ui.unit.dp
import com.mtga.app.ui.component.ErrorPanel
import com.mtga.app.ui.component.PostCard
import com.mtga.app.ui.icon.MtgaIcons
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    handle: String,
    onBack: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    viewModel: FeedViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()
    val uriHandler = LocalUriHandler.current
    val downloader: MediaDownloader = koinInject()

    LaunchedEffect(handle) { viewModel.load(handle) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(state.feed?.displayName?.takeIf { it != handle } ?: "@$handle")
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(MtgaIcons.ArrowBack, contentDescription = "Back")
                    }
                },
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
        val feed = state.feed

        when {
            state.error != null && feed == null -> Box(
                Modifier.fillMaxSize().padding(padding).padding(16.dp)
            ) {
                ErrorPanel(
                    error = state.error!!,
                    onRetry = viewModel::refresh,
                    onOpenDiagnostics = onOpenDiagnostics
                )
            }

            feed == null -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }

            else -> {
                val listState = rememberLazyListState()
                val shouldLoadMore by remember {
                    derivedStateOf {
                        val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                        last >= feed.posts.size - 5
                    }
                }
                LaunchedEffect(shouldLoadMore, state.canLoadMore) {
                    if (shouldLoadMore) viewModel.loadMore()
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(padding)
                ) {
                state.error?.let { error ->
                    item {
                        ErrorPanel(
                            modifier = Modifier.padding(16.dp),
                            error = error,
                            onRetry = viewModel::refresh,
                            onOpenDiagnostics = onOpenDiagnostics
                        )
                    }
                }

                items(feed.posts, key = { it.id }) { post ->
                    PostCard(
                        post = post,
                        onClick = { uriHandler.openUri(post.permalink) },
                        onOpenLink = { uriHandler.openUri(it) },
                        onDownload = { downloader.download(it, post.authorHandle) }
                    )
                }

                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        when {
                            state.loadingMore -> CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                            state.canLoadMore -> TextButton(onClick = viewModel::loadMore) {
                                Text("Load older posts")
                            }
                            else -> Text(
                                "Served by ${feed.fetchedFromHost}. No older posts available.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                }
            }
        }
    }
}
