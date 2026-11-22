package com.mtga.app.screenshot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
import com.mtga.app.ui.glass.LocalGlass
import com.mtga.app.ui.glass.LocalGlassBackdrop
import com.mtga.app.ui.glass.glassFloating
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
 * DRAFT for the user's yes (requests.md point 16), nothing of app/src/main changes: the pull to refresh disc, fully
 * pulled and refreshing, over the top of Home's feed. pull_disc_now is today's PullIndicator (a 56 dp disc, the mark 44 dp:
 * "on voit pas bien les plumes"); pull_disc_draft the disc 72 dp and the mark 68 dp, so the feathers fill it.
 *
 * [PullDisc] is a private-to-the-tests copy of PullIndicator with the pull held at its threshold, the state a refresh
 * leaves it in (PullToRefreshState.distanceFraction = 1 while refreshing): the disc is at fraction * threshold - height,
 * as PullToRefreshDefaults.IndicatorBox places it, so a bigger disc sits higher on the same threshold. The mark is frozen
 * (running = false, progress = 1f), which draws on Robolectric.
 * https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/pulltorefresh/PullToRefresh.kt#PullToRefreshDefaults
 * https://github.com/213YaZ786/MTGA/blob/main/app/src/main/java/com/mtga/app/ui/component/PullIndicator.kt#PullIndicator
 * The feed under it is Home's banner and cards, as HomeTopDraftScreenshotTest draws them; the list is recorded as the
 * glass backdrop, as TimelineScreen's PullToRefreshBox does, so the disc bends it.
 * not ported: PullToRefreshDefaults' Indicator and LoadingIndicator (Material's spinner and morphing shape, the app has
 * its own mark), CircularArrowProgressIndicator, drawCircularIndicator, ArrowValues, drawArrow (the arrow Material draws
 * while pulling), SpinnerContainerSize and the clipRect/shadow of IndicatorBox (the disc is glass, no shadow);
 * PullIndicator's haptic threshold effect and the fade-in alpha (a refreshing disc is fully opaque)
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = android.app.Application::class, sdk = [36])
class PullDiscDraftScreenshotTest : ScreenshotBase() {

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

    // PullIndicator as it is: 56 dp disc, mark 44 dp.
    @Test
    fun now() = capture("pull_disc_now") { Feed(disc = 56.dp, mark = 44.dp) }

    // The proposal: 72 dp disc, mark 68 dp.
    @Test
    fun draft() = capture("pull_disc_draft") { Feed(disc = 72.dp, mark = 68.dp) }

    // Home's top and cards, recorded as the glass backdrop, the disc over it at the top centre under the status bar.
    @Composable
    private fun Feed(disc: Dp, mark: Dp) {
        val look = LocalGlass.current
        val backdrop = rememberGlassBackdrop()
        CompositionLocalProvider(LocalGlassBackdrop provides backdrop.takeIf { look != null }) {
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().then(if (look != null) Modifier.glassSource(backdrop, look) else Modifier)) {
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
                PullDisc(
                    disc = disc,
                    mark = mark,
                    modifier = Modifier.align(Alignment.TopCenter).windowInsetsPadding(WindowInsets.statusBars)
                )
            }
        }
    }
}

// The PullDisc of the drafts: PullIndicator with the pull at the threshold (distanceFraction 1) and refreshing, the disc
// and the mark sizes given. PullToRefreshDefaults.PositionalThreshold is 80 dp.
@Composable
internal fun PullDisc(disc: Dp, mark: Dp, modifier: Modifier = Modifier) {
    val threshold = 80.dp
    val fraction = 1f
    Box(
        modifier.graphicsLayer {
            translationY = fraction * threshold.toPx() - this.size.height
            alpha = 1f
        }
    ) {
        // In glass, the disc bends the list under it, with no shadow.
        val look = LocalGlass.current
        val backdrop = LocalGlassBackdrop.current
        val glass = look != null && backdrop != null
        Surface(
            shape = CircleShape,
            color = if (glass) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = if (glass) 0.dp else 3.dp,
            modifier = if (glass) Modifier.size(disc).glassFloating(backdrop, CircleShape, look) else Modifier.size(disc)
        ) {
            Box(contentAlignment = Alignment.Center) {
                LoadingMark(size = mark, running = false, progress = 1f)
            }
        }
    }
}
