package com.mtga.app.screenshot

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mtga.app.core.common.AppError
import com.mtga.app.core.common.ChallengeKind
import com.mtga.app.core.common.present
import com.mtga.app.core.model.Post
import com.mtga.app.core.model.PostStats
import com.mtga.app.di.appModule
import com.mtga.app.navigation.LocalReadableInset
import com.mtga.app.ui.component.BannerAction
import com.mtga.app.ui.component.BoldButton
import com.mtga.app.ui.component.FeedBanner
import com.mtga.app.ui.component.MenuPill
import com.mtga.app.ui.component.PostCard
import com.mtga.app.ui.component.QuietButton
import com.mtga.app.ui.component.ZoneAlertDialog
import com.mtga.app.ui.component.ZoneSurface
import com.mtga.app.ui.component.failureMessage
import com.mtga.app.ui.glass.LocalGlass
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
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * DRAFT for the user's yes (requests.md point 4), nothing of app/src/main changes: Home after a tap on the red triangle.
 * alert_now is the dialog as MTGA has it (its FailureDialog, private to TimelineScreen.kt, so a faithful copy of it:
 * the failed accounts listed with their explanation), the two others are the planned one, as LinkedOut built it (its
 * points 13 and 14): ONE pane of the post card's glass centred both ways over Home blurred behind it, one message
 * naming where the trouble comes from, no profile listed. In the app it is the reader kit's FailureDialog
 * (ZoneAlertDialog: BasicAlertDialog, BlurBehind and rememberCrossWindowBlur, AOSP's window blur) with its pane drawn like
 * the post card. The dialog's width is Material's (280 to 560 dp), the padding 24 dp, the content centred:
 * https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/AlertDialog.kt#BasicAlertDialog
 * https://github.com/213YaZ786/MTGA/blob/main/app/src/main/java/com/mtga/app/feature/timeline/TimelineScreen.kt#FailureDialog
 *
 * Robolectric draws no window blur and no RenderEffect, so the draft does what the window does: the screen is captured
 * to a bitmap (Roborazzi's own capture, captureToImage), blurred by Mario Klingemann's stack blur, dimmed by
 * BlurBehind's dimAmount (0.23 by day, 0.56 by night), and the pane drawn over it. alert_now needs none of that: its
 * dialog is a real window, taken as Roborazzi's WindowCaptureTest does (ScreenshotBase's [window]).
 * https://github.com/wasabeef/glide-transformations/blob/main/transformations/src/main/java/jp/wasabeef/glide/transformations/internal/FastBlur.java#blur
 * https://github.com/takahirom/roborazzi/blob/main/sample-android/src/test/java/com/github/takahirom/roborazzi/sample/WindowCaptureTest.kt#WindowCaptureTest
 * https://github.com/213YaZ786/LinkedOut/blob/main/app/src/test/java/com/linkedout/app/screenshot/AlertDraftScreenshotTest.kt#AlertDraftScreenshotTest
 * not ported: WindowCaptureTest's composeBottomSheet, androidDialog and fragment tests (this app has none of them),
 * its createComposeRule and NexusOne qualifiers (ScreenshotBase's rule and sizes are used). FastBlur has the one
 * method, blur, ported whole.
 *
 * The message is the reader kit's failureMessage (ui/component/FailureDialog.kt), chosen by the blame shared by most
 * failed profiles (core/common/AppError.kt, Blame), site = "X". Pictures: HOST, here Blame.INSTANCE (alert_draft_host) and
 * DEVICE (alert_draft_offline). The header is the one of HomeTopDraftScreenshotTest:
 * https://github.com/bluesky-social/social-app/blob/main/src/view/com/home/HomeHeaderLayoutMobile.tsx#HomeHeaderLayoutMobile
 * not ported: LinkedOut's AlertAction copy (MTGA's BannerAction keeps a tint asked for in glass), the other blames'
 * pictures (NETWORK, UPSTREAM, APP: the same pane with another text), the silent downloads text
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = android.app.Application::class, sdk = [36])
class AlertDraftScreenshotTest : ScreenshotBase() {

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

    // Three of the twelve followed accounts failed. Blame.INSTANCE: the servers refused or timed out.
    private val hostErrors = listOf(
        AppError.RateLimited(host = "nitter.net", retryAfterSeconds = 60),
        AppError.ChallengeRequired(
            host = "xcancel.com",
            url = "https://xcancel.com/github",
            kind = ChallengeKind.JS_INTERSTITIAL,
            status = 200
        ),
        AppError.Timeout(host = "nitter.net", millis = 15_000)
    )

    // Blame.DEVICE: no network at all, no profile is to blame.
    private val offlineErrors = listOf(AppError.Offline, AppError.Offline, AppError.Offline)

    // The handles the failures belong to, for alert_now.
    private val failedHandles = listOf("NASA", "BBCWorld", "github")

    // The dialog as it is today, in its own window.
    @Test
    fun now() = capture("alert_now", window = true) {
        HomeBehind()
        NowDialog(failed = failedHandles.zip(hostErrors), onRetry = {}, onDismiss = {})
    }

    @Test
    fun host() = capture("alert_draft_host", interact = ::blurBackdrop) {
        val (title, text) = failureMessage(hostErrors, 12, "X")!!
        AlertOverScreen(title, text)
    }

    @Test
    fun offline() = capture("alert_draft_offline", interact = ::blurBackdrop) {
        val (title, text) = failureMessage(offlineErrors, 12, "X")!!
        AlertOverScreen(title, text)
    }

    // The blurred screen of the picture being drawn: filled by [blurBackdrop] once the screen has settled.
    private class Backdrop {
        var image: ImageBitmap? by mutableStateOf(null)
    }

    private var backdrop: Backdrop? = null

    // The window's blur, done by hand: capture what is drawn, blur it as wide as BlurBehind(radiusDp = 24) at this
    // density. blurBehindRadius is a Skia radius: SurfaceFlinger takes sigma = 0.57735 * radius + 0.5
    // (SkBlurMask::ConvertRadiusToSigma), and a stack blur of radius r has the sigma of its triangular kernel,
    // r / sqrt(6), so r = sigma * sqrt(6). The bitmap is not scaled down: the stack blur costs the same at any radius.
    private fun blurBackdrop() {
        val shot = compose.onNodeWithTag("screen").captureToImage().asAndroidBitmap()
        val density = RuntimeEnvironment.getApplication().resources.displayMetrics.density
        val radiusPx = 24 * density
        val sigma = 0.57735f * radiusPx + 0.5f
        val blurred = blur(shot, (sigma * sqrt(6f)).roundToInt(), false)!!
        backdrop?.image = blurred.asImageBitmap()
        compose.waitForIdle()
    }

    // The screen, then (once it is captured) its blur, its dim and the pane over it.
    @Composable
    private fun AlertOverScreen(title: String, text: String) {
        val mine = remember { Backdrop().also { backdrop = it } }
        val dark = LocalGlass.current?.dark ?: false
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().testTag("screen")) { Screen() }
            mine.image?.let { picture ->
                Image(picture, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
                // BlurBehind's dimAmount with the blur on.
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = if (dark) 0.56f else 0.23f)))
                Box(Modifier.fillMaxSize().padding(horizontal = DialogMargin), contentAlignment = Alignment.Center) {
                    AlertPane(title, text)
                }
            }
        }
    }

    // The post card's glass (ZoneSurface, its shape and fill), Material's dialog width and 24 dp padding, centred
    // content: the icon, the title (headlineSmall as ZoneAlertContent), the text (bodyMedium), the two buttons.
    @Composable
    private fun AlertPane(title: String, text: String) {
        ZoneSurface(
            shape = MaterialTheme.shapes.extraLarge,
            modifier = Modifier.widthIn(min = DialogMinWidth, max = DialogMaxWidth)
        ) {
            Column(
                Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    WarningRounded,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)) {
                    QuietButton(onClick = {}) { Text("Close") }
                    BoldButton(onClick = {}, filled = true) { Text("Try again") }
                }
            }
        }
    }

    // The header, then the posts (the first of them is the list's first item): HomeTopDraftScreenshotTest's banner.
    @Composable
    private fun Screen() {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            Column(Modifier.padding(horizontal = LocalReadableInset.current)) {
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
            Column(
                Modifier.padding(horizontal = 16.dp + LocalReadableInset.current),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PostCard(post = unread, onClick = {}, onOpenLink = {}, onDownload = {}, unread = true)
                PostCard(post = read, onClick = {}, onOpenLink = {}, onDownload = {})
            }
        }
    }

    // What lies behind alert_now: Home's title and two cards.
    @Composable
    private fun HomeBehind() {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text("Home", style = MaterialTheme.typography.titleLarge)
            }
            Column(
                Modifier.padding(horizontal = 16.dp + LocalReadableInset.current),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PostCard(post = unread, onClick = {}, onOpenLink = {}, onDownload = {}, unread = true)
                PostCard(post = read, onClick = {}, onOpenLink = {}, onDownload = {})
            }
        }
    }

    // A faithful copy of FailureDialog (TimelineScreen.kt, private): the accounts that failed, each with its explanation.
    @Composable
    private fun NowDialog(
        failed: List<Pair<String, AppError>>,
        onRetry: () -> Unit,
        onDismiss: () -> Unit
    ) {
        ZoneAlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(if (failed.size == 1) "1 account could not be updated" else "${failed.size} accounts could not be updated") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    failed.take(FAILURES_SHOWN).forEach { (handle, error) ->
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("@$handle", style = MaterialTheme.typography.titleSmall)
                            Text(
                                error.present().explanation,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (failed.size > FAILURES_SHOWN) {
                        Text(
                            "and ${failed.size - FAILURES_SHOWN} more",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = { QuietButton(onClick = onRetry) { Text("Try again") } },
            dismissButton = { QuietButton(onClick = onDismiss) { Text("Close") } }
        )
    }

    // Stack Blur v1.0 by Mario Klingemann (http://incubator.quasimondo.com), Android port by Yahel Bouaziz, as
    // Wasabeef's FastBlur.blur: a compromise between a Gaussian and a box blur, a stack of colours moved along each row
    // then each column. Apache-2.0.
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

    // TimelineScreen's FAILURES_SHOWN (private there).
    private val FAILURES_SHOWN = 5

    // Material's dialog: 280 to 560 dp wide (BasicAlertDialog's DialogMinWidth and DialogMaxWidth), 24 dp from the sides.
    private val DialogMargin = 24.dp
    private val DialogMinWidth = 280.dp
    private val DialogMaxWidth = 560.dp
}
