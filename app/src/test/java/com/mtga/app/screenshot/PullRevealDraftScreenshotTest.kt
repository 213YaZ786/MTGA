package com.mtga.app.screenshot

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mtga.app.core.model.Post
import com.mtga.app.core.model.PostStats
import com.mtga.app.di.appModule
import com.mtga.app.navigation.LocalReadableInset
import com.mtga.app.ui.component.FeedBanner
import com.mtga.app.ui.component.LoadingMark
import com.mtga.app.ui.component.MenuPill
import com.mtga.app.ui.component.PostCard
import com.mtga.app.ui.glass.LocalGlass
import com.mtga.app.ui.glass.LocalGlassBackdrop
import com.mtga.app.ui.glass.glassSource
import com.mtga.app.ui.glass.rememberGlassBackdrop
import com.mtga.app.ui.icon.MtgaIcons
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * DRAFT for the user's yes (requests.md point 16), nothing of app/src/main changes: a new place for the pull to refresh
 * ("les bulles d'animations ne me plaisent pas trop car elle se superposent avec l'arriere plan dans certains cas").
 * pull_reveal_draft has no disc and nothing drawn over the content: the list is moved down by 96 dp, as a pull at its
 * threshold leaves it, and the app's mark (LoadingMark 72 dp, bare: no surface, no glass) stands in the space the move
 * uncovers, centred between the top (under the status bar) and the moved list. It is iOS's refresh control in Mail and
 * Messages, where the content pulled down uncovers the indicator. Material's sample draws its progress line outside the
 * content, driven by distanceFraction, which is the same idea with a line:
 * https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/samples/src/main/java/androidx/compose/material3/samples/PullToRefreshSamples.kt#PullToRefreshLinearProgressIndicatorSample
 * and its scaling sample grows the indicator with the pull (scaleFraction through LinearOutSlowInEasing, 1 while refreshing):
 * https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/samples/src/main/java/androidx/compose/material3/samples/PullToRefreshSamples.kt#PullToRefreshScalingSample
 * The feed under it is the one of pull_disc_draft (Home's banner and cards, as HomeTopDraftScreenshotTest draws them), the
 * list recorded as the glass backdrop as TimelineScreen's PullToRefreshBox does:
 * https://github.com/213YaZ786/MTGA/blob/main/app/src/main/java/com/mtga/app/ui/component/PullIndicator.kt#PullIndicator
 * The marks are frozen (running = false, progress = 1f), which draws on Robolectric.
 * not ported: Modifier.pullToRefresh, rememberPullToRefreshState, the isRefreshing state and the coroutine that ends it
 * (the pull is held at its threshold, distanceFraction 1, the state a refresh leaves), the Scaffold, TopAppBar and its
 * Refresh action, the LazyColumn of items, LinearProgressIndicator and PullToRefreshDefaults.Indicator (the app's mark is
 * the indicator); PullIndicator's disc, glass, haptic threshold effect and fade-in alpha (there is no disc)
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = android.app.Application::class, sdk = [36])
class PullRevealDraftScreenshotTest : ScreenshotBase() {

    // PostCard reads the post marks through Koin.
    @Before
    fun startKoinOnce() {
        startKoin {
            androidContext(compose.activity.applicationContext)
            modules(appModule)
        }
    }

    @After
    fun stopKoinAgain() = stopKoin()

    private val now = FIXED_NOW

    // The sample of pull_disc_draft.
    private val posts = listOf(
        Post(
            id = "1850000000000000001", authorHandle = "NASA", authorName = "NASA",
            text = "The crew finished their spacewalk today. Watch the replay and read what they fixed.",
            publishedAtMillis = now - 2 * 60 * 60 * 1000,
            permalink = "https://x.com/NASA/status/1850000000000000001",
            stats = PostStats(replies = 128, reposts = 412, likes = 9_300)
        ),
        Post(
            id = "1850000000000000002", authorHandle = "BBCWorld", authorName = "BBC News (World)",
            text = "Talks resume on Monday, officials say, after a week of delays.",
            publishedAtMillis = now - 26 * 60 * 60 * 1000,
            permalink = "https://x.com/BBCWorld/status/1850000000000000002",
            stats = PostStats(replies = 41, reposts = 97, likes = 2_100)
        ),
        Post(
            id = "1850000000000000003", authorHandle = "github", authorName = "GitHub",
            text = "A good week for the people building in the open. Thank you to every maintainer.",
            publishedAtMillis = now - 3 * 24 * 60 * 60 * 1000,
            permalink = "https://x.com/github/status/1850000000000000003",
            stats = PostStats(replies = 20, reposts = 64, likes = 1_450)
        )
    )

    @Test
    fun draft() = capture("pull_reveal_draft") { Feed() }

    // Edge: the draft at 200 % text and on a 360 dp phone (font scale as Roborazzi's captureRoboImage, device by qualifiers as
    // Now in Android's captureMultiDevice)
    // https://github.com/takahirom/roborazzi/blob/main/roborazzi-compose/src/main/java/com/github/takahirom/roborazzi/RoborazziCompose.kt#captureRoboImage
    // https://github.com/android/nowinandroid/blob/main/core/screenshot-testing/src/main/kotlin/com/google/samples/apps/nowinandroid/core/testing/util/ScreenshotHelper.kt#captureMultiDevice
    @Test
    fun edge() = captureEdge("pull_reveal_draft") { Feed() }

    // The pull at its threshold: the content is moved down by [Pulled], the mark in the room above it.
    @Composable
    private fun Feed() {
        val look = LocalGlass.current
        val backdrop = rememberGlassBackdrop()
        // As the sample: the indicator's scale follows the pull, 1 at the threshold.
        val distanceFraction = 1f
        val scaleFraction = LinearOutSlowInEasing.transform(distanceFraction).coerceIn(0f, 1f)
        CompositionLocalProvider(LocalGlassBackdrop provides backdrop.takeIf { look != null }) {
            Box(Modifier.fillMaxSize()) {
                // Nothing over the content: the mark is drawn first, in the room the move uncovers, under the status bar.
                Box(
                    Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).height(Pulled),
                    contentAlignment = Alignment.Center
                ) {
                    Box(Modifier.graphicsLayer { scaleX = scaleFraction; scaleY = scaleFraction }) {
                        LoadingMark(size = 72.dp, running = false, progress = 1f)
                    }
                }
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(top = Pulled)
                        .then(if (look != null) Modifier.glassSource(backdrop, look) else Modifier)
                ) {
                    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
                        Box(Modifier.padding(horizontal = LocalReadableInset.current)) {
                            FeedBanner(
                                centre = {
                                    MenuPill(
                                        icon = MtgaIcons.Folder,
                                        label = "All",
                                        expanded = false,
                                        trailingIcon = ExpandMore,
                                        onClick = {}
                                    )
                                },
                                subtitle = "Updating"
                            )
                        }
                        Column(
                            Modifier.padding(horizontal = 16.dp + LocalReadableInset.current),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            PostCard(post = posts[0], onClick = {}, onOpenLink = {}, onDownload = {}, unread = true)
                            PostCard(post = posts[1], onClick = {}, onOpenLink = {}, onDownload = {})
                            PostCard(post = posts[2], onClick = {}, onOpenLink = {}, onDownload = {})
                        }
                    }
                }
            }
        }
    }
}

// How far a pull at its threshold leaves the content.
private val Pulled = 96.dp
