package com.mtga.app.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.navigation.NavHostController
import com.mtga.app.core.link.LinkRouter
import com.mtga.app.core.link.XLink
import org.koin.compose.koinInject
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mtga.app.core.model.Post
import com.mtga.app.core.model.PostKind
import com.mtga.app.feature.accounts.AccountsScreen
import com.mtga.app.feature.debug.DebugLogScreen
import com.mtga.app.feature.diagnostics.DiagnosticsScreen
import com.mtga.app.feature.feed.FeedScreen
import com.mtga.app.feature.post.PostDetailScreen
import com.mtga.app.feature.search.SearchScreen
import com.mtga.app.feature.settings.SettingsScreen
import com.mtga.app.feature.timeline.TimelineScreen
import com.mtga.app.ui.component.DockClearance
import com.mtga.app.ui.component.DockItem
import com.mtga.app.ui.component.FloatingDock
import com.mtga.app.ui.component.LocalDockPadding
import kotlinx.coroutines.launch

@Composable
fun MtgaApp() {
    val navController = rememberNavController()
    val links: LinkRouter = koinInject()
    val pending by links.pending.collectAsState()
    val platformUris = LocalUriHandler.current

    fun show(link: XLink) {
        when (link) {
            is XLink.Profile -> navController.navigate(Routes.feed(link.handle))
            is XLink.Post -> navController.navigate(Routes.post(link.id, link.handle))
        }
    }

    // Links from other apps, dropped off by the activity.
    LaunchedEffect(pending) {
        pending?.let {
            show(it)
            links.consume()
        }
    }

    // Every tap on a link inside the app goes through here. X profiles and
    // posts, and Nitter or twstalker links to them, open in MTGA. Anything
    // else goes to the browser as before.
    val uris = remember(platformUris) {
        object : UriHandler {
            override fun openUri(uri: String) {
                val link = links.parse(uri)
                if (link != null) show(link) else platformUris.openUri(uri)
            }
        }
    }

    CompositionLocalProvider(LocalUriHandler provides uris) {
        MtgaNavHost(navController)
    }
}

@Composable
private fun MtgaNavHost(navController: NavHostController) {
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.MAIN,
            modifier = Modifier.fillMaxSize().padding(innerPadding)
        ) {
            composable(Routes.MAIN) {
                MainTabs(
                    onOpenDiagnostics = { navController.navigate(Routes.DIAGNOSTICS) },
                    onOpenDebugLog = { navController.navigate(Routes.DEBUG_LOG) },
                    onOpenFeed = { handle -> navController.navigate(Routes.feed(handle)) },
                    onOpenPost = { post -> navController.navigate(Routes.post(post.id, post.cacheOwner())) },
                    onOpenSearch = { navController.navigate(Routes.SEARCH) }
                )
            }
            composable(Routes.SEARCH) {
                Readable {
                    SearchScreen(
                        onBack = { navController.popBackStack() },
                        onOpenPost = { post -> navController.navigate(Routes.post(post.id, post.cacheOwner())) }
                    )
                }
            }
            composable(Routes.DEBUG_LOG) {
                Readable {
                    DebugLogScreen(onBack = { navController.popBackStack() })
                }
            }
            composable(
                route = Routes.FEED_PATTERN,
                arguments = listOf(navArgument("handle") { type = NavType.StringType })
            ) { entry ->
                Readable {
                    FeedScreen(
                        handle = entry.arguments?.getString("handle").orEmpty(),
                        onBack = { navController.popBackStack() },
                        onOpenDiagnostics = { navController.navigate(Routes.DIAGNOSTICS) },
                        onOpenPost = { post ->
                            navController.navigate(Routes.post(post.id, entry.arguments?.getString("handle").orEmpty()))
                        }
                    )
                }
            }
            composable(
                route = Routes.POST_PATTERN,
                arguments = listOf(
                    navArgument("id") { type = NavType.StringType },
                    navArgument("from") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                )
            ) { entry ->
                Readable {
                    PostDetailScreen(
                        id = entry.arguments?.getString("id").orEmpty(),
                        from = entry.arguments?.getString("from"),
                        onBack = { navController.popBackStack() },
                        onOpenProfile = { handle -> navController.navigate(Routes.feed(handle)) },
                        onOpenPost = { post -> navController.navigate(Routes.post(post.id, post.authorHandle)) }
                    )
                }
            }
            composable(Routes.DIAGNOSTICS) {
                Readable {
                    DiagnosticsScreen(onBack = { navController.popBackStack() })
                }
            }
        }
    }
}

/**
 * The account whose cache file holds this post. A repost is stored with the
 * account that reposted it, everything else with its author.
 */
private fun Post.cacheOwner(): String =
    if (kind == PostKind.REPOST) relatedHandle ?: authorHandle else authorHandle

/**
 * The three tabs side by side in one pager. On a phone a swipe moves between
 * them, with the floating dock on top. From 600 dp wide, a tablet, a foldable
 * or a phone on its side, a navigation rail takes the dock's place and the
 * content is centred at a readable width. Back from Accounts or Settings
 * returns to Home before leaving the app, which is what people expect from tabs.
 */
@Composable
private fun MainTabs(
    onOpenDiagnostics: () -> Unit,
    onOpenDebugLog: () -> Unit,
    onOpenFeed: (String) -> Unit,
    onOpenPost: (Post) -> Unit,
    onOpenSearch: () -> Unit
) {
    val tabs = TopDestination.entries
    // Outside the width check, so turning a tablet or unfolding a phone keeps
    // the current tab and every scroll position.
    val pager = rememberPagerState(pageCount = { tabs.size })
    val scope = rememberCoroutineScope()

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val rail = WidthClass.of(maxWidth).usesRail

        fun go(index: Int) {
            scope.launch {
                // A rail switches in place, as Material describes it. The
                // dock slides, because there the pages are also swiped.
                if (rail) pager.scrollToPage(index) else pager.animateScrollToPage(index)
            }
        }

        BackHandler(enabled = pager.currentPage != 0) { go(0) }

        val pages: @Composable (Modifier) -> Unit = { modifier ->
            HorizontalPager(
                state = pager,
                // All three stay alive, so switching tabs never reloads or
                // loses the scroll position.
                beyondViewportPageCount = tabs.size - 1,
                // With a rail the tabs are side by side on screen already, a
                // sideways swipe would only fight horizontal gestures.
                userScrollEnabled = !rail,
                modifier = modifier
            ) { page ->
                Readable {
                    when (tabs[page]) {
                        TopDestination.TIMELINE -> TimelineScreen(
                            onOpenDiagnostics = onOpenDiagnostics,
                            onOpenAccounts = { go(TopDestination.ACCOUNTS.ordinal) },
                            onOpenPost = onOpenPost,
                            onOpenSearch = onOpenSearch
                        )
                        TopDestination.ACCOUNTS -> AccountsScreen(onOpenFeed = onOpenFeed)
                        TopDestination.SETTINGS -> SettingsScreen(
                            onOpenDiagnostics = onOpenDiagnostics,
                            onOpenDebugLog = onOpenDebugLog
                        )
                    }
                }
            }
        }

        if (rail) {
            Row(Modifier.fillMaxSize()) {
                NavigationRail(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    // The Scaffold around the NavHost already applied the system bar insets.
                    windowInsets = WindowInsets(0, 0, 0, 0)
                ) {
                    Spacer(Modifier.height(12.dp))
                    tabs.forEachIndexed { index, tab ->
                        NavigationRailItem(
                            selected = pager.currentPage == index,
                            onClick = { go(index) },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(tab.label) }
                        )
                    }
                }
                // No dock here, so nothing to clear at the bottom.
                CompositionLocalProvider(LocalDockPadding provides 0.dp) {
                    pages(Modifier.weight(1f).fillMaxHeight())
                }
            }
        } else {
            Box(Modifier.fillMaxSize()) {
                CompositionLocalProvider(LocalDockPadding provides DockClearance) {
                    pages(Modifier.fillMaxSize())
                }

                FloatingDock(
                    items = tabs.map { DockItem(it.icon, it.label) },
                    position = pager.currentPage + pager.currentPageOffsetFraction,
                    onSelect = ::go,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp)
                )
            }
        }
    }
}
