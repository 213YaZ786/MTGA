package com.mtga.app.screenshot

import androidx.compose.foundation.background
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ShapeDefaults
import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.ripple
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.asPaddingValues
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mtga.app.core.model.Post
import com.mtga.app.core.model.VerifiedType
import com.mtga.app.di.appModule
import com.mtga.app.navigation.LocalReadableInset
import com.mtga.app.ui.component.Avatar
import com.mtga.app.ui.component.BannerAction
import com.mtga.app.ui.component.BoldButton
import com.mtga.app.ui.component.PostCard
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
 * DRAFT for the user's yes (requests.md point 28), nothing of app/src/main changes: the profile's header compact, as
 * Bluesky's: the banner edge to edge from the window's top (PROFILE_BANNER_HEIGHT 150 dp, status bar shadow kept; its
 * picture is a STAND-IN, a vertical Brush of primary to tertiary), no back arrow (the system gesture), the avatar 90 dp
 * in a 2 dp ring of the page's colour at the start overlapping the banner, Follow (BoldButton filled, "Following"
 * outlined) at the end below the banner, then at the start the name with its verified mark, the handle, the counts on
 * one line, the bio, website, location and joined on one wrapping line, the source line. Today's header is
 * FeedScreen.kt's ProfileHeader.
 * Ports:
 * https://github.com/bluesky-social/social-app/blob/main/src/screens/Profile/Header/ProfileHeaderStandard.tsx#ProfileHeaderStandard
 * https://github.com/bluesky-social/social-app/blob/main/src/screens/Profile/Header/Shell.tsx#ProfileHeaderShell
 * https://github.com/bluesky-social/social-app/blob/main/src/screens/Profile/Header/Metrics.tsx#ProfileHeaderMetrics
 * https://github.com/bluesky-social/social-app/blob/main/src/screens/Profile/Header/layout.ts#PROFILE_BANNER_HEIGHT
 * https://github.com/bluesky-social/social-app/blob/main/src/screens/Profile/Header/DisplayName.tsx
 * https://github.com/bluesky-social/social-app/blob/main/src/screens/Profile/Header/Handle.tsx
 * https://github.com/bluesky-social/social-app/blob/main/src/screens/Profile/Header/StatusBarShadow.tsx#StatusBarShadow
 * https://github.com/TwidereProject/TwidereX-Android/blob/develop/common/src/commonMain/kotlin/com/twidere/twiderex/component/UserComponent.kt#ProfileItem
 * Icons, Material Symbols Rounded: location_on, calendar_month, link
 * https://github.com/google/material-design-icons/blob/master/symbols/android/location_on/materialsymbolsrounded/location_on_24px.xml
 * https://github.com/google/material-design-icons/blob/master/symbols/android/calendar_month/materialsymbolsrounded/calendar_month_24px.xml
 * https://github.com/google/material-design-icons/blob/master/symbols/android/link/materialsymbolsrounded/link_24px.xml
 * not ported: UserInfo's user-null and isMe branches, the banner click and its Coil picture and Placeholder, the parallax
 * (a TODO there), withElevation, UserInfoName's Mastodon lock, UserName's links, UserDescText's HTML and link
 * resolving, MastodonUserField, the dividers and the Listed count of UserMetrics (X only, no listed count here),
 * the metrics' navigation clicks; Shell.tsx's banner growth on overscroll, lightbox, haptics, the web offsets, the
 * hover colour, the dark circle (the glass action takes its place), the iOS pager shadow shift, the other header
 * parts (follow, menus, labels, live status)
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = android.app.Application::class, sdk = [36])
class ProfileHeaderDraftScreenshotTest : ScreenshotBase() {

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

    private val posts = listOf(
        Post(
            id = "1850000000000000011",
            authorHandle = "clashreport",
            authorName = "Clash Report",
            text = "Talks continue through the night, officials say a statement is expected in the morning.",
            publishedAtMillis = FIXED_NOW - 40 * 60 * 1000,
            permalink = "https://x.com/clashreport/status/1850000000000000011",
            verified = VerifiedType.BLUE
        ),
        Post(
            id = "1850000000000000012",
            authorHandle = "clashreport",
            authorName = "Clash Report",
            text = "Aid convoys crossed the border at dawn, the first in three weeks.",
            publishedAtMillis = FIXED_NOW - 3 * 60 * 60 * 1000,
            permalink = "https://x.com/clashreport/status/1850000000000000012",
            verified = VerifiedType.BLUE
        )
    )

    @Test
    fun draft() = capture("profile_header_draft") { Screen(following = false) }

    // https://github.com/bluesky-social/social-app/blob/main/src/screens/Profile/Header/ProfileHeaderStandard.tsx#HeaderStandardButtons
    // the second state of the button: Following.
    @Test
    fun followingState() = capture("profile_header_following_draft") { Screen(following = true) }

    // Edge: 200 % text and a 360 dp phone.
    @Test
    fun edge() = captureEdge("profile_header_draft") { Screen(following = false) }

    // https://github.com/bluesky-social/social-app/blob/main/src/screens/Profile/Header/DisplayName.tsx#ProfileHeaderDisplayName
    // https://github.com/bluesky-social/social-app/blob/main/src/screens/Profile/Header/Handle.tsx#ProfileHeaderHandle
    // Long content: a two line name, the handle and the website on one line each, the counts and details wrapping.
    @Test
    fun long() = capture("profile_header_long_draft") { Screen(following = false, content = LongContent) }

    // https://github.com/bluesky-social/social-app/blob/main/src/screens/Profile/Header/DisplayName.tsx#ProfileHeaderDisplayName
    // The same long content at 200 % text and a 360 dp phone.
    @Test
    fun longEdge() = captureEdge("profile_header_long_draft") { Screen(following = false, content = LongContent) }

    @Composable
    private fun Screen(following: Boolean, content: HeaderContent = ShortContent) {
        Column(Modifier.fillMaxSize()) {
            DraftHeader(following, content)
            Column(
                Modifier.padding(horizontal = LocalReadableInset.current),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                posts.forEach { PostCard(post = it, onClick = {}, onOpenLink = {}, onDownload = {}, showStats = false) }
            }
        }
    }
}

private class HeaderContent(
    val name: String, val handle: String, val bio: String, val website: String, val location: String,
    val followers: String, val following: String, val posts: String
)

private val ShortContent = HeaderContent(
    "Clash Report", "@clashreport", "Breaking news, reports, and opinions from around the world.",
    "t.me/clashreport", "Worldwide", "744K", "80", "68K"
)

private val LongContent = HeaderContent(
    "Clash Report International Breaking News and Live Updates Network",
    "@clashreport_international_live_updates",
    "Breaking news from every front, verified as it arrives. Reports, maps and analysis from our desks in five " +
        "countries. Follow for the latest, and send us what you see.",
    "t.me/clashreport_international_breaking_news_channel", "Worldwide, everywhere at once",
    "1.2M", "12,345", "987K"
)

// https://github.com/bluesky-social/social-app/blob/main/src/screens/Profile/Header/ProfileHeaderStandard.tsx#ProfileHeaderStandard
// https://github.com/bluesky-social/social-app/blob/main/src/screens/Profile/Header/Shell.tsx#ProfileHeaderShell
// The banner behind (PROFILE_BANNER_HEIGHT), the avatar at top 104 and left 10 inside its ring, the buttons row at the
// end below the banner (px_lg, pt_md), then the details at the start (px_lg).
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DraftHeader(following: Boolean, content: HeaderContent) {
    val inset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Box(Modifier.fillMaxWidth()) {
        // A stand-in for the banner picture: a vertical Brush of primary to tertiary, no Coil picture here.
        // Shell.tsx onPressBanner opens the lightbox; the app opens MediaViewer at the original size (X banners with
        // "/1500x500"), here the click does nothing.
        Box(
            Modifier.fillMaxWidth().height(ProfileBannerHeight)
                .semantics { contentDescription = "Banner" }
                .clickable(role = Role.Image, onClick = {})
                .background(
                    Brush.verticalGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary))
                )
        )
        // Bluesky's StatusBarShadow: rgba(0,0,0,0.5) to transparent, the height of the inset.
        Box(
            Modifier.fillMaxWidth().height(inset).background(
                Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.5f), Color.Transparent))
            )
        )
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(Modifier.fillMaxWidth()) {
                // GrowableAvatar: top 104, left 10, width 94, size 90: a ring of the page's colour around the avatar.
                Box(
                    Modifier.padding(top = AvatarTop, start = AvatarLeft)
                        .size(AvatarSize + AvatarRing * 2)
                        .background(MaterialTheme.colorScheme.background, CircleShape)
                        .clip(CircleShape)
                        .semantics { contentDescription = "Profile picture" }
                        // Shell.tsx onPressAvi opens the lightbox; the app opens MediaViewer at the original size
                        // (X avatars without their size suffix), here the click does nothing.
                        .clickable(role = Role.Image, onClick = {}),
                    contentAlignment = Alignment.Center
                ) {
                    Avatar(url = null, name = content.name, size = AvatarSize)
                }
                // The buttons row: justify_end, px_lg 16, pt_md 12 below the banner.
                Row(
                    Modifier.align(Alignment.TopEnd).padding(top = ProfileBannerHeight + 8.dp, end = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    FollowButton(following = following, onClick = {})
                }
            }
            Spacer(Modifier.height(4.dp))
            BoxWithConstraints(Modifier.padding(horizontal = 16.dp)) {
              val websiteMax = maxWidth * 0.6f
              Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    // DisplayName.tsx: the name wraps, capped here at 2 lines; the mark sits outside the Text, as
                    // PostCard's NameRow, so it stays after the last visible word.
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            content.name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                // PostCard's VerifiedIcon, blue.
                        Box(
                            Modifier.padding(start = 4.dp, bottom = 4.dp).size(20.dp).background(MaterialTheme.colorScheme.primary, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                MtgaIcons.CheckRounded,
                                contentDescription = "Verified blue account",
                                modifier = Modifier.size(15.dp),
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                    // Handle.tsx ProfileHeaderHandle: numberOfLines={1}.
                    Text(
                        content.handle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                ProfileHeaderMetrics(content)
                Text(content.bio, style = MaterialTheme.typography.bodyMedium)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // The website label: one line, at most 60 % of the width.
                    ProfileItem(
                        LinkRounded, "Website", content.website, MaterialTheme.colorScheme.primary,
                        Modifier.widthIn(max = websiteMax).clickable {}, singleLine = true
                    )
                    ProfileItem(LocationOnRounded, "Location", content.location)
                    ProfileItem(CalendarMonthRounded, "Joined", "Joined March 2017")
                }
              }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

// https://github.com/TwidereProject/TwidereX-Android/blob/develop/common/src/commonMain/kotlin/com/twidere/twiderex/component/UserComponent.kt#ProfileItem
// an icon (16 dp here) then the text; X's app puts them in one wrapping line.
@Composable
private fun ProfileItem(
    icon: ImageVector,
    description: String,
    text: String,
    textColor: Color = Color.Unspecified,
    modifier: Modifier = Modifier,
    singleLine: Boolean = false
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = description, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.size(4.dp))
        Text(
            text, style = MaterialTheme.typography.bodyMedium, color = textColor,
            maxLines = if (singleLine) 1 else Int.MAX_VALUE, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
    }
}

// https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/tokens/ButtonMediumTokens.kt#ButtonMediumTokens
// https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/tokens/FilledButtonTokens.kt#FilledButtonTokens
// https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/Button.kt#textStyleFor
// Follow as Material 3 Expressive's medium button with a leading icon, a clickable Box, no Material control.
// Filled (ContainerColor Primary, IconColor and LabelTextColor OnPrimary) or, once followed, outlined (OutlinedOutlineWidth).
// not ported: ContainerShapeSquare and the Selected shapes (no toggle here), disabled and elevation tokens.
@Composable
private fun FollowButton(following: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val density = LocalDensity.current
    val height = ButtonMediumContainerHeight
    // ContainerShapeRound (CornerFull) morphs to PressedContainerShape (CornerMedium = ShapeDefaults.Medium) with a spring.
    val full = with(density) { height.toPx() / 2 }
    val medium = ShapeDefaults.Medium.topStart.toPx(Size(full * 2, full * 2), density)
    val corner by animateFloatAsState(
        targetValue = if (pressed) medium else full,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 380f),
        label = "followCorner"
    )
    val shape = RoundedCornerShape(with(density) { corner.toDp() })
    val contentColor = if (following) colors.primary else colors.onPrimary
    Row(
        Modifier
            .height(height)
            .clip(shape)
            .then(
                if (following) Modifier.border(ButtonMediumOutlineWidth, colors.outline, shape)
                else Modifier.background(colors.primary, shape)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(color = contentColor),
                role = Role.Button,
                onClick = onClick
            )
            .padding(horizontal = ButtonMediumLeadingSpace),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (following) CheckRounded else PersonAddRounded,
            contentDescription = null,
            modifier = Modifier.size(ButtonMediumIconSize),
            tint = contentColor
        )
        Spacer(Modifier.width(ButtonMediumIconLabelSpace))
        // textStyleFor: titleMedium from the medium height up.
        Text(if (following) "Following" else "Follow", style = MaterialTheme.typography.titleMedium, color = contentColor)
    }
}

// ButtonMediumTokens
private val ButtonMediumContainerHeight: Dp = 56.dp
private val ButtonMediumIconLabelSpace: Dp = 8.dp
private val ButtonMediumIconSize: Dp = 24.dp
private val ButtonMediumLeadingSpace: Dp = 24.dp
private val ButtonMediumOutlineWidth: Dp = 1.dp

// Material Symbols Rounded "person_add".
// https://github.com/google/material-design-icons/blob/master/symbols/android/person_add/materialsymbolsrounded/person_add_24px.xml
private val PersonAddRounded: ImageVector by lazy {
    symbol(
        "PersonAdd",
        "M720,440L640,440Q623,440 611.5,428.5Q600,417 600,400Q600,383 611.5,371.5Q623,360 640,360L720,360L720,280" +
            "Q720,263 731.5,251.5Q743,240 760,240Q777,240 788.5,251.5Q800,263 800,280L800,360L880,360Q897,360 908.5,371.5" +
            "Q920,383 920,400Q920,417 908.5,428.5Q897,440 880,440L800,440L800,520Q800,537 788.5,548.5Q777,560 760,560" +
            "Q743,560 731.5,548.5Q720,537 720,520L720,440ZM360,480Q294,480 247,433Q200,386 200,320Q200,254 247,207" +
            "Q294,160 360,160Q426,160 473,207Q520,254 520,320Q520,386 473,433Q426,480 360,480ZM40,720L40,688" +
            "Q40,654 57.5,625.5Q75,597 104,582Q166,551 230,535.5Q294,520 360,520Q426,520 490,535.5Q554,551 616,582" +
            "Q645,597 662.5,625.5Q680,654 680,688L680,720Q680,753 656.5,776.5Q633,800 600,800L120,800Q87,800 63.5,776.5" +
            "Q40,753 40,720ZM120,720L600,720L600,688Q600,677 594.5,668Q589,659 580,654Q526,627 471,613.5Q416,600 360,600" +
            "Q304,600 249,613.5Q194,627 140,654Q131,659 125.5,668Q120,677 120,688L120,720ZM360,400Q393,400 416.5,376.5" +
            "Q440,353 440,320Q440,287 416.5,263.5Q393,240 360,240Q327,240 303.5,263.5Q280,287 280,320Q280,353 303.5,376.5" +
            "Q327,400 360,400Z"
    )
}

// Material Symbols Rounded "check".
// https://github.com/google/material-design-icons/blob/master/symbols/android/check/materialsymbolsrounded/check_24px.xml
private val CheckRounded: ImageVector by lazy {
    symbol(
        "Check",
        "M382,606L721,267Q733,255 749,255Q765,255 777,267Q789,279 789,295.5Q789,312 777,324L410,692Q398,704 382,704" +
            "Q366,704 354,692L182,520Q170,508 170.5,491.5Q171,475 183,463Q195,451 211.5,451Q228,451 240,463L382,606Z"
    )
}

// https://github.com/bluesky-social/social-app/blob/main/src/screens/Profile/Header/Metrics.tsx#ProfileHeaderMetrics
// followers, following, posts on one line, 10 pt apart (12 dp here), a flow so 200 % wraps.
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileHeaderMetrics(content: HeaderContent) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        MetricsItem(content.followers, "followers")
        MetricsItem(content.following, "following")
        MetricsItem(content.posts, "posts")
    }
}

// https://github.com/bluesky-social/social-app/blob/main/src/screens/Profile/Header/Metrics.tsx#ProfileHeaderMetrics
// the count semi bold, its word in the contrast medium colour.
@Composable
private fun MetricsItem(primaryText: String, secondaryText: String) {
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("$primaryText ") }
            withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) { append(secondaryText) }
        },
        style = MaterialTheme.typography.bodyMedium
    )
}

// layout.ts PROFILE_BANNER_HEIGHT, Shell.tsx GrowableAvatar (top 104, left 10, width 94, size 90)
private val ProfileBannerHeight: Dp = 150.dp
private val AvatarSize: Dp = 90.dp
private val AvatarRing: Dp = 2.dp
private val AvatarTop: Dp = 104.dp
private val AvatarLeft: Dp = 10.dp

private fun symbol(name: String, pathData: String, autoMirror: Boolean = false): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, viewportWidth = 960f, viewportHeight = 960f, autoMirror = autoMirror)
        .addPath(pathData = PathParser().parsePathString(pathData).toNodes(), fill = SolidColor(Color.White))
        .build()

// Material Symbols Rounded "location_on".
// https://github.com/google/material-design-icons/blob/master/symbols/android/location_on/materialsymbolsrounded/location_on_24px.xml
private val LocationOnRounded: ImageVector by lazy {
    symbol(
        "LocationOn",
        "M480,774Q602,662 661,570.5Q720,479 720,408Q720,299 650.5,229.5Q581,160 480,160Q379,160 309.5,229.5Q240,299 240,408" +
            "Q240,479 299,570.5Q358,662 480,774ZM480,853Q466,853 452,848Q438,843 427,833Q362,773 312,716Q262,659 228.5,605.5" +
            "Q195,552 177.5,502.5Q160,453 160,408Q160,258 256.5,169Q353,80 480,80Q607,80 703.5,169Q800,258 800,408" +
            "Q800,453 782.5,502.5Q765,552 731.5,605.5Q698,659 648,716Q598,773 533,833Q522,843 508,848Q494,853 480,853Z" +
            "M480,480Q513,480 536.5,456.5Q560,433 560,400Q560,367 536.5,343.5Q513,320 480,320Q447,320 423.5,343.5" +
            "Q400,367 400,400Q400,433 423.5,456.5Q447,480 480,480Z"
    )
}

// Material Symbols Rounded "calendar_month".
// https://github.com/google/material-design-icons/blob/master/symbols/android/calendar_month/materialsymbolsrounded/calendar_month_24px.xml
private val CalendarMonthRounded: ImageVector by lazy {
    symbol(
        "CalendarMonth",
        "M200,880Q167,880 143.5,856.5Q120,833 120,800L120,240Q120,207 143.5,183.5Q167,160 200,160L240,160L240,120" +
            "Q240,103 251.5,91.5Q263,80 280,80Q297,80 308.5,91.5Q320,103 320,120L320,160L640,160L640,120Q640,103 651.5,91.5" +
            "Q663,80 680,80Q697,80 708.5,91.5Q720,103 720,120L720,160L760,160Q793,160 816.5,183.5Q840,207 840,240L840,800" +
            "Q840,833 816.5,856.5Q793,880 760,880L200,880ZM200,800L760,800L760,400L200,400L200,800ZM200,320L760,320L760,240" +
            "L200,240L200,320ZM480,560Q463,560 451.5,548.5Q440,537 440,520Q440,503 451.5,491.5Q463,480 480,480" +
            "Q497,480 508.5,491.5Q520,503 520,520Q520,537 508.5,548.5Q497,560 480,560ZM320,560Q303,560 291.5,548.5" +
            "Q280,537 280,520Q280,503 291.5,491.5Q303,480 320,480Q337,480 348.5,491.5Q360,503 360,520Q360,537 348.5,548.5" +
            "Q337,560 320,560ZM640,560Q623,560 611.5,548.5Q600,537 600,520Q600,503 611.5,491.5Q623,480 640,480" +
            "Q657,480 668.5,491.5Q680,503 680,520Q680,537 668.5,548.5Q657,560 640,560ZM480,720Q463,720 451.5,708.5" +
            "Q440,697 440,680Q440,663 451.5,651.5Q463,640 480,640Q497,640 508.5,651.5Q520,663 520,680Q520,697 508.5,708.5" +
            "Q497,720 480,720ZM320,720Q303,720 291.5,708.5Q280,697 280,680Q280,663 291.5,651.5Q303,640 320,640" +
            "Q337,640 348.5,651.5Q360,663 360,680Q360,697 348.5,708.5Q337,720 320,720ZM640,720Q623,720 611.5,708.5" +
            "Q600,697 600,680Q600,663 611.5,651.5Q623,640 640,640Q657,640 668.5,651.5Q680,663 680,680Q680,697 668.5,708.5" +
            "Q657,720 640,720Z"
    )
}

// Material Symbols Rounded "link".
// https://github.com/google/material-design-icons/blob/master/symbols/android/link/materialsymbolsrounded/link_24px.xml
private val LinkRounded: ImageVector by lazy {
    symbol(
        "Link",
        "M280,680Q197,680 138.5,621.5Q80,563 80,480Q80,397 138.5,338.5Q197,280 280,280L400,280Q417,280 428.5,291.5" +
            "Q440,303 440,320Q440,337 428.5,348.5Q417,360 400,360L280,360Q230,360 195,395Q160,430 160,480Q160,530 195,565" +
            "Q230,600 280,600L400,600Q417,600 428.5,611.5Q440,623 440,640Q440,657 428.5,668.5Q417,680 400,680L280,680Z" +
            "M360,520Q343,520 331.5,508.5Q320,497 320,480Q320,463 331.5,451.5Q343,440 360,440L600,440Q617,440 628.5,451.5" +
            "Q640,463 640,480Q640,497 628.5,508.5Q617,520 600,520L360,520ZM560,680Q543,680 531.5,668.5Q520,657 520,640" +
            "Q520,623 531.5,611.5Q543,600 560,600L680,600Q730,600 765,565Q800,530 800,480Q800,430 765,395Q730,360 680,360" +
            "L560,360Q543,360 531.5,348.5Q520,337 520,320Q520,303 531.5,291.5Q543,280 560,280L680,280Q763,280 821.5,338.5" +
            "Q880,397 880,480Q880,563 821.5,621.5Q763,680 680,680L560,680Z"
    )
}
