package com.mtga.app.screenshot

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mtga.app.core.model.Post
import com.mtga.app.di.appModule
import com.mtga.app.navigation.LocalReadableInset
import com.mtga.app.ui.component.Avatar
import com.mtga.app.ui.component.FeatherRing
import com.mtga.app.ui.component.PostCard
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * DRAFT for the user's yes (requests.md point 38), nothing of app/src/main changes: the pull to refresh of a profile.
 * profile_pull_count_draft_today: as FeedScreen.kt:187 draws it, the ring turning with an empty centre (no count: one
 * account); profile_pull_count_draft: as Home's (TimelineScreen.kt:346), the same counted FeatherRing filling with
 * "2 of 4" and "sources" in its centre, the sources a profile reads in turn (x.com, FxTwitter, Nitter, archives).
 * Both over the profile header and its posts, blurred as PullRevealBox blurs (16 dp).
 * The ring is the app's FeatherRing, Material's CircularProgressIndicator in its determinate and indeterminate forms:
 * https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/ProgressIndicator.kt#CircularProgressIndicator
 * The blur is done by hand as PullRingDraftScreenshotTest does (RenderEffect does not draw on the JVM): the feed is
 * captured, blurred by FastBlur's stack blur, and drawn back over itself.
 * The profile header is a stand-in (a banner brush, the avatar, name, handle, bio): FeedScreen's own header is not
 * redrawn here, only what lies under the ring.
 * not ported: CircularProgressIndicator's own overloads and strokes (FeatherRing is their port, used as it is),
 * strokeCap and gapSize; FastBlur's blur(Bitmap, Rect) overload; the pull state, the zoom and the release animation
 * (PullRing's graphicsLayer: the ring is drawn at rest, full size)
 * Robolectric holds the clock still: the indeterminate ring of "today" is drawn at its first frame (its arc at 10 %),
 * it cannot show the turning.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = android.app.Application::class, sdk = [36])
class ProfilePullCountDraftScreenshotTest : ScreenshotBase() {

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
            id = "1850000000000000011", authorHandle = "clashreport", authorName = "Clash Report",
            text = "Talks continue through the night, officials say a statement is expected in the morning.",
            publishedAtMillis = FIXED_NOW - 40 * 60 * 1000,
            permalink = "https://x.com/clashreport/status/1850000000000000011"
        ),
        Post(
            id = "1850000000000000012", authorHandle = "clashreport", authorName = "Clash Report",
            text = "Aid convoys crossed the border at dawn, the first in three weeks.",
            publishedAtMillis = FIXED_NOW - 3 * 60 * 60 * 1000,
            permalink = "https://x.com/clashreport/status/1850000000000000012"
        )
    )

    // FeedScreen.kt:187 today: no count, the ring runs on its own, its centre empty.
    @Test
    fun today() = capture("profile_pull_count_draft_today", interact = { blurFeed(Size.medium) }) {
        Screen(counted = false)
    }

    // Proposed: Home's counted ring, 2 of the 4 sources read.
    @Test
    fun draft() = capture("profile_pull_count_draft", interact = { blurFeed(Size.medium) }) {
        Screen(counted = true)
    }

    // Edge: at 200 % text and on a 360 dp phone.
    @Test
    fun edge() = captureEdge("profile_pull_count_draft", interact = { blurFeed(Size.medium) }) {
        Screen(counted = true)
    }

    // The blurred feed of the picture being drawn: filled by [blurFeed] once the feed has settled.
    private class Blurred {
        var image: ImageBitmap? by mutableStateOf(null)
    }

    private var blurred: Blurred? = null

    // Neko's Modifier.blur(radius) by hand: a stack blur of radius r has the sigma r / sqrt(6), so r = sigma * sqrt(6) (pixels).
    private fun blurFeed(radius: Dp) {
        val shot = compose.onNodeWithTag("feed").captureToImage().asAndroidBitmap()
        val density = RuntimeEnvironment.getApplication().resources.displayMetrics.density
        val sigma = radius.value * density
        val radiusPx = (sigma * sqrt(6f)).roundToInt().coerceAtMost(254)
        blurred?.image = blur(shot, radiusPx, false)!!.asImageBitmap()
        compose.waitForIdle()
    }

    // The screen of PullRevealBox while refreshing: the profile blurred, the ring at the centre. [counted] is what
    // FeedScreen passes as its progress: nothing today, the sources read in turn proposed.
    @Composable
    private fun Screen(counted: Boolean) {
        val mine = remember { Blurred().also { blurred = it } }
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().testTag("feed")) { Profile() }
            mine.image?.let { picture ->
                Image(picture, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
            }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (counted) {
                    // TimelineScreen.kt:346's ring: the fill with its count, here 2 of 4.
                    FeatherRing(progress = { 0.5f }) {
                        Text("2 of 4", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
                        Text("sources", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    }
                } else {
                    FeatherRing()
                }
            }
        }
    }

    // A profile: the banner under the status bar, the avatar over its foot, the name, the handle, the bio, the posts.
    @Composable
    private fun Profile() {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth()) {
                Box(
                    Modifier.fillMaxWidth().height(150.dp).background(
                        Brush.verticalGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary))
                    )
                )
                Box(Modifier.padding(start = 10.dp + LocalReadableInset.current, top = 104.dp)) {
                    Avatar(url = null, name = "Clash Report", size = 90.dp)
                }
            }
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp + LocalReadableInset.current, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Box(Modifier.height(44.dp))
                Text("Clash Report", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("@clashreport", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "Breaking news, reports, and opinions from around the world.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
            Column(
                Modifier.padding(horizontal = 16.dp + LocalReadableInset.current),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                posts.forEach { PostCard(post = it, onClick = {}, onOpenLink = {}, onDownload = {}, showStats = false) }
            }
        }
    }

    /** Neko's Size.medium. https://github.com/nekomangaorg/Neko/blob/main/app/src/main/java/org/nekomanga/presentation/theme/Constants.kt#Size */
    private object Size {
        val medium = 16.dp
    }

    /**
     * Stack Blur v1.0 by Mario Klingemann (http://incubator.quasimondo.com), Android port by Yahel Bouaziz, as Wasabeef's
     * https://github.com/wasabeef/glide-transformations/blob/main/transformations/src/main/java/jp/wasabeef/glide/transformations/internal/FastBlur.java#blur
     * Apache-2.0.
     */
    private fun blur(sentBitmap: Bitmap, radius: Int, canReuseInBitmap: Boolean): Bitmap? {
        val bitmap = if (canReuseInBitmap) sentBitmap else sentBitmap.copy(Bitmap.Config.ARGB_8888, true)

        if (radius < 1) {
            return null
        }

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
                    rinsum += sir[0]
                    ginsum += sir[1]
                    binsum += sir[2]
                } else {
                    routsum += sir[0]
                    goutsum += sir[1]
                    boutsum += sir[2]
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
                    rinsum += sir[0]
                    ginsum += sir[1]
                    binsum += sir[2]
                } else {
                    routsum += sir[0]
                    goutsum += sir[1]
                    boutsum += sir[2]
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
}
