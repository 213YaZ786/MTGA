package com.mtga.app.screenshot

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ShapeDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mtga.app.core.model.Post
import com.mtga.app.core.model.PostStats
import com.mtga.app.di.appModule
import com.mtga.app.navigation.LocalReadableInset
import com.mtga.app.ui.component.BannerAction
import com.mtga.app.ui.component.BoldIconButton
import com.mtga.app.ui.component.DropdownMenuGroup
import com.mtga.app.ui.component.FeedBanner
import com.mtga.app.ui.component.GroupSpacing
import com.mtga.app.ui.component.MenuPill
import com.mtga.app.ui.component.PostCard
import com.mtga.app.ui.component.SelectableDropdownMenuItem
import com.mtga.app.ui.component.ZoneSurface
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
 * DRAFT for the user's yes (requests.md point 2), nothing of app/src/main changes: Home's top with every button shown
 * (the alert at the left, Search at the right), as it is and as LinkedOut built it (its points 10 and 11):
 * home_top_now (HomeHeader as built), home_top_draft (one row: the reader kit's FeedBanner, the feed MenuPill closed in the
 * middle with the age beside it), home_top_menu_draft (the same with the pill's menu open). The row is the shape of
 * Bluesky's header, a slot at each side and the centre between them:
 * https://github.com/bluesky-social/social-app/blob/main/src/view/com/home/HomeHeaderLayoutMobile.tsx#HomeHeaderLayoutMobile
 * The draft is drawn as LinkedOut's TimelineScreen draws it (feedPill and banner), with the same sample cards:
 * https://github.com/213YaZ786/LinkedOut/blob/main/app/src/test/java/com/linkedout/app/screenshot/HomeTopDraftScreenshotTest.kt#HomeTopDraftScreenshotTest
 * and the current header is MTGA's own, private to TimelineScreen.kt, so a faithful copy of it:
 * https://github.com/213YaZ786/MTGA/blob/main/app/src/main/java/com/mtga/app/feature/timeline/TimelineScreen.kt#HomeHeader
 * not ported: HomeHeaderLayoutMobile's MenuButton, the Logo and its PressableScale, the Feeds link, the haptics and soft
 * reset, the hide on scroll (headerMinimalShellTransform), the tablet width style, StatusBarFade and
 * STATUS_BAR_FADE_STOPS (the app's own ground and status bar padding stay)
 * not ported: LinkedOut's variants a, b, c and d of the top (the user chose one, the draft is its built form), its
 * AlertAction copy (MTGA's BannerAction already keeps a tint asked for in glass), TimelineScreen's FailureDialog,
 * TimelineFooter, EmptyState, the pull to refresh, the ChallengePill, subtitle(state), the viewModel and the clock
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = android.app.Application::class, sdk = [36])
class HomeTopDraftScreenshotTest : ScreenshotBase() {

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

    // The card writes the age against the clock, so the posts are dated from it.
    private val now = System.currentTimeMillis()

    private val unread = Post(
        id = "1850000000000000001",
        authorHandle = "NASA",
        authorName = "NASA",
        text = "The crew finished their spacewalk today. Watch the replay and read what they fixed.",
        publishedAtMillis = now - 2 * 60 * 60 * 1000,
        permalink = "https://x.com/NASA/status/1850000000000000001",
        stats = PostStats(replies = 128, reposts = 412, likes = 9_300)
    )

    private val read = Post(
        id = "1850000000000000002",
        authorHandle = "BBCWorld",
        authorName = "BBC News (World)",
        text = "Talks resume on Monday, officials say, after a week of delays.",
        publishedAtMillis = now - 26 * 60 * 60 * 1000,
        permalink = "https://x.com/BBCWorld/status/1850000000000000002",
        stats = PostStats(replies = 41, reposts = 97, likes = 2_100)
    )

    private val third = Post(
        id = "1850000000000000003",
        authorHandle = "github",
        authorName = "GitHub",
        text = "A good week for the people building in the open. Thank you to every maintainer.",
        publishedAtMillis = now - 3 * 24 * 60 * 60 * 1000,
        permalink = "https://x.com/github/status/1850000000000000003",
        stats = PostStats(replies = 20, reposts = 64, likes = 1_450)
    )

    // The folders as AccountStore keeps them (FolderNames.ordered): Main first, the rest by name.
    private val folders = listOf("Main", "Friends", "News")

    // HomeHeader as it is: the title in a zone, the freshness line under it, the alert in the tonal error button, Search.
    @Test
    fun now() = capture("home_top_now") {
        Screen { HomeHeader() }
    }

    // FeedBanner as TimelineScreen's banner: the alert BannerAction leading (error container, error tint), the feed pill in
    // the middle with the age beside it, Search trailing.
    @Test
    fun draft() = capture("home_top_draft") {
        Screen { Banner(onFeedPill = {}) }
    }

    // The pill's menu open over the screen: All and the folders, then Liked and Archived (TimelineScreen's feedPill).
    @Test
    fun menu() = capture("home_top_menu_draft") {
        var anchor by remember { mutableStateOf(IntOffset.Zero) }
        MenuOverScreen(open = true, anchor = anchor, screen = { Screen { Banner(onFeedPill = { anchor = it }) } }) {
            DropdownMenuGroup(index = 0, count = 2) {
                SelectableDropdownMenuItem(selected = true, onClick = {}, text = "All")
                folders.forEach { SelectableDropdownMenuItem(selected = false, onClick = {}, text = it) }
            }
            DropdownMenuGroup(index = 1, count = 2) {
                listOf("Liked", "Archived").forEach { SelectableDropdownMenuItem(selected = false, onClick = {}, text = it) }
            }
        }
    }

    // The screen under the top: the sample cards, in the list's side padding.
    @Composable
    private fun Screen(top: @Composable () -> Unit) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            Box(Modifier.padding(horizontal = LocalReadableInset.current)) { top() }
            Column(
                Modifier.padding(horizontal = 16.dp + LocalReadableInset.current),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PostCard(post = unread, onClick = {}, onOpenLink = {}, onDownload = {}, unread = true)
                PostCard(post = read, onClick = {}, onOpenLink = {}, onDownload = {})
                PostCard(post = third, onClick = {}, onOpenLink = {}, onDownload = {})
            }
        }
    }

    @Composable
    private fun Banner(onFeedPill: (IntOffset) -> Unit) {
        FeedBanner(
            centre = {
                MenuPill(
                    icon = MtgaIcons.Folder,
                    label = "All",
                    expanded = false,
                    trailingIcon = ExpandMore,
                    modifier = Modifier.onGloballyPositioned {
                        val at = it.positionInRoot()
                        onFeedPill(IntOffset((at.x + it.size.width / 2).toInt(), (at.y + it.size.height).toInt()))
                    },
                    onClick = {}
                )
            },
            subtitle = "Updated 5m ago",
            leading = {
                BannerAction(
                    icon = WarningRounded,
                    label = "What went wrong",
                    onClick = {},
                    container = MaterialTheme.colorScheme.errorContainer,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            trailing = {
                BannerAction(icon = MtgaIcons.Search, label = "Search saved posts", onClick = {})
            }
        )
    }

    // A faithful copy of HomeHeader (TimelineScreen.kt, private) for a reader who follows accounts and made a folder: the
    // alert in the tonal error button, the title with its arrow (the whole title is the tap), the freshness line, Search.
    @Composable
    private fun HomeHeader() {
        ZoneSurface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            shape = ShapeDefaults.ExtraLarge
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BoldIconButton(
                    onClick = {},
                    modifier = Modifier.size(ButtonSize),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ),
                    edge = MaterialTheme.colorScheme.error
                ) {
                    Icon(MtgaIcons.Pulse, contentDescription = "What went wrong", modifier = Modifier.size(IconSize))
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(ShapeDefaults.Large)
                        .clickable(onClickLabel = "Choose which folder to show", onClick = {})
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Home",
                            style = MaterialTheme.typography.titleLarge,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Icon(
                            MtgaIcons.ArrowDown,
                            contentDescription = null,
                            modifier = Modifier.padding(start = 4.dp).size(20.dp)
                        )
                    }
                    Text(
                        "Updated 5m ago",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }

                BoldIconButton(onClick = {}, modifier = Modifier.size(ButtonSize)) {
                    Icon(MtgaIcons.Search, contentDescription = "Search saved posts", modifier = Modifier.size(IconSize))
                }
            }
        }
    }

    // TimelineScreen's BUTTON_SIZE and ICON_SIZE (private there).
    private val ButtonSize = 48.dp
    private val IconSize = 24.dp
}

// Material Symbols Rounded "expand_more", the pill's chevron: the app's icon set has only ArrowDown, a long arrow.
// https://github.com/google/material-design-icons/blob/master/symbols/android/expand_more/materialsymbolsoutlined/expand_more_24px.xml
internal val ExpandMore: ImageVector by lazy {
    val pathData = "M480,615L240,375L296,319L480,503L664,319L720,375L480,615Z"
    ImageVector.Builder("ExpandMore", 24.dp, 24.dp, viewportWidth = 960f, viewportHeight = 960f)
        .addPath(pathData = PathParser().parsePathString(pathData).toNodes(), fill = SolidColor(Color.White))
        .build()
}

// Material Symbols Rounded "warning", fill 1: the alert's triangle with rounded corners, the path of FailureDialog's own
// FailureIcons.Warning (private there, the same file as LinkedOut's) kept here for the draft.
// https://github.com/google/material-design-icons/blob/master/symbols/android/warning/materialsymbolsrounded/warning_fill1_24px.xml
internal val WarningRounded: ImageVector by lazy {
    val pathData = "M109,840Q98,840 89,834.5Q80,829 75,820Q70,811 69.5,800.5Q69,790 75,780L445,140Q451,130 460.5,125" +
        "Q470,120 480,120Q490,120 499.5,125Q509,130 515,140L885,780Q891,790 890.5,800.5Q890,811 885,820" +
        "Q880,829 871,834.5Q862,840 851,840L109,840ZM480,720Q497,720 508.5,708.5Q520,697 520,680Q520,663 508.5,651.5" +
        "Q497,640 480,640Q463,640 451.5,651.5Q440,663 440,680Q440,697 451.5,708.5Q463,720 480,720Z" +
        "M480,600Q497,600 508.5,588.5Q520,577 520,560L520,440Q520,423 508.5,411.5Q497,400 480,400Q463,400 451.5,411.5" +
        "Q440,423 440,440L440,560Q440,577 451.5,588.5Q463,600 480,600Z"
    ImageVector.Builder("Warning", 24.dp, 24.dp, viewportWidth = 960f, viewportHeight = 960f)
        .addPath(pathData = PathParser().parsePathString(pathData).toNodes(), fill = SolidColor(Color.White))
        .build()
}

private val MenuOverWidth: Dp = 280.dp

// The screen with the menu over it: a scrim on the screen, then the menu under the pill that opened it (a Popup in the
// app, Menu.kt's DropdownMenu, which Robolectric does not draw). [anchor] is the point under the pill's middle, in the
// window; the menu is kept 12 dp from the screen's sides. Ported from LinkedOut's MenuOverScreen
// https://github.com/213YaZ786/LinkedOut/blob/main/app/src/test/java/com/linkedout/app/screenshot/JobsDraftScreenshotTest.kt#MenuOverScreen
// https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/Menu.kt#DropdownMenu
// not ported: JobsDraftScreenshotTest's own DropdownMenuGroup, MenuItemRow and SelectableDropdownMenuItem (the app's
// GlassMenu.kt has them, used as they are), its Jobs and job detail drafts, its filters and date menus
@Composable
internal fun MenuOverScreen(
    open: Boolean,
    anchor: IntOffset,
    screen: @Composable () -> Unit,
    menu: @Composable ColumnScope.() -> Unit
) {
    var origin by remember { mutableStateOf(IntOffset.Zero) }
    var width by remember { mutableStateOf(0) }
    val density = LocalDensity.current
    Box(
        Modifier.fillMaxSize().onGloballyPositioned {
            val at = it.positionInRoot()
            origin = IntOffset(at.x.toInt(), at.y.toInt())
            width = it.size.width
        }
    ) {
        screen()
        if (open) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f)))
            val menuWidth = with(density) { MenuOverWidth.roundToPx() }
            val margin = with(density) { 12.dp.roundToPx() }
            val gap = with(density) { 8.dp.roundToPx() }
            // The menu is centred under the pill: [anchor].x is the pill's middle.
            val x = (anchor.x - origin.x - menuWidth / 2).coerceIn(margin, (width - menuWidth - margin).coerceAtLeast(margin))
            Column(
                Modifier.offset { IntOffset(x, anchor.y - origin.y + gap) }.width(MenuOverWidth),
                verticalArrangement = Arrangement.spacedBy(GroupSpacing),
                content = menu
            )
        }
    }
}
