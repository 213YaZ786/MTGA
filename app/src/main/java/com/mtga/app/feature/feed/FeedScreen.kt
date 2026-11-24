package com.mtga.app.feature.feed

import com.mtga.app.ui.component.ZoneAlertDialog
import com.mtga.app.ui.component.QuietButton
import androidx.compose.ui.graphics.Color
import com.mtga.app.ui.component.RejectOnFailure
import com.mtga.app.ui.component.rememberHaptics
import com.mtga.app.ui.component.LoadingMark
import androidx.compose.foundation.layout.navigationBarsPadding
import com.mtga.app.ui.component.plus
import com.mtga.app.ui.component.BoldButton
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ShapeDefaults
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import com.mtga.app.ui.component.rememberMediaPolicy
import java.util.Locale
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mtga.app.core.common.solvableChallenge
import com.mtga.app.navigation.LocalReadableInset
import com.mtga.app.core.media.MediaDownloader
import com.mtga.app.data.settings.SettingsStore
import com.mtga.app.core.model.Feed
import com.mtga.app.core.model.MediaItem
import com.mtga.app.core.model.MediaType
import com.mtga.app.core.model.Post
import com.mtga.app.core.model.VerifiedType
import com.mtga.app.feature.media.MediaViewer
import com.mtga.app.ui.component.Avatar
import com.mtga.app.ui.component.ChallengePill
import com.mtga.app.ui.component.PullRevealBox
import com.mtga.app.ui.component.ErrorPanel
import com.mtga.app.ui.component.LocalDockPadding
import com.mtga.app.ui.component.LocalInlinePlaying
import com.mtga.app.ui.component.PostCard
import com.mtga.app.ui.component.rememberInlineTarget
import com.mtga.app.ui.component.relativeTime
import com.mtga.app.ui.icon.MtgaIcons
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

/**
 * One account: a profile header (ProfileHeader.kt), then its posts.
 *
 * The header is always there, even before anything loaded, so you can follow
 * an account whose feed is momentarily unreachable. No bar: the pull down
 * refreshes, back is the system's.
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
    val haptics = rememberHaptics()
    var confirmUnfollow by remember { mutableStateOf(false) }
    if (confirmUnfollow) {
        ZoneAlertDialog(
            onDismissRequest = { confirmUnfollow = false },
            title = { Text("Unfollow?") },
            confirmButton = {
                QuietButton(onClick = {
                    confirmUnfollow = false
                    haptics.done()
                    viewModel.toggleFollow()
                }) { Text("Unfollow") }
            },
            dismissButton = { QuietButton(onClick = { confirmUnfollow = false }) { Text("Cancel") } }
        )
    }
    RejectOnFailure(state.error)
    val followed by viewModel.followed.collectAsState()
    val isFollowing = followed.any { it.handle.equals(handle, ignoreCase = true) }
    val uriHandler = LocalUriHandler.current
    val downloader: MediaDownloader = koinInject()
    val settingsStore: SettingsStore = koinInject()
    val settings by settingsStore.settings.collectAsState()
    val listState = rememberLazyListState()
    // Post id, attachments, start index. The id is what lets the viewer find
    // a saved copy, and it is empty for the avatar, which belongs to no post.
    var viewing by remember { mutableStateOf<Triple<String, List<MediaItem>, Int>?>(null) }

    val feed = state.feed
    val name = feed?.displayName?.takeIf { it.isNotBlank() && it != handle }

    viewing?.let { (postId, media, index) ->
        MediaViewer(
            postId = postId,
            media = media,
            startIndex = index,
            // The viewer holds the post's id only (empty for the avatar, which
            // belongs to no post): its address is looked up in the feed.
            onDownload = {
                downloader.download(it, handle, feed?.posts?.firstOrNull { post -> post.id == postId })
            },
            onDismiss = { viewing = null }
        )
    }

    LaunchedEffect(handle) { viewModel.load(handle) }

    val shown = feed?.posts.orEmpty()
    val canMore = state.canLoadMore
    val pagingFailed = state.pagingFailed
    val loadingMore = state.loadingMore

    val shouldLoadMore by remember(shown.size) {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            shown.isNotEmpty() && last >= shown.size - 5
        }
    }
    LaunchedEffect(shouldLoadMore, canMore, pagingFailed) {
        if (shouldLoadMore) viewModel.loadMore()
    }

    // No bar on a profile: back is the system's, refresh is the pull down (Tusky's AccountActivity refreshes its profile by
    // a swipe, LinkedOut's FeedScreen draws no bar while its header is on screen). [onBack] stays for the navigation host.
    // Source: https://github.com/tuskyapp/Tusky/blob/develop/app/src/main/java/com/keylesspalace/tusky/components/account/AccountActivity.kt#AccountActivity
    // and https://github.com/213YaZ786/LinkedOut/blob/main/app/src/main/java/com/linkedout/app/feature/feed/FeedScreen.kt#FeedScreen
    // not ported: AccountActivity's toolbar, tabs, banner collapsing, menu and its view pager (the user's words: no bar)
    // not ported: LinkedOut's FeedScreen own header card and back-in-card, refresh button (the pull refreshes, back is the system's)
    Scaffold(
        // The page's ground is painted under the whole app, see MainActivity.
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground
    ) { padding ->
        Box(Modifier.fillMaxSize()) {
        val direction = LocalLayoutDirection.current
        val readable = LocalReadableInset.current
        val inline = rememberInlineTarget(
            listState = listState,
            posts = shown,
            keyOf = { it.id },
            paused = viewing != null
        )
        // Every load (the first one, a retry, the pull) shows the app's mark over the list, as Home's.
        PullRevealBox(
            isRefreshing = state.loading,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize()
        ) {
        CompositionLocalProvider(LocalInlinePlaying provides inline) {
        LazyColumn(
            state = listState,
            // The header starts at the window's top, its banner under the status bar, so the top inset is not padded.
            // The readable inset is on the rows, not the list, so a drag in the margins of a wide window scrolls too
            // and the banner stays edge to edge.
            contentPadding = PaddingValues(
                start = padding.calculateStartPadding(direction),
                end = padding.calculateEndPadding(direction),
                bottom = LocalDockPadding.current + padding.calculateBottomPadding()
            ),
            modifier = Modifier.fillMaxSize()
        ) {
            item(key = "header") {
                ProfileHeader(
                    handle = handle,
                    name = name,
                    feed = feed,
                    isFollowing = isFollowing,
                    verified = feed?.posts?.firstOrNull {
                        it.authorHandle.equals(handle, ignoreCase = true) && it.verified != VerifiedType.NONE
                    }?.verified ?: feed?.verified ?: VerifiedType.NONE,
                    onToggleFollow = {
                        // Following answers at once; unfollowing asks first, a stray
                        // tap on "Following" must not drop an account.
                        if (isFollowing) {
                            confirmUnfollow = true
                        } else {
                            haptics.done()
                            viewModel.toggleFollow()
                        }
                    },
                    onOpenAvatar = { small ->
                        viewing = Triple(
                            "",
                            listOf(
                                MediaItem(
                                    previewUrl = small,
                                    downloadUrl = originalAvatar(small),
                                    type = MediaType.PHOTO
                                )
                            ),
                            0
                        )
                    },
                    // Shell.tsx onPressBanner opens the lightbox; the viewer gets the banner at its full size.
                    // https://github.com/bluesky-social/social-app/blob/main/src/screens/Profile/Header/Shell.tsx#onPressBanner
                    // not ported: the lightbox's own moderation and alt text (X banners carry none)
                    onOpenBanner = { small ->
                        viewing = Triple(
                            "",
                            listOf(
                                MediaItem(
                                    previewUrl = small,
                                    downloadUrl = bannerFull(small),
                                    type = MediaType.PHOTO
                                )
                            ),
                            0
                        )
                    }
                )
            }

            // No Replies and Media tabs since 2.9.10. Both were read from
            // Nitter alone, which barely answers any more, so they showed an
            // error nearly every time. LinkedOut dropped its own earlier.
            state.error?.let {
                item(key = "error") {
                    ErrorPanel(
                        modifier = Modifier.padding(horizontal = readable).padding(16.dp),
                        error = it,
                        onRetry = viewModel::refresh,
                        onOpenDiagnostics = onOpenDiagnostics
                    )
                }
            }

            // The list's own 96 dp mark is gone: the pull's mark above the header shows the load.
            if (shown.isEmpty()) return@LazyColumn

            items(shown, key = { it.id }) { post ->
                PostCard(
                    post = post,
                    onClick = { onOpenPost(post) },
                    onOpenLink = { uriHandler.openUri(it) },
                    onDownload = { downloader.download(it, post.authorHandle, post) },
                    showStats = settings.showCounts,
                    onOpenMedia = { index -> viewing = Triple(post.id, post.media, index) },
                    modifier = Modifier.padding(horizontal = readable).animateItem()
                )
            }

            item(key = "footer") {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = readable).padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Crossfade between the footer's states (mark, button, text), as Home's footer.
                    val kind = when {
                        loadingMore -> 0
                        canMore -> if (pagingFailed) 1 else 2
                        else -> 3
                    }
                    Crossfade(targetState = kind, label = "feedFooter") { shown ->
                        when (shown) {
                            0 -> LoadingMark(size = 28.dp)
                            1, 2 -> BoldButton(onClick = { viewModel.loadMore(manual = true) }) {
                                Text(if (shown == 1) "Try again" else "Load older posts")
                            }
                            else -> Text(
                                "No older posts available.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
        }
        }

        // Same pill as Home, same place. A check asked for halfway down a
        // profile used to be announced by a card above the header, which the
        // reader had already scrolled past.
        ChallengePill(
            challenge = listOfNotNull(state.error).solvableChallenge(),
            onVerify = viewModel::verify,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                // The screen draws under the navigation bar, the pill stays above it.
                .navigationBarsPadding()
                .padding(bottom = LocalDockPadding.current + 16.dp)
        )
        }
    }
}

