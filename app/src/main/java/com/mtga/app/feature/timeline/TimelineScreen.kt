package com.mtga.app.feature.timeline

import com.mtga.app.data.marks.PostMarks
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.mtga.app.ui.glass.rememberGlassBackdrop
import com.mtga.app.ui.glass.glassSource
import com.mtga.app.ui.glass.LocalGlassBackdrop
import com.mtga.app.ui.glass.LocalGlass
import com.mtga.app.ui.component.BannerAction
import com.mtga.app.ui.component.DropdownMenuGroup
import com.mtga.app.ui.component.FailureDialog
import com.mtga.app.ui.component.FeedBanner
import com.mtga.app.ui.component.AppBarWithSearch
import com.mtga.app.ui.component.FolderSwitch
import com.mtga.app.ui.component.SearchCapsuleHint
import com.mtga.app.data.accounts.AccountStore
import com.mtga.app.ui.component.GlassMenu
import com.mtga.app.ui.component.MenuPill
import com.mtga.app.ui.component.ScrollUpButton
import com.mtga.app.ui.component.SelectableDropdownMenuItem
import com.mtga.app.ui.component.rememberRefreshHaptics
import com.mtga.app.ui.component.LoadingMark
import com.mtga.app.ui.component.LocalBannerProbe
import com.mtga.app.ui.component.PullRevealBox
import com.mtga.app.ui.component.ageForms
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.foundation.border
import com.mtga.app.ui.component.EmptyZone
import com.mtga.app.ui.component.boldBorder
import com.mtga.app.ui.component.BoldButton
import com.mtga.app.ui.component.LocalDockPadding
import com.mtga.app.ui.component.LocalInlinePlaying
import com.mtga.app.ui.component.rememberInlineTarget
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ShapeDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
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
 * Pull down to refresh. A post that arrived since the last visit wears a
 * dot until you have scrolled past it. The banner's red triangle appears when
 * a source fails and opens what went wrong.
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
    val loaded by viewModel.state.collectAsState()
    // Liked and Archived are views picked from Home's folder menu, not
    // folders: their posts come from PostMarks, and archived posts leave the
    // other views.
    val postMarks: PostMarks = koinInject()
    val marked by postMarks.marks.collectAsState()
    var special by rememberSaveable { mutableStateOf<String?>(null) }
    val state = remember(loaded, marked, special) {
        when (special) {
            LIKED -> loaded.copy(posts = marked.liked.sortedByDescending { it.publishedAtMillis }, folder = LIKED, canLoadMore = false)
            ARCHIVED -> loaded.copy(posts = marked.archived.sortedByDescending { it.publishedAtMillis }, folder = ARCHIVED, canLoadMore = false)
            else -> if (marked.archived.isEmpty()) loaded else loaded.copy(posts = loaded.posts.filterNot { marked.isArchived(it.id) })
        }
    }
    // The threshold is felt in the indicator itself; this answers the end of
    // a refresh the reader pulled: done, or a refusal when nothing came.
    val askedRefresh = rememberRefreshHaptics(
        refreshing = state.loading,
        failed = state.errors.isNotEmpty() && (state.posts.isEmpty() || state.errors.size >= state.followedCount)
    )
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
            onDownload = { downloader.download(it, post.authorHandle, post) },
            onDismiss = { viewing = null }
        )
    }


    var showFailures by remember { mutableStateOf(false) }
    // The failure message names the network and where the fault likely lies. MTGA has no silent downloads state to add
    // to it, so silentDownloads stays false.
    // https://github.com/213YaZ786/LinkedOut/blob/main/app/src/main/java/com/linkedout/app/feature/timeline/TimelineScreen.kt#TimelineScreen
    if (showFailures) {
        FailureDialog(
            failed = state.errors.values.toList(),
            followed = state.followedCount,
            silentDownloads = false,
            site = "X",
            onRetry = {
                showFailures = false
                viewModel.refresh()
            },
            onDismiss = { showFailures = false }
        )
    }

    // The feed choice, in the banner's middle: one pill with the folder icon, the feed's name and a chevron, which opens a
    // menu under it: All and the folders, then Liked and Archived, the shown one checked (Material 3 Expressive's grouped
    // menu in glass, GlassMenu). Picking does what the folder dialog did.
    // The pill is an item of the list that records listBackdrop: floating on it, it would draw the recording that holds
    // it, a layer inside itself, and Android's renderer overflows its stack at the first frame. So MenuPill draws it with
    // no backdrop, as a zone, and the menu lives in a window of its own.
    // https://github.com/213YaZ786/LinkedOut/blob/main/app/src/main/java/com/linkedout/app/feature/timeline/TimelineScreen.kt#TimelineScreen
    var feedsOpen by remember { mutableStateOf(false) }
    val showFeed: (String?) -> Unit = { name ->
        feedsOpen = false
        // Liked and Archived are views of the marks, not folders. Any other name is a folder that exists already.
        if (name == LIKED || name == ARCHIVED) {
            special = name
        } else {
            special = null
            viewModel.showFolder(name)
        }
    }
    // The menu moved out of feedPill (MTGA's own TimelineScreen, feedPill's GlassMenu), each folder row with its count of
    // accounts as the badge of NavigationDrawerItem.
    // https://github.com/213YaZ786/MTGA/blob/main/app/src/main/java/com/mtga/app/feature/timeline/TimelineScreen.kt#TimelineScreen
    // https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/NavigationDrawer.kt#NavigationDrawerItem
    // not ported: NavigationDrawerItem (GlassMenu's SelectableDropdownMenuItem carries its badge slot as count)
    // not ported: TimelineScreen (the screen is this file; only the menu is rebuilt here)
    val followed by koinInject<AccountStore>().accounts.collectAsState()
    val feedMenu: @Composable () -> Unit = {
        GlassMenu(expanded = feedsOpen, onDismissRequest = { feedsOpen = false }) {
            // No Clear row: a feed is chosen, not cleared.
            DropdownMenuGroup(index = 0, count = 2) {
                SelectableDropdownMenuItem(selected = state.folder == null, onClick = { showFeed(null) }, text = "All", count = followed.size)
                state.folders.forEach { name ->
                    SelectableDropdownMenuItem(
                        selected = state.folder == name,
                        onClick = { showFeed(name) },
                        text = name,
                        count = followed.count { it.folder == name }
                    )
                }
            }
            DropdownMenuGroup(index = 1, count = 2) {
                listOf(LIKED, ARCHIVED).forEach { name ->
                    SelectableDropdownMenuItem(selected = state.folder == name, onClick = { showFeed(name) }, text = name)
                }
            }
        }
    }
    val feedPill: @Composable (String) -> Unit = { shownLabel ->
        // The screen keeps its heading for TalkBack: this wrapper is a heading named "Home", and the pill inside it
        // stays its own node, a button that says the feed and whether its menu is open.
        Box(Modifier.semantics { heading(); contentDescription = "Home" }) {
            Box {
                MenuPill(
                    icon = MtgaIcons.Folder,
                    label = shownLabel,
                    expanded = feedsOpen,
                    trailingIcon = MtgaIcons.ExpandMore,
                    onClick = { feedsOpen = true }
                )
                // The banner composes the pill once per label it tries, to measure it: only the one that stays opens the menu.
                if (!LocalBannerProbe.current) feedMenu()
            }
        }
    }
    // The capsule's navigation icon (point 41): the folder switch with the same menu, no probe, one composition.
    val feedSwitch: @Composable () -> Unit = {
        Box(Modifier.semantics { heading(); contentDescription = "Home" }) {
            Box {
                FolderSwitch(
                    icon = MtgaIcons.Folder,
                    trailingIcon = MtgaIcons.ExpandMore,
                    label = state.folder ?: "All",
                    expanded = feedsOpen,
                    onClick = { feedsOpen = true }
                )
                feedMenu()
            }
        }
    }

    // https://github.com/androidx/androidx/blob/androidx-main/compose/animation/animation/samples/src/main/java/androidx/compose/animation/samples/AnimatedVisibilitySamples.kt#AnimatedVisibilityWithBooleanVisibleParamNoReceiver
    // The alert pops in and out (scale and fade) as the failures come and go.
    // not ported: the sample's Box, its fixed size, background and the toggle button around the visible flag
    val alert: (@Composable () -> Unit) = {
        AnimatedVisibility(
            visible = state.errors.isNotEmpty(),
            enter = scaleIn() + fadeIn(),
            exit = scaleOut() + fadeOut()
        ) {
            BannerAction(
                icon = MtgaIcons.Warning,
                label = "What went wrong",
                onClick = { showFailures = true },
                container = MaterialTheme.colorScheme.errorContainer,
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
    val banner: @Composable () -> Unit = {
        if (state.followedCount > 0) {
            // One glass capsule, nothing under it: the folder switch, "Search", the alert on a failure only.
            // https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/SearchBar.kt#AppBarWithSearch
            Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                AppBarWithSearch(
                    inputField = { SearchCapsuleHint(MtgaIcons.Search, listOf("Search"), onOpenSearch) },
                    navigationIcon = feedSwitch,
                    actions = { alert() }
                )
            }
        } else {
            // Nobody followed: the same feed pill, "All" with its menu, no age, no Search.
            FeedBanner(centre = { feedPill("All") }, leading = alert)
        }
    }

    // A post is read once it has gone past the top of the screen. Everything
    // before the first visible row has, by definition, and one pass of the
    // list costs one write rather than one per card.
    val marks: ReadMarks = koinInject()
    val readIds by marks.read.collectAsState()
    val marksReady by marks.readyState.collectAsState()

    // No top bar at all. A bar that folds away and comes back still owns a
    // band of the screen the whole time it is on it, which on a phone is
    // several lines of a post. The banner (the feed pill and its two actions) is the first
    // row of the list instead: they scroll away with everything else, one
    // flick or the back to top button brings them back, and the reading area
    // is the whole window.
    val pull = rememberPullToRefreshState()
    // In glass, the list is recorded as it scrolls, for the controls that
    // float over it (the refresh disc and the back to top button)
    // to bend it.
    val look = LocalGlass.current
    val listBackdrop = rememberGlassBackdrop()
    // The pull's ring fills with the accounts the refresh has finished; the count shows once it is known.
    val refreshProgress: () -> Float = { state.refreshFill }
    val refreshCount = if (state.refreshTotal > 0) state.refreshDone to state.refreshTotal else null
    // The page's ground is painted under the whole app, see MainActivity.
    Scaffold(containerColor = Color.Transparent, contentColor = MaterialTheme.colorScheme.onBackground) { padding ->
        CompositionLocalProvider(LocalGlassBackdrop provides listBackdrop.takeIf { look != null }) {
        // The check pill lives outside the when, so it is on screen whether
        // Home is empty, failed or full, and whatever the scroll position.
        Box(Modifier.fillMaxSize()) {
        when {
            state.followedCount == 0 -> Column(
                Modifier.fillMaxSize().padding(padding).padding(horizontal = LocalReadableInset.current)
            ) {
                banner()
                // The weight gives it the rest of the screen, so it sits in the middle of what is left under the
                // banner rather than against it.
                EmptyState(
                    title = "Nothing followed yet",
                    message = "Find a few accounts and they will all appear here in one stream.",
                    actionLabel = "Open Accounts",
                    onAction = onOpenAccounts,
                    modifier = Modifier.weight(1f)
                )
            }

            // Pull works here too. Before 1.3.2 this screen was a dead end: a
            // failed refresh at launch left Home stuck until a restart.
            // The list pulled down uncovers the mark, under the status bar, where the list starts.
            state.isEmpty && state.errors.isNotEmpty() -> PullRevealBox(
                isRefreshing = state.loading,
                onRefresh = {
                    askedRefresh()
                    viewModel.refresh()
                },
                modifier = Modifier.fillMaxSize(),
                state = pull,
                progress = refreshProgress,
                count = refreshCount,
                lastUpdated = subtitle(state)
            ) {
                // A list, because the pull gesture needs something scrollable.
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = LocalReadableInset.current,
                        end = LocalReadableInset.current,
                        top = padding.calculateTopPadding(),
                        bottom = LocalDockPadding.current
                    )
                ) {
                    // The banner stays, so a folder whose accounts all failed
                    // is not a dead end: the reader can still switch away.
                    item(key = "home-header") { banner() }
                    item(key = "nothing") {
                        EmptyState(
                            title = "Nothing could be loaded",
                            message = "None of your accounts could be loaded. Pull down to try " +
                                "again. The connection check shows which servers are down.",
                            actionLabel = "Check connection",
                            onAction = onOpenDiagnostics,
                            modifier = Modifier.fillParentMaxHeight(0.8f)
                        )
                    }
                }
            }

            else -> PullRevealBox(
                isRefreshing = state.loading,
                onRefresh = {
                    askedRefresh()
                    viewModel.refresh()
                },
                modifier = Modifier.fillMaxSize(),
                state = pull,
                progress = refreshProgress,
                count = refreshCount,
                lastUpdated = subtitle(state)
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
                    // The list starts under the status bar and scrolls beneath it.
                    contentPadding = PaddingValues(
                        start = LocalReadableInset.current,
                        end = LocalReadableInset.current,
                        top = padding.calculateTopPadding(),
                        bottom = LocalDockPadding.current
                    ),
                    modifier = Modifier.fillMaxSize().then(if (look != null) Modifier.glassSource(listBackdrop, look) else Modifier)
                ) {
                    item(key = "home-header") { banner() }

                    if (state.posts.isEmpty() && !state.loading && state.folder != null) {
                        item(key = "folder-empty") {
                            EmptyState(
                                title = "Nothing in ${state.folder} yet",
                                message = "File accounts here from Accounts, or show every account.",
                                actionLabel = "Show every account",
                                onAction = { viewModel.showFolder(null) },
                                modifier = Modifier.fillParentMaxHeight(0.7f).animateItem()
                            )
                        }
                    }

                    items(state.posts, key = { it.id }) { post ->
                        PostCard(
                            post = post,
                            // Unknown until the file is read, and unknown
                            // means read: a border flashed on every card for
                            // one frame at launch would be worse than none.
                            unread = marksReady && post.id !in readIds,
                            onClick = { onOpenPost(post) },
                            onOpenLink = { uriHandler.openUri(it) },
                            onDownload = { downloader.download(it, post.authorHandle, post) },
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

                // The way back to the top shows only once it is a real chore.
                // The feed is chosen from the banner now, so nothing stacks with it.
                val showBackToTop by remember {
                    derivedStateOf { listState.firstVisibleItemIndex >= BACK_TO_TOP_AFTER }
                }
                ScrollUpButton(
                    visible = showBackToTop,
                    icon = MtgaIcons.ArrowUp,
                    onClick = { scope.launch { listState.animateScrollToItem(0) } },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 16.dp + LocalReadableInset.current, bottom = LocalDockPadding.current + 16.dp)
                )
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
}

// The age the pull shows in its ring before release (PullRevealBox's lastUpdated, "Updated" over "3m ago"); null while
// loading or when nothing was read yet. Was the banner's subtitle, now in the pull (point 41).
// https://github.com/213YaZ786/MTGA/blob/main/app/src/main/java/com/mtga/app/feature/timeline/TimelineScreen.kt#TimelineScreen
private fun subtitle(state: TimelineUiState): String? = when {
    state.followedCount == 0 -> null
    state.loading -> null
    state.lastUpdatedMillis != null -> relativeTime(state.lastUpdatedMillis)
    else -> null
}

@Composable
private fun TimelineFooter(state: TimelineUiState, onLoadMore: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        // Crossfade between the footer's states (mark, button, text) instead of swapping them at once.
        val kind = when {
            state.loadingMore -> 0
            state.pagingFailed -> 1
            state.canLoadMore -> 2
            else -> 3
        }
        Crossfade(targetState = kind, label = "timelineFooter") { shown ->
        when (shown) {
            0 -> LoadingMark(size = 28.dp)
            1 -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Couldn't load older posts. The server is busy, " +
                        "which usually clears in a minute.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                BoldButton(onClick = onLoadMore) { Text("Try again") }
            }
            2 -> BoldButton(onClick = onLoadMore) { Text("Load older posts") }
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
    // A zone centred in the space, like every empty screen, so an empty
    // Home still carries the wallpaper's colour.
    EmptyZone(
        title = title,
        message = message,
        actionLabel = actionLabel,
        onAction = onAction,
        modifier = modifier.fillMaxSize()
    )
}

private const val LIKED = "Liked"
private const val ARCHIVED = "Archived"
