package com.mtga.app.screenshot

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect as AndroidRect
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import com.mtga.app.ui.component.SparkButton
import com.mtga.app.ui.component.rememberSparkButtonState
import com.mtga.app.ui.glass.LocalGlass
import com.mtga.app.ui.glass.rememberGlassLook
import com.mtga.app.ui.theme.MtgaTheme
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * DRAFT of requests.md point 42 (the like from the long press: a glitch when the post returns to its place), nothing of
 * app/src/main changes. A filmstrip, frames every 40 ms from the close tap to 400 ms after, on a stopped test clock
 * (mainClock.autoAdvance = false, advanceTimeBy, captureToImage), a post card over a list, Like chosen. Top row,
 * "today": HoldMenu.kt:119-128 and PostActions.kt:74, :134 as they behave. Bottom row, "proposed": ONE progress drives
 * the panes and veil, the window's dim, the window's blur behind and the copy's return; the window's own exit animation
 * is off, so the original shows and the window goes in the same frame; then the star burst.
 *
 * Ports:
 * https://github.com/LineageOS/android_frameworks_base/blob/lineage-24.0/packages/SystemUI/src/com/android/systemui/globalactions/GlobalActionsDialogLite.java#startAnimation
 * (startAnimation(dialog, isEnter, then): one progress 0 to 1, STANDARD_ACCELERATE interpolator on the exit, alpha = 1 - progress
 * on the exit, window dimAmount = initialWindowDimAmount x alpha, then run [then], hide and dismiss in one go)
 * https://github.com/LineageOS/android_frameworks_base/blob/lineage-24.0/tests/graphics/SilkFX/src/com/android/test/silkfx/materials/BackgroundBlurActivity.kt#setBlurBehind
 * (setBlurBehind(radius) sets the window's blurBehindRadius, setDimAmount(amount) its dimAmount, each frame)
 * RenderEffect and window blurs do not draw on the JVM, so the blur behind is FastBlur's stack blur of the list picture,
 * at the radius each frame implies (as PullRingDraftScreenshotTest does), the dim a black scrim of its alpha.
 * not ported: startAnimation's enter branch (isEnter true, STANDARD, config_activityDefaultDur), the translation of the
 * layout and its rotation switch (the menu's own motion is the copy's spring here); setBlurBehind's TextView readout and
 * BackgroundBlurActivity's other functions (setBackgroundBlur, setBackgroundColorAlpha, setBlurDisabled, setBatterySavingModeOn,
 * onProgressChanged and its seek bars).
 *
 * Not driven as in the app: HoldMenu's Dialog and PostCard (the card is a stand-in, as in AnimationFilmstripDraftScreenshotTest),
 * a veil of 12 percent and the panes drawn by hand; the window's exit animation of today is drawn as the copy fading over
 * the original for 150 ms (at rest the copy and the card are the same picture, so it is hard to see here).
 */
@OptIn(ExperimentalRoborazziApi::class, ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = android.app.Application::class, sdk = [36])
class HoldMenuCloseDraftScreenshotTest {

    @get:org.junit.Rule
    val compose = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private val options = RoborazziOptions(
        compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0f)
    )

    private val times = (0..400 step 40).toList()

    private var pictures = 0

    private val cardHeight = 84.dp

    // STANDARD_ACCELERATE of SystemUI's Interpolators (PathInterpolator 0.3, 0, 1, 1).
    private val standardAccelerate = CubicBezierEasing(0.3f, 0f, 1f, 1f)

    // The window's own settings, ZoneDialog.kt BlurBehind: blur 16 dp by day, 8 by night, dim 0.23 by day, 0.56 by night.
    private fun blurBehindRadiusDp(night: Boolean) = if (night) 8 else 16
    private fun initialWindowDimAmount(night: Boolean) = if (night) 0.56f else 0.23f

    // One row: the list alone first (the card held, hidden), its picture taken, then the open menu, the close, a frame each instant.
    private fun row(night: Boolean, proposed: Boolean): List<Bitmap> {
        val go = mutableStateOf(false)
        val shot = mutableStateOf<Bitmap?>(null)
        val picture = pictures++
        compose.mainClock.autoAdvance = false
        compose.activity.setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                MtgaTheme(darkTheme = night, dynamicColor = false) {
                    val look = rememberGlassLook(MaterialTheme.colorScheme, true, quiet = true)
                    CompositionLocalProvider(
                        LocalGlass provides look,
                        LocalContentColor provides MaterialTheme.colorScheme.onBackground
                    ) {
                        androidx.compose.runtime.key(picture) {
                            Box(
                                Modifier
                                    .testTag("stage")
                                    .size(200.dp, 320.dp)
                                    .clipToBounds()
                                    .background(MaterialTheme.colorScheme.background)
                            ) { Stage(go.value, shot.value, proposed, night) }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1000)
        compose.waitForIdle()
        val behind = compose.onNodeWithTag("stage").captureToImage().asAndroidBitmap().copy(Bitmap.Config.ARGB_8888, false)
        compose.runOnIdle { shot.value = behind }
        compose.mainClock.advanceTimeBy(1000)
        compose.waitForIdle()
        compose.runOnIdle { go.value = true }
        val frames = mutableListOf<Bitmap>()
        var elapsed = 0
        times.forEach { t ->
            compose.mainClock.advanceTimeBy((t - elapsed).toLong())
            elapsed = t
            frames += compose.onNodeWithTag("stage").captureToImage().asAndroidBitmap().copy(Bitmap.Config.ARGB_8888, false)
        }
        compose.mainClock.autoAdvance = true
        return frames
    }

    /**
     * The close, as GlobalActionsDialogLite's startAnimation does it on the exit: one progress, alpha = 1 - progress,
     * dim = initialWindowDimAmount x alpha, blur behind = blurBehindRadius x alpha (BackgroundBlurActivity's setBlurBehind),
     * then hide and dismiss in one go. Today: the fade is the veil's own 200 ms, blur and dim stay full until onDismiss.
     * https://github.com/LineageOS/android_frameworks_base/blob/lineage-24.0/packages/SystemUI/src/com/android/systemui/globalactions/GlobalActionsDialogLite.java#startAnimation
     */
    @Composable
    private fun Stage(go: Boolean, shot: Bitmap?, proposed: Boolean, night: Boolean) {
        val density = LocalDensity.current.density
        val rise = remember { Animatable(1f) }
        val fade = remember { Animatable(1f) }
        val progress = remember { Animatable(0f) }
        val windowExit = remember { Animatable(0f) }
        var menuOpen by remember { mutableStateOf(true) }
        var burst by remember { mutableStateOf(false) }
        val spark = rememberSparkButtonState()
        LaunchedEffect(go) {
            if (!go) return@LaunchedEffect
            val then = {
                // Hide then dismiss, in one frame: the original shows, the window is gone.
                menuOpen = false
            }
            coroutineScope {
                if (proposed) {
                    launch { progress.animateTo(1f, tween(250, easing = standardAccelerate)) }
                } else {
                    launch { fade.animateTo(0f, tween(200)) }
                }
                rise.animateTo(0f, spring(dampingRatio = 1f, stiffness = 500f))
            }
            then()
            if (!proposed) {
                launch {
                    windowExit.snapTo(1f)
                    windowExit.animateTo(0f, tween(150))
                }
            }
            burst = true
            spark.animate()
        }
        val alpha = if (proposed) 1f - progress.value else fade.value
        // Window settings of the frame: proposed follows alpha, today stays full until the window is gone.
        val windowAlpha = if (proposed) alpha else if (menuOpen) 1f else 0f
        val blurBehindRadius = blurBehindRadiusDp(night) * density * windowAlpha
        val dimAmount = initialWindowDimAmount(night) * windowAlpha
        val radiusPx = (blurBehindRadius * sqrt(6f)).roundToInt().coerceAtMost(254)
        val behind = remember(shot, radiusPx) {
            if (shot == null) null
            else if (radiusPx < 1) shot.asImageBitmap()
            else blur(shot, radiusPx, false)!!.asImageBitmap()
        }
        val holdAlpha = if (menuOpen) 0f else 1f
        Box(Modifier.fillMaxSize()) {
            // The list, the held card hidden while the menu is open (PostActions.kt:74).
            Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Card(Modifier.fillMaxWidth().height(cardHeight))
                Card(Modifier.fillMaxWidth().height(cardHeight).graphicsLayer { this.alpha = holdAlpha })
                Card(Modifier.fillMaxWidth().height(cardHeight))
                Card(Modifier.fillMaxWidth().height(cardHeight))
            }
            if (shot != null && menuOpen) {
                // The window: blur behind, dim, then the veil and the panes.
                if (behind != null) {
                    Image(behind, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
                }
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = dimAmount)))
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.12f * alpha)))
                Column(
                    Modifier.align(Alignment.BottomCenter).padding(16.dp).fillMaxWidth().graphicsLayer { this.alpha = alpha }
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(20.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Like"); Text("Archive"); Text("Share")
                }
            }
            if (shot != null) {
                // The lifted copy, at the held card's place (same column), and the star's burst on the card once it is back.
                val copyAlpha = if (menuOpen) 1f else windowExit.value
                Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Spacer(Modifier.fillMaxWidth().height(cardHeight))
                    Box(Modifier.fillMaxWidth().height(cardHeight), contentAlignment = Alignment.Center) {
                        if (menuOpen || windowExit.value > 0f) {
                            Card(
                                Modifier.fillMaxWidth().height(cardHeight).graphicsLayer {
                                    val s = 1f + 0.05f * rise.value
                                    scaleX = s
                                    scaleY = s
                                    translationY = -4.dp.toPx() * rise.value
                                    this.alpha = copyAlpha
                                }
                            )
                        }
                        if (burst) {
                            SparkButton(
                                onClick = {},
                                modifier = Modifier.align(Alignment.Center),
                                animateOnClick = false,
                                enabled = true,
                                state = spark,
                                primaryColor = MaterialTheme.colorScheme.tertiary,
                                secondaryColor = MaterialTheme.colorScheme.primary
                            ) {
                                // PostActions' private star (its path), drawn here.
                                val tertiary = MaterialTheme.colorScheme.tertiary
                                Box(
                                    Modifier.size(56.dp).drawBehind {
                                        val path = androidx.compose.ui.graphics.vector.PathParser()
                                            .parsePathString("M12,17.27L18.18,21l-1.64,-7.03L22,9.24l-7.19,-0.61L12,2 9.19,8.63 2,9.24l5.46,4.73L5.82,21z")
                                            .toPath()
                                        val k = size.width / 24f
                                        drawContext.canvas.save()
                                        drawContext.canvas.scale(k, k)
                                        drawPath(path, tertiary)
                                        drawContext.canvas.restore()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // A post card, as AnimationFilmstripDraftScreenshotTest's CardMini.
    @Composable
    private fun Card(modifier: Modifier = Modifier) {
        Column(
            modifier
                .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(16.dp))
                .padding(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(32.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                Spacer(Modifier.width(8.dp))
                Column {
                    Text("Amina Rahal", style = MaterialTheme.typography.titleSmall, maxLines = 1)
                    Text("@amina.rahal", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
        }
    }

    // The two rows joined, a label per row, the ms under each frame.
    private fun sheet(night: Boolean, rows: List<Pair<String, List<Bitmap>>>) {
        val scale = 0.5f
        val w = (rows[0].second[0].width * scale).toInt()
        val h = (rows[0].second[0].height * scale).toInt()
        val labelW = 230
        val ms = 40
        val gap = 12
        val sheet = Bitmap.createBitmap(labelW + (w + gap) * times.size + gap, (h + ms) * rows.size + gap, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(sheet)
        canvas.drawColor(if (night) 0xFF000000.toInt() else 0xFFFFFFFF.toInt())
        val ink = if (night) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ink; textSize = 26f }
        val smooth = Paint(Paint.FILTER_BITMAP_FLAG)
        rows.forEachIndexed { r, (label, frames) ->
            val top = r * (h + ms) + gap
            canvas.drawText(label, 10f, top + h / 2f, text)
            frames.forEachIndexed { i, frame ->
                val left = labelW + gap + i * (w + gap)
                canvas.drawBitmap(frame, null, AndroidRect(left, top, left + w, top + h), smooth)
                canvas.drawText("${times[i]} ms", left + 4f, top + h + 28f, text)
            }
        }
        sheet.captureRoboImage(
            "src/test/screenshots/anim_holdmenu_like_close_draft_${if (night) "dark" else "light"}.png",
            roborazziOptions = options
        )
    }

    @Test
    fun likeClose() {
        RuntimeEnvironment.setQualifiers("w411dp-h914dp-420dpi")
        listOf(false, true).forEach { night ->
            val today = row(night, proposed = false)
            val proposed = row(night, proposed = true)
            sheet(night, listOf("today" to today, "proposed" to proposed))
        }
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
