package com.mtga.app.feature.timeline

import com.mtga.app.ui.component.LocalDockPadding
import com.mtga.app.ui.component.LocalInlinePlaying
import com.mtga.app.ui.component.rememberInlineTarget
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mtga.app.core.common.solvableChallenge
import com.mtga.app.data.read.ReadMarks
import com.mtga.app.navigation.LocalReadableInset
import com.mtga.app.core.media.MediaDownloader
import com.mtga.app.data.settings.SettingsStore
import com.mtga.app.core.model.Post
import com.mtga.app.feature.media.MediaViewer
import com.mtga.app.ui.component.ChallengePill
import com.mtga.app.ui.component.PostCard
import com.mtga.app.ui.component.relativeTime
import com.mtga.app.ui.icon.MtgaIcons
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

/**
 * Home: everything you follow in one stream, newest first.
 *
 * Pull down to refresh. A post that arrived since the last visit wears an
 * outline until you have scrolled past it. The pulse icon turns red when a
 * source fails and opens Diagnostics.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineScreen(
    onOpenDiagnostics: () -> Unit,
    onOpenAccounts: () -> Unit,
    onOpenPost: (Post) -> Unit,
    onOpenSearch: () -> Unit,
    viewModel: TimelineViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()
    val uriHandler = LocalUriHandler.current
    val downloader: MediaDownloader = koinInject()
    val settingsStore: SettingsStore = koinInject()
    val settings by settingsStore.settings.collectAsState()
    val listState = rememberLazyListState()

    val scope = rememberCoroutineScope()
    var viewing by remember { mutableStateOf<Pair<Post, Int>?>(null) }

    viewing?.let { (post, index) ->
        MediaViewer(
            postId = post.id,
            media = post.media,
            startIndex = index,
            onDownload = { downloader.download(it, post.authorHandle) },
            onDismiss = { viewing = null }
        )
    }

    val haptics = LocalHapticFeedback.current

    // A post is read once it has gone past the top of the screen. Everything
    // before the first visible row has, by definition, and one pass of the
    // list costs one write rather than one per card.
    val marks: ReadMarks = koinInject()
    val readIds by marks.read.collectAsState()

    // No top bar at all. A bar that folds away and comes back still owns a
    // band of the screen the whole time it is on it, which on a phone is
    // several lines of a post. The title and its two actions are the first
    // zone of the list instead: they scroll away with everything else, one
    // flick or the back to top button brings them back, and the reading area
    // is the whole window.
    Scaffold { padding ->
        // The check pill lives outside the when, so it is on screen whether
        // Home is empty, failed or full, and whatever the scroll position.
        Box(Modifier.fillMaxSize()) {
        when {
            state.followedCount == 0 -> EmptyState(
                title = "Nothing followed yet",
                message = "Find a few accounts and they will all appear here in one stream.",
                actionLabel = "Open Accounts",
                onAction = onOpenAccounts,
                modifier = Modifier.padding(padding)
            )

            // Pull works here too. Before 1.3.2 this screen was a dead end: a
            // failed refresh at launch left Home stuck until a restart.
            state.isEmpty && state.errors.isNotEmpty() -> PullToRefreshBox(
                isRefreshing = state.loading,
                onRefresh = {
                    // Confirms the gesture crossed the threshold, so the
                    // reader can let go without watching for the spinner.
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.refresh()
                },
                modifier = Modifier.fillMaxSize().padding(padding)
            ) {
                // A list, because the pull gesture needs something scrollable.
                LazyColumn(Modifier.fillMaxSize()) {
                    item(key = "nothing") {
                        EmptyState(
                            title = "Nothing could be loaded",
                            message = "None of your accounts could be loaded. Pull down to try " +
                                "again. The connection check shows which servers are down.",
                            actionLabel = "Check connection",
                            onAction = onOpenDiagnostics,
                            modifier = Modifier.fillParentMaxSize()
                        )
                    }
                }
            }

            else -> PullToRefreshBox(
                isRefreshing = state.loading,
                onRefresh = {
                    // Confirms the gesture crossed the threshold, so the
                    // reader can let go without watching for the spinner.
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.refresh()
                },
                modifier = Modifier.fillMaxSize().padding(padding)
            ) {
                // Prefetch a page before the reader actually hits the bottom,
                // so scrolling stays continuous instead of stalling.
                val shouldLoadMore by remember {
                    derivedStateOf {
                        val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                        last >= listState.layoutInfo.totalItemsCount - LOAD_MORE_THRESHOLD
                    }
                }
                LaunchedEffect(shouldLoadMore, state.canLoadMore, state.pagingFailed) {
                    if (shouldLoadMore) viewModel.loadMore()
                }

                val inline = rememberInlineTarget(
                    listState = listState,
                    posts = state.posts,
                    keyOf = { it.id },
                    paused = viewing != null
                )
                CompositionLocalProvider(LocalInlinePlaying provides inline) {
                // The Home zone is the one row drawn above the posts, so an
                // index in the list turns back into an index in the posts by
                // subtracting it.
                val headerCount = 1

                LaunchedEffect(listState, state.posts, headerCount) {
                    snapshotFlow { listState.firstVisibleItemIndex }
                        .collect { first ->
                            val past = first - headerCount
                            if (past > 0) {
                                marks.markRead(state.posts.take(past).map { it.id })
                            }
                        }
                }
                LazyColumn(
                    state = listState,
                    // Horizontal padding rather than a narrower list, so a
                    // drag in the margins of a wide window scrolls too.
                    contentPadding = PaddingValues(
                        start = LocalReadableInset.current,
                        end = LocalReadableInset.current,
                        bottom = LocalDockPadding.current
                    ),
                    modifier = Modifier.fillMaxSize()
                ) {
                    item(key = "home-header") {
                        HomeHeader(
                            state = state,
                            onOpenSearch = onOpenSearch,
                            onOpenDiagnostics = onOpenDiagnostics
                        )
                    }

                    items(state.posts, key = { it.id }) { post ->
                        PostCard(
                            post = post,
                            // Unknown until the file is read, and unknown
                            // means read: a border flashed on every card for
                            // one frame at launch would be worse than none.
                            unread = marks.ready && post.id !in readIds,
                            onClick = { onOpenPost(post) },
                            onOpenLink = { uriHandler.openUri(it) },
                            onDownload = { downloader.download(it, post.authorHandle) },
                            showStats = settings.showCounts,
                            onOpenMedia = { index -> viewing = post to index },
                            // Posts arrive and merge while the list is on
                            // screen. Animating placement means a card slides
                            // into its row instead of teleporting.
                            modifier = Modifier.animateItem()
                        )
                    }

                    item(key = "footer") {
                        TimelineFooter(state = state, onLoadMore = { viewModel.loadMore(manual = true) })
                    }
                }
                }

                // Only once the way back is a real chore. Below that the
                // button would be in the way of the posts it sits on.
                val showBackToTop by remember {
                    derivedStateOf { listState.firstVisibleItemIndex >= BACK_TO_TOP_AFTER }
                }
                AnimatedVisibility(
                    visible = showBackToTop,
                    enter = fadeIn() + scaleIn(initialScale = 0.8f),
                    exit = fadeOut() + scaleOut(targetScale = 0.8f),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(
                            end = 16.dp + LocalReadableInset.current,
                            bottom = LocalDockPadding.current + 16.dp
                        )
                ) {
                    SmallFloatingActionButton(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            scope.launch { listState.animateScrollToItem(0) }
                        },
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ) {
                        Icon(MtgaIcons.ArrowUp, contentDescription = "Back to the newest post")
                    }
                }
            }
        }

        ChallengePill(
            challenge = state.errors.values.solvableChallenge(),
            onVerify = viewModel::verify,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = LocalDockPadding.current + 16.dp)
        )
        }
    }
}

/**
 * Home's title, its freshness line and its two actions, as the first zone of
 * the list. Centred, because a title is data and not a row of a list.
 *
 * The actions sit on the title's own line and the freshness line runs under
 * them, so a long "Updated an hour ago" never has to fight the icons for room.
 */
@Composable
private fun HomeHeader(
    state: TimelineUiState,
    onOpenSearch: () -> Unit,
    onOpenDiagnostics: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val failing = state.errors.isNotEmpty()
            if (state.followedCount > 0) {
                FilledTonalIconButton(
                    onClick = onOpenDiagnostics,
                    modifier = Modifier.size(BUTTON_SIZE),
                    // A failing source is the one thing here that needs
                    // noticing, so it fills rather than tints.
                    colors = if (failing) {
                        IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    } else {
                        IconButtonDefaults.filledTonalIconButtonColors()
                    }
                ) {
                    Icon(
                        MtgaIcons.Pulse,
                        contentDescription = if (failing) "Some sources failed" else "Sources working",
                        modifier = Modifier.size(ICON_SIZE)
                    )
                }
            }

            // The two buttons are the same width, so the title lands on the
            // centre of the zone without being measured against them.
            Column(
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Home", style = MaterialTheme.typography.titleLarge)
                subtitle(state)?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (state.followedCount > 0) {
                FilledTonalIconButton(
                    onClick = onOpenSearch,
                    modifier = Modifier.size(BUTTON_SIZE)
                ) {
                    Icon(
                        MtgaIcons.Search,
                        contentDescription = "Search saved posts",
                        modifier = Modifier.size(ICON_SIZE)
                    )
                }
            }
        }
    }
}

private fun subtitle(state: TimelineUiState): String? = when {
    state.followedCount == 0 -> null
    state.loading -> "Updating"
    state.lastUpdatedMillis != null -> relativeTime(state.lastUpdatedMillis).let { age ->
        if (age.isEmpty() || age == "now") "Updated just now" else "Updated $age ago"
    }
    else -> null
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
            state.pagingFailed -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Couldn't load older posts. The server is busy, " +
                        "which usually clears in a minute.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                TextButton(onClick = onLoadMore) { Text("Try again") }
            }
            state.canLoadMore -> TextButton(onClick = onLoadMore) { Text("Load older posts") }
            else -> Text(
                "No older posts available.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Matched to the dock at the bottom: its slots are 48 dp across and its icons
 * are Material's default 24 dp. Two controls of the same app should not be two
 * different sizes.
 */
private val BUTTON_SIZE = 48.dp
private val ICON_SIZE = 24.dp

private const val LOAD_MORE_THRESHOLD = 5

/** Posts scrolled past before the way back becomes worth a button. */
private const val BACK_TO_TOP_AFTER = 5

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
