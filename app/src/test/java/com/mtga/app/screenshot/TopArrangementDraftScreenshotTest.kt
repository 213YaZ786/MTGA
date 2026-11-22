package com.mtga.app.screenshot

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.selection.selectable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import com.mtga.app.ui.component.DropdownMenuGroup
import com.mtga.app.ui.component.DropdownMenuItem
import com.mtga.app.ui.component.DropdownMenuItemContent
import com.mtga.app.ui.component.FeatherArt
import com.mtga.app.ui.component.FeatherInk
import com.mtga.app.ui.component.drawFeather
import org.robolectric.RuntimeEnvironment
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ShapeDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mtga.app.core.model.Post
import com.mtga.app.core.model.PostStats
import com.mtga.app.di.appModule
import com.mtga.app.feature.accounts.AccountRow
import com.mtga.app.navigation.LocalReadableInset
import com.mtga.app.ui.component.Avatar
import com.mtga.app.ui.component.BannerAction
import com.mtga.app.ui.component.FeedBanner
import com.mtga.app.ui.component.FloatingAction
import com.mtga.app.ui.component.FloatingPane
import com.mtga.app.ui.component.GlassSearchField
import com.mtga.app.ui.component.MenuPill
import com.mtga.app.ui.component.PostCard
import com.mtga.app.ui.component.ZoneSurface
import com.mtga.app.ui.component.relativeTime
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
 * DRAFT for the user's yes (requests.md point 41), nothing of app/src/main changes: Home's and Accounts' tops, today
 * then two arrangements, each over sample posts or accounts.
 * A, the search capsule, Material 3 Expressive's app bar with search (Gmail): one glass capsule the width of the page,
 * the folder switch leading, "Search" in the middle, the alert trailing on a failure only, the age or the count under it.
 * https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/SearchBar.kt#AppBarWithSearch
 * B, the large title, Material 3 Expressive's large top app bar with a subtitle (iOS Mail): the folder's name as the big
 * bold title with its chevron, the age or the count as its subtitle, a round glass Search at the right, the alert at the
 * left on a failure only.
 * https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/AppBar.kt#LargeFlexibleTopAppBar
 * Pictures (prefix top_arrangement_draft): home_today, home_a, home_b, accounts_today, accounts_a, accounts_b, then
 * home_a_alert, home_b_alert and home_b_long (the folder "International news and politics") for the edges, and the
 * 200 % text and 360 dp edges of home_a_alert, home_b_long and accounts_b.
 * Today is the app's own FeedBanner with its MenuPill, as TimelineScreen and AccountsScreen build it; the posts and the
 * account rows are local copies of the app's (AccountCard is private).
 * The folder's GlassMenu is a Popup that Robolectric does not draw: no picture of it open here (HomeTopDraftScreenshotTest
 * draws Home's menu); the switch of A and B is shown closed.
 * not ported: AppBarWithSearch's SearchBarState, the expanded search (ExpandedFullScreenContainedSearchBar), the colours and
 * animateColorAsState of the scroll, tonal and shadow elevation, scrollBehavior and windowInsets (the page has its own
 * insets), SearchBarImpl (the input field is the page's own content); LargeFlexibleTopAppBar's TwoRowsTopAppBar collapse
 * on scroll, collapsedHeight and expandedHeight tokens, scrollBehavior, colors, windowInsets and the small title (the top
 * scrolls away as a list item, as today)
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = android.app.Application::class, sdk = [36])
class TopArrangementDraftScreenshotTest : ScreenshotBase() {

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

    private val rows = listOf(
        AccountRow("NASA", "NASA", null, now - 2 * 60 * 60 * 1000, "News"),
        AccountRow("BBCWorld", "BBC News (World)", null, now - 26 * 60 * 60 * 1000, "News"),
        AccountRow("github", "GitHub", null, now - 3 * 24 * 60 * 60 * 1000, "Main"),
        AccountRow("jsmith", null, null, null, "Friends")
    )

    private val longFolder = "International news and politics"

    @Test
    fun homeToday() = capture("top_arrangement_draft_home_today") { HomeScreen { TodayHome("All", alert = false) } }

    @Test
    fun homeA() = capture("top_arrangement_draft_home_a") { HomeScreen { SearchCapsule("All", "Search", "Updated 3 min ago", alert = false) } }

    @Test
    fun homeB() = capture("top_arrangement_draft_home_b") { HomeScreen { LargeTitleTop("All", "Updated 3 min ago", search = true, alert = false) } }

    @Test
    fun accountsToday() = capture("top_arrangement_draft_accounts_today") { AccountsScreen { TodayAccounts("All") } }

    @Test
    fun accountsA() = capture("top_arrangement_draft_accounts_a") {
        AccountsScreen { SearchCapsule("All", "Search or open a handle", "12 followed", alert = false) }
    }

    @Test
    fun accountsB() = capture("top_arrangement_draft_accounts_b") {
        AccountsScreen { LargeTitleTop("News", "12 followed", search = false, alert = false); Field() }
    }

    // The alert showing: the failure that today sits at the left of the row.
    @Test
    fun homeAAlert() = capture("top_arrangement_draft_home_a_alert") {
        HomeScreen { SearchCapsule("All", "Search", "Updated 3 min ago", alert = true) }
    }

    @Test
    fun homeBAlert() = capture("top_arrangement_draft_home_b_alert") {
        HomeScreen { LargeTitleTop("All", "Updated 3 min ago", search = true, alert = true) }
    }

    // A long folder name: the title wraps to its lines, the chevron after the last word.
    @Test
    fun homeBLong() = capture("top_arrangement_draft_home_b_long") {
        HomeScreen { LargeTitleTop(longFolder, "Updated 3 min ago", search = true, alert = false) }
    }

    @Test
    fun edgeHomeAAlert() = captureEdge("top_arrangement_draft_home_a_alert") {
        HomeScreen { SearchCapsule("All", "Search", "Updated 3 min ago", alert = true) }
    }

    @Test
    fun edgeHomeBLong() = captureEdge("top_arrangement_draft_home_b_long") {
        HomeScreen { LargeTitleTop(longFolder, "Updated 3 min ago", search = true, alert = false) }
    }

    @Test
    fun edgeAccountsB() = captureEdge("top_arrangement_draft_accounts_b") {
        AccountsScreen { LargeTitleTop("News", "12 followed", search = false, alert = false); Field() }
    }

    // The screen under the top: the sample cards, in the list's side padding.
    @Composable
    private fun HomeScreen(top: @Composable () -> Unit) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            Box(Modifier.padding(horizontal = LocalReadableInset.current)) { Column { top() } }
            Column(
                Modifier.padding(horizontal = 16.dp + LocalReadableInset.current),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                posts.forEach { PostCard(post = it, onClick = {}, onOpenLink = {}, onDownload = {}, unread = it === posts.first()) }
            }
        }
    }

    @Composable
    private fun AccountsScreen(top: @Composable () -> Unit) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            Box(Modifier.padding(horizontal = LocalReadableInset.current)) { Column { top() } }
            Column(
                Modifier.padding(horizontal = 16.dp + LocalReadableInset.current),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rows.forEach { AccountCard(it) }
            }
        }
    }

    // Today's Home top: TimelineScreen's FeedBanner, the pill and the age beside it, the alert left, Search right.
    @Composable
    private fun TodayHome(label: String, alert: Boolean) {
        FeedBanner(
            label = label,
            subtitleForms = listOf("Updated 3 min ago", "3m ago", "3m"),
            pill = { shown -> MenuPill(icon = MtgaIcons.Folder, label = shown, expanded = false, trailingIcon = MtgaIcons.ExpandMore, onClick = {}) },
            leading = if (alert) ({ AlertAction() }) else null,
            trailing = { BannerAction(icon = MtgaIcons.Search, label = "Search saved posts", onClick = {}) }
        )
    }

    // Today's Accounts top: AccountsScreen's FeedBanner with both sides empty, then the field.
    @Composable
    private fun TodayAccounts(label: String) {
        FeedBanner(
            label = label,
            subtitleForms = listOf("12 followed", "12"),
            pill = { shown -> MenuPill(icon = MtgaIcons.Folder, label = shown, expanded = false, trailingIcon = MtgaIcons.ExpandMore, onClick = {}) }
        )
        Field()
    }

    @Composable
    private fun AlertAction() {
        BannerAction(
            icon = MtgaIcons.Warning,
            label = "What went wrong",
            onClick = {},
            container = MaterialTheme.colorScheme.errorContainer,
            tint = MaterialTheme.colorScheme.error
        )
    }

    // A: one capsule of glass the width of the page, the age or the count under it, centred.
    @Composable
    private fun SearchCapsule(folder: String, hint: String, status: String, alert: Boolean) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            AppBarWithSearch(
                inputField = {
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClickLabel = hint, onClick = {}).padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(MtgaIcons.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                        Text(
                            hint,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = { FolderSwitch(folder, style = MaterialTheme.typography.labelLarge) },
                actions = if (alert) ({ AlertAction() }) else null
            )
            Text(
                status,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }

    // The folder switch: icon, name, chevron, the whole of it the tap that opens the GlassMenu.
    @Composable
    private fun FolderSwitch(folder: String, style: androidx.compose.ui.text.TextStyle, modifier: Modifier = Modifier) {
        Row(
            modifier.clip(CircleShape).clickable(onClickLabel = "Choose which folder to show", onClick = {}).padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(MtgaIcons.Folder, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(folder, style = style, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Icon(MtgaIcons.ExpandMore, contentDescription = null, modifier = Modifier.size(20.dp))
        }
    }

    // B: the folder's name as the big bold title with its chevron, its subtitle under it, centred; the alert at the left
    // and Search at the right keep their room, so the title is centred on the page.
    @Composable
    private fun LargeTitleTop(folder: String, status: String, search: Boolean, alert: Boolean) {
        LargeFlexibleTopAppBar(
            title = {
                // The chevron is a mark at the end of the title's text (an inline placeholder, as a mark ends the name in
                // PostCard's NameRow), so it follows the last word; two lines at most, the size stepping down to 20 sp
                // before the title ends with an ellipsis (BarTitle's TextAutoSize).
                val style = MaterialTheme.typography.headlineLarge
                val chevron = "chevron"
                BasicText(
                    buildAnnotatedString {
                        append(folder)
                        append(" ")
                        appendInlineContent(chevron, "v")
                    },
                    modifier = Modifier
                        .clip(ShapeDefaults.Large)
                        .clickable(onClickLabel = "Choose which folder to show", onClick = {})
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                        .semantics { heading() },
                    style = style.copy(color = LocalContentColor.current, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    inlineContent = mapOf(
                        chevron to InlineTextContent(
                            Placeholder(width = 0.9.em, height = 0.9.em, placeholderVerticalAlign = PlaceholderVerticalAlign.Center)
                        ) { Icon(MtgaIcons.ExpandMore, contentDescription = null, modifier = Modifier.fillMaxSize()) }
                    ),
                    autoSize = TextAutoSize.StepBased(minFontSize = 20.sp, maxFontSize = style.fontSize, stepSize = 0.5.sp)
                )
            },
            subtitle = {
                Text(status, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            },
            navigationIcon = { if (alert) AlertAction() },
            actions = { if (search) FloatingAction(icon = MtgaIcons.Search, label = "Search saved posts", onClick = {}) }
        )
    }

    // ---- A2: the user's choice A, without the line under the capsule (requests.md point 41) ----

    private val homeHints = listOf("Search")
    private val accountsHints = listOf("Search 12 accounts or open a handle", "Search 12 accounts")

    // Pictures: a2_home (capsule, posts right under it), a2_home_pull (the pull before release), a2_accounts, then the
    // folder menu open from the capsule's switch on each, and the edges (200 % text, 360 dp) of all five.
    @Test
    fun homeA2() = capture("top_arrangement_draft_a2_home") { HomeScreen { A2Capsule("All", homeHints, onSwitch = {}) } }

    @Test
    fun homeA2Pull() = capture("top_arrangement_draft_a2_home_pull", interact = { blurFeed(Size.medium) }) { PullScreen(progress = 0.6f) }

    @Test
    fun accountsA2() = capture("top_arrangement_draft_a2_accounts") { AccountsScreen { A2Capsule("All", accountsHints, onSwitch = {}) } }

    @Test
    fun homeA2Menu() = capture("top_arrangement_draft_a2_home_menu") { HomeMenu() }

    @Test
    fun accountsA2Menu() = capture("top_arrangement_draft_a2_accounts_menu") { AccountsMenu() }

    @Test
    fun edgeHomeA2() = captureEdge("top_arrangement_draft_a2_home") { HomeScreen { A2Capsule("All", homeHints, onSwitch = {}) } }

    @Test
    fun edgeHomeA2Pull() = captureEdge("top_arrangement_draft_a2_home_pull", interact = { blurFeed(Size.medium) }) { PullScreen(progress = 0.6f) }

    @Test
    fun edgeAccountsA2() = captureEdge("top_arrangement_draft_a2_accounts") { AccountsScreen { A2Capsule("All", accountsHints, onSwitch = {}) } }

    @Test
    fun edgeHomeA2Menu() = captureEdge("top_arrangement_draft_a2_home_menu") { HomeMenu() }

    @Test
    fun edgeAccountsA2Menu() = captureEdge("top_arrangement_draft_a2_accounts_menu") { AccountsMenu() }

    @Composable
    private fun HomeMenu() {
        var anchor by remember { mutableStateOf(IntOffset.Zero) }
        MenuOverScreen(
            open = true,
            anchor = anchor,
            screen = { HomeScreen { A2Capsule("All", homeHints, onSwitch = { anchor = it }) } }
        ) {
            DropdownMenuGroup(index = 0, count = 2) { FolderRows() }
            DropdownMenuGroup(index = 1, count = 2) {
                listOf("Liked", "Archived").forEach { DropdownMenuItem(text = it, onClick = {}) }
            }
        }
    }

    @Composable
    private fun AccountsMenu() {
        var anchor by remember { mutableStateOf(IntOffset.Zero) }
        MenuOverScreen(
            open = true,
            anchor = anchor,
            screen = { AccountsScreen { A2Capsule("All", accountsHints, onSwitch = { anchor = it }) } }
        ) {
            DropdownMenuGroup(index = 0, count = 2) { FolderRows() }
            DropdownMenuGroup(index = 1, count = 2) {
                DropdownMenuItem(
                    text = "Manage folders",
                    onClick = {},
                    leadingIcon = { Icon(MtgaIcons.Tune, contentDescription = null, modifier = Modifier.size(24.dp)) },
                    trailingIcon = {
                        Icon(ChevronRight, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                )
            }
        }
    }

    @Composable
    private fun FolderRows() {
        CountedMenuItem(selected = true, text = "All", count = 12)
        CountedMenuItem(selected = false, text = "News", count = 3)
        CountedMenuItem(selected = false, text = "Friends", count = 1)
        CountedMenuItem(selected = false, text = "Main", count = 3)
    }

    /**
     * A folder row with its count at the end: the choice of GlassMenu's SelectableDropdownMenuItem, and the count in the
     * badge slot of NavigationDrawerItem (Gmail's drawer): after the label, 12 dp from it, labelLarge, the badge colour
     * following the selection, the label taking the room so the count sits at the right.
     * https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/NavigationDrawer.kt#NavigationDrawerItem
     * not ported: NavigationDrawerItem's Surface, its Role.Tab, shape, the drawer colours (NavigationDrawerItemColors,
     * NavigationDrawerItemDefaults, the 24 dp end padding: the menu's own row keeps its 16 dp), the icon slot (the menu's
     * check slot takes its place), interactionSource
     */
    @Composable
    private fun CountedMenuItem(selected: Boolean, text: String, count: Int) {
        val content = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
        val badge = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
        DropdownMenuItemContent(
            selected = selected,
            interaction = Modifier.selectable(selected = selected, role = Role.RadioButton, onClick = {})
        ) {
            if (selected) {
                Icon(MtgaIcons.Check, contentDescription = null, modifier = Modifier.size(24.dp), tint = content)
            } else {
                Spacer(Modifier.size(24.dp))
            }
            Box(Modifier.weight(1f)) { Text(text, style = MaterialTheme.typography.bodyLarge, color = content) }
            Text(count.toString(), style = MaterialTheme.typography.labelLarge, color = badge)
        }
    }

    // A's capsule exactly, nothing under it; the field shrinks before it cuts, then drops to the next shorter form
    // (as FeedBanner's subtitleForms do). [onSwitch] gives the point under the folder switch, where the menu opens.
    @Composable
    private fun A2Capsule(folder: String, hints: List<String>, onSwitch: (IntOffset) -> Unit) {
        var form by remember(hints) { mutableStateOf(0) }
        val style = MaterialTheme.typography.bodyLarge
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            AppBarWithSearch(
                inputField = {
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClickLabel = hints[0], onClick = {}).padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(MtgaIcons.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                        BasicText(
                            hints[form],
                            style = style.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            autoSize = TextAutoSize.StepBased(minFontSize = 14.sp, maxFontSize = style.fontSize, stepSize = 0.5.sp),
                            onTextLayout = { if (it.hasVisualOverflow && form < hints.lastIndex) form++ }
                        )
                    }
                },
                navigationIcon = {
                    FolderSwitch(
                        folder,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.onGloballyPositioned {
                            val at = it.positionInRoot()
                            onSwitch(IntOffset((at.x + it.size.width / 2).toInt(), (at.y + it.size.height).toInt()))
                        }
                    )
                }
            )
        }
    }

    /**
     * The pull before release: the page blurred, over it the app's counted feather ring part filled by the pull, the age of
     * the last update in its centre as MJRefresh puts the last update under its state, here in the pull itself.
     * https://github.com/CoderMJLee/MJRefresh/blob/master/MJRefresh/Custom/Header/MJRefreshStateHeader.m#lastUpdatedTimeLabel
     * not ported: MJRefreshStateHeader's stateLabel and its titles per state, lastUpdatedTimeText's date formats (the app's
     * own "Updated N ago" is the text), the NSUserDefaults key of the last time, placeSubviews and its arrow and loading
     * views, setState, prepare and the localized strings
     */
    @Composable
    private fun PullScreen(progress: Float) {
        val mine = remember { Blurred().also { blurred = it } }
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().testTag("feed")) { HomeScreen { A2Capsule("All", homeHints, onSwitch = {}) } }
            mine.image?.let { picture ->
                Image(picture, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
            }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                FeatherRing(progress = { progress }) {
                    Text(
                        "Updated 3 min ago",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 28.dp)
                    )
                }
            }
        }
    }

    // The blurred page of the picture being drawn, filled by [blurFeed] once the page has settled (PullRingDraftScreenshotTest's).
    private class Blurred {
        var image: ImageBitmap? by mutableStateOf(null)
    }

    private var blurred: Blurred? = null

    // Neko's Modifier.blur(radius) by hand: a stack blur of radius r has the sigma r / sqrt(6), so r = sigma * sqrt(6) (pixels).
    // https://github.com/nekomangaorg/Neko/blob/main/app/src/main/java/org/nekomanga/presentation/components/PullRefresh.kt#PullRefresh
    private fun blurFeed(radius: Dp) {
        val shot = compose.onNodeWithTag("feed").captureToImage().asAndroidBitmap()
        val density = RuntimeEnvironment.getApplication().resources.displayMetrics.density
        val sigma = radius.value * density
        val radiusPx = (sigma * sqrt(6f)).roundToInt().coerceAtMost(254)
        blurred?.image = blur(shot, radiusPx, false)!!.asImageBitmap()
        compose.waitForIdle()
    }

    /** Neko's Size.medium. https://github.com/nekomangaorg/Neko/blob/main/app/src/main/java/org/nekomanga/presentation/theme/Constants.kt#Size */
    private object Size {
        val medium = 16.dp
    }

    private val SLOTS = 16

    /**
     * CircularProgressIndicator's determinate form: 168 dp, start at 12 o'clock (270 degrees), sweep = progress x 360, the
     * slots inside the sweep in full colour, the others as the track, each feather laid radially, drawn by LoadingMark's
     * drawFeather (as PullRingDraftScreenshotTest draws it).
     * https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/ProgressIndicator.kt#CircularProgressIndicator
     */
    @Composable
    private fun FeatherRing(progress: () -> Float, centre: @Composable ColumnScope.() -> Unit) {
        val coercedProgress = { progress().coerceIn(0f, 1f) }
        val accent = MaterialTheme.colorScheme.primary
        val ink = remember(accent) {
            FeatherInk(
                base = lerp(accent, Color.White, 0.72f), middle = lerp(accent, Color.White, 0.38f),
                tip = accent, shaft = lerp(accent, Color.White, 0.3f)
            )
        }
        val track = MaterialTheme.colorScheme.outlineVariant
        val trackInk = remember(track) { FeatherInk(track, track, track, track) }
        val art = remember { FeatherArt() }
        Box(
            Modifier
                .semantics(mergeDescendants = true) {
                    progressBarRangeInfo = ProgressBarRangeInfo(coercedProgress().takeUnless { it.isNaN() } ?: 0f, 0f..1f)
                }
                .size(168.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val startAngle = 270f
                val sweep = coercedProgress() * 360f
                val unit = 0.4.dp.toPx() // one unit of the feather's 108 grid
                val featherLength = 72f * unit
                val radius = size.minDimension / 2f - featherLength / 2f
                val c = art.centre
                for (i in 0 until SLOTS) {
                    val angle = i * 360f / SLOTS
                    val on = angle < sweep
                    val rad = Math.toRadians((startAngle + angle).toDouble())
                    val x = size.width / 2f + radius * cos(rad).toFloat()
                    val y = size.height / 2f + radius * sin(rad).toFloat()
                    translate(x - c.x, y - c.y) {
                        rotate(angle - 45f, pivot = c) {
                            scale(unit, pivot = c) {
                                drawFeather(art, if (on) ink else trackInk, if (on) 1f else 0.35f)
                            }
                        }
                    }
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, content = centre)
        }
    }

    /**
     * Stack Blur v1.0 by Mario Klingemann (http://incubator.quasimondo.com), Android port by Yahel Bouaziz, as Wasabeef's
     * https://github.com/wasabeef/glide-transformations/blob/main/transformations/src/main/java/jp/wasabeef/glide/transformations/internal/FastBlur.java#blur
     * Apache-2.0.
     */
    private fun blur(sentBitmap: Bitmap, radius: Int, canReuseInBitmap: Boolean): Bitmap? {
        val bitmap = if (canReuseInBitmap) sentBitmap else sentBitmap.copy(Bitmap.Config.ARGB_8888, true)
        if (radius < 1) return null
        val w = bitmap.width
        val h = bitmap.height
        val pix = IntArray(w * h)
        bitmap.getPixels(pix, 0, w, 0, 0, w, h)
        val wm = w - 1
        val hm = h - 1
        val wh = w * h
        val div = radius + radius + 1
        val r = IntArray(wh)
        val g = IntArray(wh)
        val b = IntArray(wh)
        var rsum: Int
        var gsum: Int
        var bsum: Int
        var x: Int
        var y: Int
        var i: Int
        var p: Int
        var yp: Int
        var yi: Int
        var yw: Int
        val vmin = IntArray(max(w, h))
        var divsum = (div + 1) shr 1
        divsum *= divsum
        val dv = IntArray(256 * divsum)
        i = 0
        while (i < 256 * divsum) {
            dv[i] = (i / divsum)
            i++
        }
        yw = 0
        yi = 0
        val stack = Array(div) { IntArray(3) }
        var stackpointer: Int
        var stackstart: Int
        var sir: IntArray
        var rbs: Int
        val r1 = radius + 1
        var routsum: Int
        var goutsum: Int
        var boutsum: Int
        var rinsum: Int
        var ginsum: Int
        var binsum: Int
        y = 0
        while (y < h) {
            rinsum = 0; ginsum = 0; binsum = 0; routsum = 0; goutsum = 0; boutsum = 0; rsum = 0; gsum = 0; bsum = 0
            i = -radius
            while (i <= radius) {
                p = pix[yi + min(wm, max(i, 0))]
                sir = stack[i + radius]
                sir[0] = (p and 0xff0000) shr 16
                sir[1] = (p and 0x00ff00) shr 8
                sir[2] = (p and 0x0000ff)
                rbs = r1 - Math.abs(i)
                rsum += sir[0] * rbs
                gsum += sir[1] * rbs
                bsum += sir[2] * rbs
                if (i > 0) {
                    rinsum += sir[0]; ginsum += sir[1]; binsum += sir[2]
                } else {
                    routsum += sir[0]; goutsum += sir[1]; boutsum += sir[2]
                }
                i++
            }
            stackpointer = radius
            x = 0
            while (x < w) {
                r[yi] = dv[rsum]
                g[yi] = dv[gsum]
                b[yi] = dv[bsum]
                rsum -= routsum
                gsum -= goutsum
                bsum -= boutsum
                stackstart = stackpointer - radius + div
                sir = stack[stackstart % div]
                routsum -= sir[0]
                goutsum -= sir[1]
                boutsum -= sir[2]
                if (y == 0) {
                    vmin[x] = min(x + radius + 1, wm)
                }
                p = pix[yw + vmin[x]]
                sir[0] = (p and 0xff0000) shr 16
                sir[1] = (p and 0x00ff00) shr 8
                sir[2] = (p and 0x0000ff)
                rinsum += sir[0]
                ginsum += sir[1]
                binsum += sir[2]
                rsum += rinsum
                gsum += ginsum
                bsum += binsum
                stackpointer = (stackpointer + 1) % div
                sir = stack[(stackpointer) % div]
                routsum += sir[0]
                goutsum += sir[1]
                boutsum += sir[2]
                rinsum -= sir[0]
                ginsum -= sir[1]
                binsum -= sir[2]
                yi++
                x++
            }
            yw += w
            y++
        }
        x = 0
        while (x < w) {
            rinsum = 0; ginsum = 0; binsum = 0; routsum = 0; goutsum = 0; boutsum = 0; rsum = 0; gsum = 0; bsum = 0
            yp = -radius * w
            i = -radius
            while (i <= radius) {
                yi = max(0, yp) + x
                sir = stack[i + radius]
                sir[0] = r[yi]
                sir[1] = g[yi]
                sir[2] = b[yi]
                rbs = r1 - Math.abs(i)
                rsum += r[yi] * rbs
                gsum += g[yi] * rbs
                bsum += b[yi] * rbs
                if (i > 0) {
                    rinsum += sir[0]; ginsum += sir[1]; binsum += sir[2]
                } else {
                    routsum += sir[0]; goutsum += sir[1]; boutsum += sir[2]
                }
                if (i < hm) {
                    yp += w
                }
                i++
            }
            yi = x
            stackpointer = radius
            y = 0
            while (y < h) {
                // Preserve alpha channel: ( 0xff000000 & pix[yi] )
                pix[yi] = (0xff000000.toInt() and pix[yi]) or (dv[rsum] shl 16) or (dv[gsum] shl 8) or dv[bsum]
                rsum -= routsum
                gsum -= goutsum
                bsum -= boutsum
                stackstart = stackpointer - radius + div
                sir = stack[stackstart % div]
                routsum -= sir[0]
                goutsum -= sir[1]
                boutsum -= sir[2]
                if (x == 0) {
                    vmin[y] = min(y + r1, hm) * w
                }
                p = x + vmin[y]
                sir[0] = r[p]
                sir[1] = g[p]
                sir[2] = b[p]
                rinsum += sir[0]
                ginsum += sir[1]
                binsum += sir[2]
                rsum += rinsum
                gsum += ginsum
                bsum += binsum
                stackpointer = (stackpointer + 1) % div
                sir = stack[stackpointer]
                routsum += sir[0]
                goutsum += sir[1]
                boutsum += sir[2]
                rinsum -= sir[0]
                ginsum -= sir[1]
                binsum -= sir[2]
                yi += w
                y++
            }
            x++
        }
        bitmap.setPixels(pix, 0, w, 0, 0, w, h)
        return bitmap
    }

    // The search field as AccountsScreen builds it, the paste button at its end.
    @Composable
    private fun Field() {
        GlassSearchField(
            value = "",
            onValueChange = {},
            placeholder = "Search or open a handle",
            leadingIcon = { Icon(MtgaIcons.Search, contentDescription = null) },
            trailingIcon = {
                Box(Modifier.size(40.dp).clip(CircleShape).clickable(onClick = {}), contentAlignment = Alignment.Center) {
                    Icon(MtgaIcons.Paste, contentDescription = "Paste a link or a handle", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
        )
    }

    // AccountCard of AccountsScreen (private there), the folder as a small label.
    @Composable
    private fun AccountCard(row: AccountRow) {
        ZoneSurface(onClick = {}, shape = ShapeDefaults.ExtraLarge, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Avatar(url = row.avatarUrl, name = row.name ?: row.handle, size = 44.dp)
                Column(Modifier.weight(1f)) {
                    Text(row.name ?: "@${row.handle}", style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (row.name != null) {
                        Text("@${row.handle}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        row.lastPostMillis?.let(::relativeTime) ?: "Not read yet",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(row.folder, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

// Material Symbols "chevron_right", as AccountsTopDraftScreenshotTest draws it (GlassMenu.kt keeps its chevron private).
// https://github.com/google/material-design-icons/blob/master/symbols/android/chevron_right/materialsymbolsoutlined/chevron_right_24px.xml
private val ChevronRight: ImageVector by lazy {
    val pathData = "M504,480L320,296L376,240L616,480L376,720L320,664L504,480Z"
    ImageVector.Builder("ChevronRight", 24.dp, 24.dp, viewportWidth = 960f, viewportHeight = 960f, autoMirror = true)
        .addPath(pathData = PathParser().parsePathString(pathData).toNodes(), fill = SolidColor(Color.White))
        .build()
}

private val SearchBarAsTopBarPadding = 8.dp
private val AppBarWithSearchHorizontalPadding = 4.dp
private val AppBarWithSearchVerticalPadding = 4.dp

/**
 * AppBarWithSearch's layout: a Row of the navigation icon, the input field taking the rest and centred, the actions at
 * the end, the field padded by SearchBarAsTopBarPadding and AppBarWithSearchVerticalPadding, the icons by
 * AppBarWithSearchHorizontalPadding; here on the app's glass (FloatingPane) instead of Material's Surface.
 * https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/SearchBar.kt#AppBarWithSearch
 */
@Composable
private fun AppBarWithSearch(
    inputField: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable (() -> Unit)? = null,
    actions: @Composable (RowScope.() -> Unit)? = null,
    shape: Shape = CircleShape,
    contentPadding: PaddingValues = PaddingValues(all = 0.dp)
) {
    FloatingPane(shape = shape, modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(contentPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            navigationIcon?.let {
                Box(Modifier.padding(start = AppBarWithSearchHorizontalPadding)) { it() }
            }
            Box(modifier = Modifier.weight(1f)) {
                Box(
                    Modifier
                        .padding(horizontal = SearchBarAsTopBarPadding, vertical = AppBarWithSearchVerticalPadding)
                        .align(Alignment.Center)
                ) { inputField() }
            }
            actions?.let {
                Box(Modifier.padding(end = AppBarWithSearchHorizontalPadding)) {
                    Row(horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically, content = it)
                }
            }
        }
    }
}

/**
 * LargeFlexibleTopAppBar's slots: a title, a subtitle under it, a navigation icon and actions, the title aligned with
 * titleHorizontalAlignment (centred here); both side slots keep their room so the title is centred on the page.
 * https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/AppBar.kt#LargeFlexibleTopAppBar
 */
@Composable
private fun LargeFlexibleTopAppBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: (@Composable () -> Unit)? = null,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    titleHorizontalAlignment: Alignment.Horizontal = Alignment.CenterHorizontally
) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(Modifier.width(48.dp), contentAlignment = Alignment.Center) { navigationIcon() }
        Column(Modifier.weight(1f), horizontalAlignment = titleHorizontalAlignment) {
            title()
            subtitle?.let { Box(Modifier.padding(top = 2.dp)) { it() } }
        }
        Row(Modifier.width(48.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}
