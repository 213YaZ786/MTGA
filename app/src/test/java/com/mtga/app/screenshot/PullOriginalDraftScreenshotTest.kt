package com.mtga.app.screenshot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
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
import com.mtga.app.ui.component.PullRevealBox
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
 * DRAFT for the user's yes (requests.md point 43), nothing of app/src/main changes: the pull to refresh back to the app's
 * own animation, no bubble, bigger. pull_original_draft_<state>: LoadingMark (the falling feathers) at 72 dp alone, straight
 * in the layer (no disc, no glass, no shadow, no blur, no count, no "Updated"), sliding down from the top edge as
 * IndicatorBox does (translationY = distanceFraction * maxDistance - height, read in the layer), its alpha from the pull
 * (fraction * 2, clamped), held at 1 while refreshing. States: half (0.5), threshold (1.0), refreshing.
 * pull_original_draft_today_<state>: the current PullRevealBox at the same states (its blur is a RenderEffect, which does
 * not draw on the JVM).
 * https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/pulltorefresh/PullToRefresh.kt#IndicatorBox
 * https://github.com/213YaZ786/MTGA/blob/main/app/src/main/java/com/mtga/app/ui/component/LoadingMark.kt#LoadingMark
 * not ported: IndicatorBox's drawWithContent clipRect, shape, containerColor, background and shadowElevation (the mark has
 * none); PullToRefreshBox's gesture (Modifier.pullToRefresh: the pull is fixed per picture); PullIndicator's haptic effect
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = android.app.Application::class, sdk = [36])
class PullOriginalDraftScreenshotTest : ScreenshotBase() {

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
    fun half() = capture("pull_original_draft_half") { Draft(fraction = 0.5f, refreshing = false) }

    @Test
    fun threshold() = capture("pull_original_draft_threshold") { Draft(fraction = 1f, refreshing = false) }

    @Test
    fun refreshing() = capture("pull_original_draft_refreshing") { Draft(fraction = 1f, refreshing = true) }

    @Test
    fun todayHalf() = capture("pull_original_draft_today_half") { Today(fraction = 0.5f, refreshing = false) }

    @Test
    fun todayThreshold() = capture("pull_original_draft_today_threshold") { Today(fraction = 1f, refreshing = false) }

    @Test
    fun todayRefreshing() = capture("pull_original_draft_today_refreshing") { Today(fraction = 1f, refreshing = true) }

    // Home's banner and cards, as the other pull drafts draw them.
    @Composable
    private fun Feed() {
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

    // The proposal: the feed recorded as the glass backdrop, the mark alone over it.
    @Composable
    private fun Draft(fraction: Float, refreshing: Boolean) {
        val look = LocalGlass.current
        val backdrop = rememberGlassBackdrop()
        CompositionLocalProvider(LocalGlassBackdrop provides backdrop.takeIf { look != null }) {
            Box(Modifier.fillMaxSize()) {
                // The list stays in place, the feathers fall over it.
                Box(Modifier.fillMaxSize().then(if (look != null) Modifier.glassSource(backdrop, look) else Modifier)) { Feed() }
                IndicatorBox(
                    distanceFraction = { fraction },
                    isRefreshing = refreshing,
                    modifier = Modifier.align(Alignment.TopCenter).windowInsetsPadding(WindowInsets.statusBars)
                ) {
                    LoadingMark(size = 72.dp, running = refreshing, progress = fraction.coerceIn(0f, 1f))
                }
            }
        }
    }

    // Today: the current PullRevealBox over the same feed, its pull state set to [fraction].
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun Today(fraction: Float, refreshing: Boolean) {
        val look = LocalGlass.current
        val backdrop = rememberGlassBackdrop()
        val state = rememberPullToRefreshState()
        LaunchedEffect(state) { state.snapTo(fraction) }
        CompositionLocalProvider(LocalGlassBackdrop provides backdrop.takeIf { look != null }) {
            PullRevealBox(isRefreshing = refreshing, onRefresh = {}, state = state, modifier = Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().then(if (look != null) Modifier.glassSource(backdrop, look) else Modifier)) { Feed() }
            }
        }
    }
}

// The distance at which the mark's foot lands: the banner's 64 dp, the mark's 72 dp and 4 dp of air, so at the threshold
// the mark sits just under the folder capsule, over the first post card.
private val PullMaxDistance = 140.dp

// IndicatorBox of PullToRefresh.kt without its shape, background and elevation: the box places itself with a layer,
// translationY = distanceFraction * maxDistance - height, read in the layer and not in composition. maxDistance is
// PullToRefreshDefaults's IndicatorMaxDistance, 80 dp. The alpha comes from the pull (fraction * 2, clamped) and is 1
// while refreshing. The size is the mark's own 72 dp (SpinnerContainerSize is Material's).
@Composable
private fun IndicatorBox(
    distanceFraction: () -> Float,
    isRefreshing: Boolean,
    modifier: Modifier = Modifier,
    maxDistance: Dp = PullMaxDistance,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .size(72.dp)
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                layout(placeable.width, placeable.height) {
                    placeable.placeWithLayer(
                        0,
                        0,
                        layerBlock = {
                            translationY = distanceFraction() * maxDistance.roundToPx() - size.height
                            alpha = if (isRefreshing) 1f else (distanceFraction() * 2f).coerceIn(0f, 1f)
                        }
                    )
                }
            },
        contentAlignment = Alignment.Center,
        content = content
    )
}
