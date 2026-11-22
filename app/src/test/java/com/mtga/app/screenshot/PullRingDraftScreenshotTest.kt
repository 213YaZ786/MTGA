package com.mtga.app.screenshot

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mtga.app.core.model.Post
import com.mtga.app.core.model.PostStats
import com.mtga.app.di.appModule
import com.mtga.app.navigation.LocalReadableInset
import com.mtga.app.ui.component.FeatherArt
import com.mtga.app.ui.component.FeatherInk
import com.mtga.app.ui.component.FeedBanner
import com.mtga.app.ui.component.MenuPill
import com.mtga.app.ui.component.PostCard
import com.mtga.app.ui.component.drawFeather
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
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * DRAFT for the user's yes (requests.md point 25), nothing of app/src/main changes: the pull to refresh shows its load
 * as a ring of 16 feathers at the centre of the screen over the blurred feed; the feathers fill clockwise from 12 o'clock
 * as the load goes, the count in the middle. The ring is Material's determinate indicator with the arc cut into feather
 * slots (start at 270 degrees, sweep = progress x 360, the track for the rest, coercedProgress, ProgressBarRangeInfo):
 * https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/ProgressIndicator.kt#CircularProgressIndicator
 * The feather and its colours are LoadingMark's (FeatherArt, FeatherInk and drawFeather, internal, same module):
 * https://github.com/213YaZ786/MTGA/blob/main/app/src/main/java/com/mtga/app/ui/component/LoadingMark.kt#LoadingMark
 * Neko's PullRefresh blurs the content itself while it refreshes (16 dp):
 * https://github.com/nekomangaorg/Neko/blob/main/app/src/main/java/org/nekomanga/presentation/components/PullRefresh.kt#PullRefresh
 * RenderEffect does not draw on the JVM, so the blur is done by hand as PullCentreDraftScreenshotTest does: the feed is
 * captured, blurred by FastBlur's stack blur, and drawn back over itself.
 * not ported: CircularProgressIndicator's indeterminate overload, drawCircularIndicator and drawDeterminateCircularIndicator
 * (arc strokes: feathers take their place), strokeCap and gapSize (no stroke); Neko's PullRefresh pull state, nestedScroll
 * and release animation, the black dim where blur is unavailable, WavyLinearIndicator; FastBlur's blur(Bitmap, Rect) overload.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = android.app.Application::class, sdk = [36])
class PullRingDraftScreenshotTest : ScreenshotBase() {

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

    // Fixed instant (2026-11-21 12:00 UTC).
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

    // A quarter of the load: 10 of 40.
    @Test
    fun early() = capture("pull_ring_early_draft", interact = { blurFeed(Size.medium) }) {
        Screen(progress = 0.25f, count = 10, total = 40)
    }

    // Three quarters: 30 of 40.
    @Test
    fun late() = capture("pull_ring_draft", interact = { blurFeed(Size.medium) }) {
        Screen(progress = 0.75f, count = 30, total = 40)
    }

    // Edge: at 200 % text and on a 360 dp phone.
    @Test
    fun edge() = captureEdge("pull_ring_draft", interact = { blurFeed(Size.medium) }) {
        Screen(progress = 0.75f, count = 30, total = 40)
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

    /**
     * The screen of PullRefresh while refreshing: the content blurred, and over it the ring at the centre.
     * https://github.com/nekomangaorg/Neko/blob/main/app/src/main/java/org/nekomanga/presentation/components/PullRefresh.kt#PullRefresh
     */
    @Composable
    private fun Screen(progress: Float, count: Int, total: Int) {
        val mine = remember { Blurred().also { blurred = it } }
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().testTag("feed")) { Feed() }
            mine.image?.let { picture ->
                Image(picture, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
            }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                FeatherRing(progress = { progress }) {
                    Text("$count of $total", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
                    Text("accounts", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                }
            }
        }
    }

    /**
     * CircularProgressIndicator's determinate form: 168 dp, start at 12 o'clock (270 degrees), sweep = progress x 360, the
     * slots inside the sweep in full colour, the others as the track (outlineVariant at low alpha, the indicator's trackColor);
     * each feather laid radially, quill to the centre, tip outwards, drawn by LoadingMark's drawFeather.
     * https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/ProgressIndicator.kt#CircularProgressIndicator
     */
    @Composable
    private fun FeatherRing(progress: () -> Float, centre: @Composable ColumnScope.() -> Unit) {
        val coercedProgress = { progress().coerceIn(0f, 1f) }
        val accent = MaterialTheme.colorScheme.primary
        // LoadingMark's ink.
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
                // Start at 12 o'clock
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
                    // The art's tip points 45 degrees clockwise from up: turned to the slot's direction.
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

    private val SLOTS = 16

    // The feed of pull_reveal_draft, not moved.
    @Composable
    private fun Feed() {
        val look = LocalGlass.current
        val backdrop = rememberGlassBackdrop()
        CompositionLocalProvider(LocalGlassBackdrop provides backdrop.takeIf { look != null }) {
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
