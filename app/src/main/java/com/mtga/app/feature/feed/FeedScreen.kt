package com.mtga.app.feature.feed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
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

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                state.error?.let { error ->
                    item {
                        ErrorPanel(
                            error = error,
                            onRetry = viewModel::refresh,
                            onOpenDiagnostics = onOpenDiagnostics
                        )
                    }
                }

                items(feed.posts, key = { it.id }) { post ->
                    PostCard(post = post, onClick = { uriHandler.openUri(post.permalink) })
                }

                item {
                    Text(
                        "Served by ${feed.fetchedFromHost}. RSS carries roughly the last " +
                            "20 posts and no like or repost counts. Deeper history and stats " +
                            "arrive with the HTML source in step 5.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
