package com.mtga.app.feature.feed

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mtga.app.core.media.MediaDownloader
import com.mtga.app.data.settings.SettingsStore
import com.mtga.app.core.model.Feed
import com.mtga.app.core.model.MediaItem
import com.mtga.app.core.model.MediaType
import com.mtga.app.core.model.Post
import com.mtga.app.feature.media.MediaViewer
import com.mtga.app.ui.component.Avatar
import com.mtga.app.ui.component.ErrorPanel
import com.mtga.app.ui.component.PostCard
import com.mtga.app.ui.component.relativeTime
import com.mtga.app.ui.icon.MtgaIcons
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

/**
 * One account: a centred profile header, then its posts.
 *
 * The header is always there, even before anything loaded, so you can follow
 * an account whose feed is momentarily unreachable. The top bar stays quiet
 * and only shows the name once the header has scrolled away.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    handle: String,
    onBack: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenPost: (Post) -> Unit,
    viewModel: FeedViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()
    val followed by viewModel.followed.collectAsState()
    val isFollowing = followed.any { it.handle.equals(handle, ignoreCase = true) }
    val uriHandler = LocalUriHandler.current
    val downloader: MediaDownloader = koinInject()
    val settingsStore: SettingsStore = koinInject()
    val settings by settingsStore.settings.collectAsState()
    val listState = rememberLazyListState()
    var viewing by remember { mutableStateOf<Pair<List<MediaItem>, Int>?>(null) }

    val feed = state.feed
    val name = feed?.displayName?.takeIf { it.isNotBlank() && it != handle }
    val headerGone by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }

    viewing?.let { (media, index) ->
        MediaViewer(
            media = media,
            startIndex = index,
            onDownload = { downloader.download(it, handle) },
            onDismiss = { viewing = null }
        )
    }

    LaunchedEffect(handle) { viewModel.load(handle) }

    if (feed != null) {
        val shouldLoadMore by remember(feed.posts.size) {
            derivedStateOf {
                val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                last >= feed.posts.size - 5
            }
        }
        LaunchedEffect(shouldLoadMore, state.canLoadMore, state.pagingFailed) {
            if (shouldLoadMore) viewModel.loadMore()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (headerGone) {
                        Text(name ?: "@$handle", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(MtgaIcons.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::refresh, enabled = !state.loading) {
                        if (state.loading) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(MtgaIcons.Refresh, contentDescription = "Refresh")
                        }
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            item(key = "header") {
                ProfileHeader(
                    handle = handle,
                    name = name,
                    feed = feed,
                    isFollowing = isFollowing,
                    onToggleFollow = viewModel::toggleFollow,
                    onOpenAvatar = { small ->
                        viewing = listOf(
                            MediaItem(previewUrl = small, downloadUrl = largeAvatar(small), type = MediaType.PHOTO)
                        ) to 0
                    }
                )
            }

            state.error?.let { error ->
                item(key = "error") {
                    ErrorPanel(
                        modifier = Modifier.padding(16.dp),
                        error = error,
                        onRetry = viewModel::refresh,
                        onOpenDiagnostics = onOpenDiagnostics,
                        onVerify = viewModel::verify
                    )
                }
            }

            if (feed == null) {
                if (state.loading) {
                    item(key = "loading") {
                        Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                }
                return@LazyColumn
            }

            items(feed.posts, key = { it.id }) { post ->
                PostCard(
                    post = post,
                    onClick = { onOpenPost(post) },
                    onOpenLink = { uriHandler.openUri(it) },
                    onDownload = { downloader.download(it, post.authorHandle) },
                    showStats = settings.showCounts,
                            onOpenMedia = { index -> viewing = post.media to index }
                )
            }

            item(key = "footer") {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        state.loadingMore -> CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                        state.canLoadMore -> TextButton(onClick = { viewModel.loadMore(manual = true) }) {
                            Text(if (state.pagingFailed) "Try again" else "Load older posts")
                        }
                        else -> Text(
                            "No older posts available.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileHeader(
    handle: String,
    name: String?,
    feed: Feed?,
    isFollowing: Boolean,
    onToggleFollow: () -> Unit,
    onOpenAvatar: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        val avatar = feed?.avatarUrl
        Box(
            Modifier
                .clip(CircleShape)
                .clickable(enabled = avatar != null) { avatar?.let(onOpenAvatar) }
        ) {
            Avatar(url = avatar, name = name ?: handle, size = 88.dp)
        }

        Text(
            name ?: "@$handle",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp)
        )
        if (name != null) {
            Text(
                "@$handle",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        feed?.bio?.let { bio ->
            Text(
                bio,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 520.dp).padding(top = 4.dp)
            )
        }

        if (isFollowing) {
            OutlinedButton(onClick = onToggleFollow, modifier = Modifier.padding(top = 8.dp)) {
                Text("Following")
            }
        } else {
            FilledTonalButton(onClick = onToggleFollow, modifier = Modifier.padding(top = 8.dp)) {
                Text("Follow")
            }
        }

        feed?.let {
            Text(
                sourceLine(it),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }

        HorizontalDivider(Modifier.padding(top = 12.dp))
    }
}

/** Which server answered and how fresh it is. MTGA always names its source. */
private fun sourceLine(feed: Feed): String {
    val age = relativeTime(feed.fetchedAtMillis)
    val freshness = when {
        age.isEmpty() -> ""
        age == "now" -> ", updated just now"
        else -> ", updated $age ago"
    }
    return "Read via ${feed.fetchedFromHost}$freshness"
}

/**
 * X serves avatars in several sizes behind the same name: "_normal" is 48 px,
 * "_400x400" is the large one. Unknown formats are returned unchanged.
 */
private fun largeAvatar(url: String): String =
    url.replace("_normal.", "_400x400.").replace("_bigger.", "_400x400.")
