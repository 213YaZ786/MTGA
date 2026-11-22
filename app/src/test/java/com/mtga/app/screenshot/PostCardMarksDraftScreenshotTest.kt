package com.mtga.app.screenshot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ShapeDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mtga.app.core.model.Post
import com.mtga.app.core.model.VerifiedType
import com.mtga.app.di.appModule
import com.mtga.app.navigation.LocalReadableInset
import com.mtga.app.ui.component.Avatar
import com.mtga.app.ui.component.PostCard
import com.mtga.app.ui.component.ZoneSurface
import com.mtga.app.ui.component.getShortTime
import com.mtga.app.ui.theme.LocalDisplayPrefs
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
 * DRAFT for the user's yes (requests.md points 11, 12 and 13), nothing of app/src/main changes: three posts as the
 * cards are today (post_marks_now, MTGA's own PostCard) and with the planned marks (post_marks_draft).
 * - Point 11, verified: Nitter's icon after the name, a filled circle with a check in it, in the theme's colours
 *   (blue is primary, business tertiary, government onSurfaceVariant: Nitter's three types differ by colour only);
 *   protected: Nitter's lock after the name.
 * - Point 12: #Artemis and $TSLA coloured as the @mentions are by linkify. The draft only colours them, a tap will open
 *   the app's Search with the tag.
 * - Point 13: ", edited" after the time, in the time's own style, where Nitter writes "Last edited".
 * Nitter's marks:
 * https://github.com/zedeus/nitter/blob/master/src/views/renderutils.nim#verifiedIcon (circle and check, after the name)
 * https://github.com/zedeus/nitter/blob/master/src/views/renderutils.nim#linkUser (the lock after the name)
 * https://github.com/zedeus/nitter/blob/master/src/views/tweet.nim#renderTweet ("Last edited" next to the time)
 * The card's header is MTGA's own, NameRow is private to PostCard.kt, so a faithful copy of it with the additions:
 * https://github.com/213YaZ786/MTGA/blob/main/app/src/main/java/com/mtga/app/ui/component/PostCard.kt#NameRow
 * and the tags are coloured as
 * https://github.com/213YaZ786/MTGA/blob/main/app/src/main/java/com/mtga/app/feature/post/PostText.kt#linkify
 * Icon paths: Material Symbols Rounded.
 * not ported: renderutils.nim's icon helper and its CSS (verified-icon-circle, verified-icon-check sizes and the page
 * colours), the title text "Verified blue account" (the draft gives the icons a content description instead),
 * renderTweet's history link and renderLatestPost (the "Last edited" link to /history and the pointer to the latest
 * version), the community note, disclosures, poll, card, quote and every other block of renderTweet
 * not ported: PostCard's ContextLine, media, poll, link card, quote, note and stats blocks (the sample posts carry none),
 * the tap and long press, the held copy, the shared avatar key and the compact display style (the default style is
 * drawn), NewDot (no post is unread here)
 * not ported: linkify's mention and address matching (the sample texts have none, only the tags are drawn), its click
 * handler (the draft only colours)
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = android.app.Application::class, sdk = [36])
class PostCardMarksDraftScreenshotTest : ScreenshotBase() {

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
    private val now = FIXED_NOW

    // No stats on the samples: the draft's copy of the card has no stats row, and the two pictures must differ only by
    // the marks.
    private val verifiedPost = Post(
        id = "1850000000000000001",
        authorHandle = "NASA",
        authorName = "NASA",
        text = "Artemis crews trained in the lab today. Follow #Artemis and the mission partners, and watch " +
            "\$TSLA and the rest of the market react to the news.",
        publishedAtMillis = now - 2 * 60 * 60 * 1000,
        permalink = "https://x.com/NASA/status/1850000000000000001"
    )

    private val protectedPost = Post(
        id = "1850000000000000002",
        authorHandle = "marie_k",
        authorName = "Marie K",
        text = "Only my followers see this one. Back from the mountains, the weather held all week.",
        publishedAtMillis = now - 5 * 60 * 60 * 1000,
        permalink = "https://x.com/marie_k/status/1850000000000000002"
    )

    private val editedPost = Post(
        id = "1850000000000000003",
        authorHandle = "github",
        authorName = "GitHub",
        text = "A good week for the people building in the open. Thank you to every maintainer.",
        publishedAtMillis = now - 26 * 60 * 60 * 1000,
        permalink = "https://x.com/github/status/1850000000000000003"
    )

    // Today's cards, MTGA's own PostCard.
    @Test
    fun now() = capture("post_marks_now") {
        // The same marks as the draft, now carried by Post itself (points 11 and 13).
        Screen {
            PostCard(post = verifiedPost.copy(verified = VerifiedType.BLUE), onClick = {}, onOpenLink = {}, onDownload = {}, showStats = false)
            PostCard(post = protectedPost.copy(isProtected = true), onClick = {}, onOpenLink = {}, onDownload = {}, showStats = false)
            PostCard(post = editedPost.copy(edited = true), onClick = {}, onOpenLink = {}, onDownload = {}, showStats = false)
        }
    }

    // The same three with the marks: a verified blue check, a lock, ", edited", and the tags in the link colour.
    @Test
    fun draft() = capture("post_marks_draft") {
        Screen {
            MarkedCard(verifiedPost, Marks(verified = Verified.Blue))
            MarkedCard(protectedPost, Marks(isProtected = true))
            MarkedCard(editedPost, Marks(edited = true))
        }
    }

    // The screen under the cards: the list's side padding, a gap between two cards.
    @Composable
    private fun Screen(cards: @Composable () -> Unit) {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = LocalReadableInset.current),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) { cards() }
    }
}

// Nitter's verified types (VerifiedType: blue, business, government), which differ only by colour.
private enum class Verified { Blue, Business, Government }

// What the planned card knows about a post besides the Post model, which has no such field yet.
private data class Marks(
    val verified: Verified? = null,
    val isProtected: Boolean = false,
    val edited: Boolean = false
)

// PostCard's zone with its header and text, as PostCard draws them (no tap, no stats), with the marks added.
@Composable
private fun MarkedCard(post: Post, marks: Marks) {
    ZoneSurface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp),
        shape = ShapeDefaults.ExtraLarge
    ) {
        Column(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Avatar(post.avatarUrl, post.authorName, size = 44.dp)
                MarkedNameRow(post, marks, Modifier.weight(1f))
            }
            Text(tagged(post.text, MaterialTheme.colorScheme.primary), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

// A faithful copy of PostCard's NameRow (private there). After the name come Nitter's marks, the verified icon
// (renderutils.nim's verifiedIcon, tweet.nim line 52) and the lock (linkUser), then the line under it gets ", edited"
// after the time, as Nitter writes "Last edited" next to it.
@Composable
private fun MarkedNameRow(post: Post, marks: Marks, modifier: Modifier = Modifier) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                post.authorName.ifBlank { "@${post.authorHandle}" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            marks.verified?.let { VerifiedIcon(it, Modifier.padding(start = 4.dp)) }
            if (marks.isProtected) {
                Icon(
                    LockRounded,
                    contentDescription = "Protected account",
                    modifier = Modifier.padding(start = 4.dp).size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        val time = getShortTime(post.publishedAtMillis).let { if (marks.edited) "$it, edited" else it }
        Text(
            if (post.authorName.isBlank()) time else "@${post.authorHandle}, $time",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// Nitter's verifiedIcon: a circle (icon "circle") with the check (icon "ok") over it, 16 dp. The circle takes the type's
// colour and the check the ground colour that shows through it in Nitter.
@Composable
private fun VerifiedIcon(type: Verified, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val (circle, check) = when (type) {
        Verified.Blue -> scheme.primary to scheme.onPrimary
        Verified.Business -> scheme.tertiary to scheme.onTertiary
        Verified.Government -> scheme.onSurfaceVariant to scheme.surface
    }
    val label = "Verified ${type.name.lowercase()} account"
    Box(modifier.size(16.dp).background(circle, CircleShape), contentAlignment = Alignment.Center) {
        Icon(CheckRounded, contentDescription = label, modifier = Modifier.size(12.dp), tint = check)
    }
}

// The tags drawn as linkify draws a link: the link colour on the token, a clickable annotation that does nothing here.
private val TAG = Regex("""[#$][\p{L}_][\p{L}\p{N}_]*""")

private fun tagged(text: String, linkColor: Color): AnnotatedString {
    val style = TextLinkStyles(style = SpanStyle(color = linkColor))
    return buildAnnotatedString {
        var cursor = 0
        for (match in TAG.findAll(text)) {
            append(text.substring(cursor, match.range.first))
            withLink(LinkAnnotation.Clickable("tag:${match.value}", style) {}) { append(match.value) }
            cursor = match.range.last + 1
        }
        append(text.substring(cursor))
    }
}

// Material Symbols Rounded "lock", fill 1.
// https://github.com/google/material-design-icons/blob/master/symbols/android/lock/materialsymbolsrounded/lock_fill1_24px.xml
private val LockRounded: ImageVector by lazy {
    val pathData = "M240,880Q207,880 183.5,856.5Q160,833 160,800L160,400Q160,367 183.5,343.5Q207,320 240,320L280,320" +
        "L280,240Q280,157 338.5,98.5Q397,40 480,40Q563,40 621.5,98.5Q680,157 680,240L680,320L720,320" +
        "Q753,320 776.5,343.5Q800,367 800,400L800,800Q800,833 776.5,856.5Q753,880 720,880L240,880Z" +
        "M480,680Q513,680 536.5,656.5Q560,633 560,600Q560,567 536.5,543.5Q513,520 480,520Q447,520 423.5,543.5" +
        "Q400,567 400,600Q400,633 423.5,656.5Q447,680 480,680Z" +
        "M360,320L600,320L600,240Q600,190 565,155Q530,120 480,120Q430,120 395,155Q360,190 360,240L360,320Z"
    ImageVector.Builder("Lock", 24.dp, 24.dp, viewportWidth = 960f, viewportHeight = 960f)
        .addPath(pathData = PathParser().parsePathString(pathData).toNodes(), fill = SolidColor(Color.White))
        .build()
}

// Material Symbols Rounded "check".
// https://github.com/google/material-design-icons/blob/master/symbols/android/check/materialsymbolsrounded/check_24px.xml
private val CheckRounded: ImageVector by lazy {
    val pathData = "M382,606L721,267Q733,255 749,255Q765,255 777,267Q789,279 789,295.5Q789,312 777,324L410,692" +
        "Q398,704 382,704Q366,704 354,692L182,520Q170,508 170.5,491.5Q171,475 183,463Q195,451 211.5,451" +
        "Q228,451 240,463L382,606Z"
    ImageVector.Builder("Check", 24.dp, 24.dp, viewportWidth = 960f, viewportHeight = 960f)
        .addPath(pathData = PathParser().parsePathString(pathData).toNodes(), fill = SolidColor(Color.White))
        .build()
}
