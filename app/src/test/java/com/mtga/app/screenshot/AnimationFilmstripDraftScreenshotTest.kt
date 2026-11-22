package com.mtga.app.screenshot

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect as AndroidRect
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import com.mtga.app.ui.component.NewDot
import com.mtga.app.ui.component.SparkButton
import com.mtga.app.ui.component.rememberSparkButtonState
import com.mtga.app.ui.glass.LocalGlass
import com.mtga.app.ui.glass.rememberGlassLook
import com.mtga.app.ui.icon.MtgaIcons
import com.mtga.app.ui.theme.MtgaTheme
import kotlinx.coroutines.delay
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * DRAFT of requests.md point 32 (every animation smooth): a filmstrip per case, motion shown in a still. Top row,
 * "today": the app's piece as it is; bottom row, "proposed": the same piece with the fix. Frames are taken with the
 * test clock stopped (mainClock.autoAdvance = false), advanced to each instant, then captured with captureToImage,
 * as the compose testing guide does for animations
 * https://developer.android.com/develop/ui/compose/testing/time-manipulation
 * and joined in one bitmap saved with Roborazzi's Bitmap capture.
 *
 * Ports (the sample of each mechanism the proposals use, names kept: visible, enter, exit, contentState, animatedOffset):
 * https://github.com/androidx/androidx/blob/androidx-main/compose/animation/animation/samples/src/main/java/androidx/compose/animation/samples/AnimatedVisibilitySamples.kt#AnimatedVisibilityWithBooleanVisibleParamNoReceiver
 * (fadeIn, fadeOut + scaleOut on a `visible` flag: like_end, newdot, video_bubble)
 * https://github.com/androidx/androidx/blob/androidx-main/compose/animation/animation/samples/src/main/java/androidx/compose/animation/samples/AnimatedContentSamples.kt#SimpleAnimatedContentSample
 * (AnimatedContent on a state: follow's label and icon)
 * https://github.com/androidx/androidx/blob/androidx-main/compose/animation/animation-core/samples/src/main/java/androidx/compose/animation/core/samples/AnimatableSamples.kt#AnimatableAnimateToGenericsType
 * (an Animatable animateTo with a spring: zoom and the held card's rise)
 * not ported: AnimatedVisibility's receiver variants and the Transition animateDp of the sample, the sample's
 * Foo/Bar/Baz contents and its pointerInput (the test drives the state instead).
 *
 * Not driven as in the app: HoldMenu's Dialog (its content drawn without it), PostCard's name row, FollowButton and
 * ZoomableImage (private or tied to gestures and Coil: redrawn here from the lines named in each case), the video
 * bubble (inside GlassVideo's whole screen). SparkButton and NewDot are the app's own composables.
 */
@OptIn(ExperimentalRoborazziApi::class, ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = android.app.Application::class, sdk = [36])
class AnimationFilmstripDraftScreenshotTest {

    @get:org.junit.Rule
    val compose = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private val standard = listOf(0, 50, 100, 150, 200, 300, 450)

    private val options = RoborazziOptions(
        compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0f)
    )

    private var pictures = 0

    // One row: the piece composed on a fixed stage, the change triggered ([go] true), a frame at each instant.
    private fun row(
        night: Boolean,
        times: List<Int>,
        width: Int,
        height: Int,
        content: @Composable (go: Boolean) -> Unit
    ): List<Bitmap> {
        val go = mutableStateOf(false)
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
                                    .size(width.dp, height.dp)
                                    .clipToBounds()
                                    .background(MaterialTheme.colorScheme.background)
                            ) { content(go.value) }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        // The first composition and its enter animations settle on the stopped clock before the change.
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

    // The two rows joined, a label per row, the ms under each frame.
    private fun sheet(name: String, night: Boolean, times: List<Int>, today: List<Bitmap>, proposed: List<Bitmap>) =
        sheet(name, night, times, listOf("today" to today, "proposed" to proposed))

    private fun sheet(name: String, night: Boolean, times: List<Int>, rows: List<Pair<String, List<Bitmap>>>) {
        val scale = 0.6f
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
        sheet.captureRoboImage("src/test/screenshots/anim_${name}_draft_${if (night) "dark" else "light"}.png", roborazziOptions = options)
    }

    private fun film(name: String, width: Int, height: Int, times: List<Int> = standard,
                     today: @Composable (Boolean) -> Unit, proposed: @Composable (Boolean) -> Unit) {
        RuntimeEnvironment.setQualifiers("w411dp-h914dp-420dpi")
        listOf(false, true).forEach { night ->
            val a = row(night, times, width, height, today)
            val b = row(night, times, width, height, proposed)
            sheet(name, night, times, a, b)
        }
    }

    // A post card, as the cases show it: name, handle, text.
    @Composable
    private fun CardMini(modifier: Modifier = Modifier) {
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
            Spacer(Modifier.height(6.dp))
            Text("Morning light over the old harbour.", style = MaterialTheme.typography.bodyMedium, maxLines = 2)
        }
    }

    // 1. SparkButton.kt:127-141 (the star scale snapped to 0 after the first frame), PostActions.kt:132-155 (removed after
    // SPARK_DURATION_MILLIS). Today the app's own code; proposed the star inside AnimatedVisibility.
    // Instants differ from the others: the burst lasts a second, its end must show.
    // https://github.com/androidx/androidx/blob/androidx-main/compose/animation/animation/samples/src/main/java/androidx/compose/animation/samples/AnimatedVisibilitySamples.kt#AnimatedVisibilityWithBooleanVisibleParamNoReceiver
    @Composable
    private fun LikeBurst(go: Boolean, softStar: Boolean) {
        val spark = rememberSparkButtonState()
        var burst by remember { mutableStateOf(false) }
        var visible by remember { mutableStateOf(false) }
        LaunchedEffect(go) {
            if (go) {
                burst = true
                visible = true
                spark.animate()
                delay(700)
                visible = false
                delay(300)
                burst = false
            }
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CardMini(Modifier.fillMaxSize().padding(8.dp))
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
                    val star = @Composable {
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
                    if (softStar) {
                        AnimatedVisibility(
                            visible = visible,
                            enter = scaleIn(initialScale = 0f) + fadeIn(),
                            exit = fadeOut(animationSpec = tween(250)) + scaleOut()
                        ) { star() }
                    } else {
                        star()
                    }
                }
            }
        }
    }

    @Test
    fun likeEnd() = film(
        "like_end", 200, 130, listOf(0, 300, 600, 750, 850, 950, 1050),
        today = { LikeBurst(it, softStar = false) }, proposed = { LikeBurst(it, softStar = true) }
    )

    // 2. HoldMenu.kt:108-225: the Dialog is left out (it cannot be driven here): veil, card and pane drawn in place.
    // Today closing removes the dialog at once and the card is at rest the next frame.
    @Composable
    private fun HoldClose(go: Boolean, animated: Boolean) {
        val rise = remember { Animatable(1f) }
        val fade by animateFloatAsState(if (go) 0f else 1f, tween(200), label = "holdFade")
        LaunchedEffect(go) {
            if (go && animated) rise.animateTo(0f, spring(dampingRatio = 1f, stiffness = 500f))
        }
        val open = if (animated) true else !go
        val r = if (animated) rise.value else 1f
        val a = if (animated) fade else 1f
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainer))
            if (open) {
                Box(Modifier.fillMaxSize().graphicsLayer { alpha = a }.background(Color.Black.copy(alpha = 0.45f)))
            }
            CardMini(
                Modifier.padding(16.dp).fillMaxWidth().graphicsLayer {
                    val s = 1f + 0.05f * (if (open) r else 0f)
                    scaleX = s
                    scaleY = s
                    translationY = -4.dp.toPx() * (if (open) r else 0f)
                }
            )
            if (open) {
                Column(
                    Modifier.align(Alignment.BottomCenter).padding(16.dp).fillMaxWidth().graphicsLayer { alpha = a }
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(20.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Copy link"); Text("Share"); Text("Mute")
                }
            }
        }
    }

    @Test
    fun holdmenuClose() = film(
        "holdmenu_close", 200, 240,
        today = { HoldClose(it, animated = false) }, proposed = { HoldClose(it, animated = true) }
    )

    // 3. NewDot.kt:24-31 at the end of the name row (PostCard.kt:238): the dot arrives, the time after it moves.
    @Composable
    private fun NameRow(go: Boolean, proposed: Boolean, leaving: Boolean) {
        val unread = if (leaving) !go else go
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Amina Rahal", style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (proposed) {
                // The slot (start padding and dot) opens or closes first; the dot scales and fades from its own
                // centre inside it, so it never moves over the name.
                AnimatedVisibility(
                    visible = unread,
                    enter = expandHorizontally(tween(200)),
                    exit = shrinkHorizontally(tween(200, delayMillis = 100))
                ) {
                    Box(Modifier.padding(start = 8.dp).size(10.dp)) {
                        Box(
                            Modifier.fillMaxSize()
                                .animateEnterExit(
                                    enter = scaleIn(tween(200, delayMillis = 60)) + fadeIn(tween(200, delayMillis = 60)),
                                    exit = scaleOut(tween(120)) + fadeOut(tween(120))
                                )
                                .background(MaterialTheme.colorScheme.primary, CircleShape)
                        )
                    }
                }
            } else {
                NewDot(unread, Modifier.padding(start = 8.dp))
            }
            Text("  · 2h", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    // Two pairs in one picture: the dot arriving, then leaving.
    @Test
    fun newdot() {
        RuntimeEnvironment.setQualifiers("w411dp-h914dp-420dpi")
        listOf(false, true).forEach { night ->
            val rows = listOf(
                "arrives, today" to row(night, standard, 200, 50) { NameRow(it, proposed = false, leaving = false) },
                "arrives, proposed" to row(night, standard, 200, 50) { NameRow(it, proposed = true, leaving = false) },
                "leaves, today" to row(night, standard, 200, 50) { NameRow(it, proposed = false, leaving = true) },
                "leaves, proposed" to row(night, standard, 200, 50) { NameRow(it, proposed = true, leaving = true) }
            )
            sheet("newdot", night, standard, rows)
        }
    }

    // 4. ProfileHeader.kt:270-308 FollowButton (private: redrawn from its lines). Its private PersonAddRounded path
    // (ProfileHeader.kt:419, Material Symbols Rounded person_add) is copied here.
    private val personAdd: androidx.compose.ui.graphics.vector.ImageVector by lazy {
        androidx.compose.ui.graphics.vector.ImageVector.Builder("PersonAdd", 24.dp, 24.dp, viewportWidth = 960f, viewportHeight = 960f)
            .addPath(
                pathData = androidx.compose.ui.graphics.vector.PathParser().parsePathString(
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
                ).toNodes(),
                fill = androidx.compose.ui.graphics.SolidColor(Color.White)
            )
            .build()
    }

    // https://github.com/androidx/androidx/blob/androidx-main/compose/animation/animation/samples/src/main/java/androidx/compose/animation/samples/AnimatedContentSamples.kt#SimpleAnimatedContentSample
    @Composable
    private fun Follow(following: Boolean, proposed: Boolean) {
        val colors = MaterialTheme.colorScheme
        val shape = RoundedCornerShape(28.dp)
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (!proposed) {
                val contentColor = if (following) colors.primary else colors.onPrimary
                Row(
                    Modifier.height(56.dp)
                        .then(
                            if (following) Modifier.border(1.dp, colors.outline, shape)
                            else Modifier.background(colors.primary, shape)
                        )
                        .padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(if (following) MtgaIcons.CheckRounded else personAdd, null, Modifier.size(24.dp), tint = contentColor)
                    Spacer(Modifier.width(8.dp))
                    Text(if (following) "Following" else "Follow", style = MaterialTheme.typography.titleMedium, color = contentColor)
                }
            } else {
                // Same hue, alpha only: a fade to Color.Transparent would pass through grey.
                val container by animateColorAsState(if (following) colors.primary.copy(alpha = 0f) else colors.primary, tween(200), label = "c")
                val line by animateColorAsState(if (following) colors.outline else colors.outline.copy(alpha = 0f), tween(200), label = "b")
                val contentColor by animateColorAsState(if (following) colors.primary else colors.onPrimary, tween(200), label = "t")
                Row(
                    Modifier.height(56.dp)
                        .background(container, shape)
                        .border(1.dp, line, shape)
                        .padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AnimatedContent(
                        targetState = following,
                        // Not clipped while the width changes (SizeTransform, AnimatedContentSamples.kt).
                        transitionSpec = {
                            (fadeIn(tween(110, delayMillis = 90)) togetherWith fadeOut(tween(90)))
                                .using(androidx.compose.animation.SizeTransform(clip = false))
                        },
                        label = "followContent"
                    ) { isFollowing ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (isFollowing) MtgaIcons.CheckRounded else personAdd, null, Modifier.size(24.dp), tint = contentColor)
                            Spacer(Modifier.width(8.dp))
                            Text(if (isFollowing) "Following" else "Follow", style = MaterialTheme.typography.titleMedium, color = contentColor)
                        }
                    }
                }
            }
        }
    }

    @Test
    fun follow() = film(
        "follow", 200, 80,
        today = { Follow(it, proposed = false) }, proposed = { Follow(it, proposed = true) }
    )

    // 5. MediaViewerFrame.kt:226-251 ZoomableImage (Coil's image replaced by a drawn photo): the double tap's 1x to 2.5x.
    // https://github.com/androidx/androidx/blob/androidx-main/compose/animation/animation-core/samples/src/main/java/androidx/compose/animation/core/samples/AnimatableSamples.kt#AnimatableAnimateToGenericsType
    @Composable
    private fun Photo(scale: Float) {
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = (size.width / 2f - size.width * 0.7f) * (scale - 1f)
                translationY = (size.height / 2f - size.height * 0.62f) * (scale - 1f)
            }.drawBehind {
                drawRect(Brush.verticalGradient(listOf(Color(0xFF7FB3E6), Color(0xFFF2D6A8)), 0f, size.height * 0.62f))
                drawCircle(Color(0xFFFFF1B8), size.minDimension * 0.09f, Offset(size.width * 0.7f, size.height * 0.34f))
                val hills = Path().apply {
                    moveTo(0f, size.height * 0.62f)
                    cubicTo(size.width * 0.3f, size.height * 0.5f, size.width * 0.6f, size.height * 0.7f, size.width, size.height * 0.58f)
                    lineTo(size.width, size.height); lineTo(0f, size.height); close()
                }
                drawPath(hills, Color(0xFF3C6E4F))
                drawRect(Color(0xFF2B4F3A), Offset(size.width * 0.66f, size.height * 0.5f), androidx.compose.ui.geometry.Size(size.width * 0.08f, size.height * 0.12f))
            }
        )
    }

    @Composable
    private fun Zoom(go: Boolean, animated: Boolean) {
        val scale = remember { Animatable(1f) }
        LaunchedEffect(go) {
            if (go) {
                if (animated) scale.animateTo(2.5f, spring(dampingRatio = 0.8f, stiffness = 400f)) else scale.snapTo(2.5f)
            }
        }
        Photo(scale.value)
    }

    @Test
    fun zoom() = film(
        "zoom", 200, 200,
        today = { Zoom(it, animated = false) }, proposed = { Zoom(it, animated = true) }
    )

    // 6. GlassVideo.kt:401-412: the double tap bubble on the left ("-10 s"), then it leaves. Redrawn: it sits inside the
    // player's whole screen.
    @Composable
    private fun Bubble(go: Boolean, keepSide: Boolean) {
        var last by remember { mutableIntStateOf(-1) }
        val jumped = if (go) 0 else -1
        if (jumped != 0) last = jumped
        val side = if (keepSide) last else jumped
        Box(Modifier.fillMaxSize().background(Color(0xFF1B1F2A))) {
            AnimatedVisibility(
                visible = jumped != 0,
                enter = fadeIn() + scaleIn(initialScale = 0.7f),
                exit = fadeOut() + scaleOut(targetScale = 0.9f),
                modifier = Modifier
                    .align(if (side < 0) Alignment.CenterStart else Alignment.CenterEnd)
                    .padding(horizontal = 40.dp)
            ) {
                Text(
                    if (side < 0) "−10 s" else "+10 s",
                    style = MaterialTheme.typography.labelLarge.copy(shadow = Shadow(Color.Black.copy(alpha = 0.6f), Offset(0f, 2f), 6f)),
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp)
                )
            }
        }
    }

    @Test
    fun videoBubble() = film(
        "video_bubble", 260, 100,
        today = { Bubble(it, keepSide = false) }, proposed = { Bubble(it, keepSide = true) }
    )
}
