package com.mtga.app.screenshot

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect as AndroidRect
import android.graphics.RuntimeShader
import android.util.Log
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.Interpolator
import android.view.animation.PathInterpolator
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.navigationBars
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import com.mtga.app.core.model.Post
import com.mtga.app.core.model.PostStats
import com.mtga.app.di.appModule
import com.mtga.app.feature.settings.SettingsIcons
import com.mtga.app.navigation.LocalReadableInset
import com.mtga.app.ui.component.BoldButton
import com.mtga.app.ui.component.LoadingMark
import com.mtga.app.ui.component.PostCard
import com.mtga.app.ui.component.SetupTile
import com.mtga.app.ui.component.SetupTileGrid
import com.mtga.app.ui.glass.LocalGlass
import com.mtga.app.ui.glass.rememberGlassLook
import com.mtga.app.ui.icon.MtgaIcons
import com.mtga.app.ui.theme.MtgaTheme
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
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * DRAFT for the user's yes (requests.md point 48), nothing of app/src/main changes: after the guide's Start the app opens
 * from the Start button outward to the screen's edges, with SystemUI's sparkle ripple drawn by its real AGSL shader
 * (android.graphics.RuntimeShader in a Compose ShaderBrush). A filmstrip as AnimationFilmstripDraftScreenshotTest makes
 * them (the test clock stopped, advanced to each instant, each frame captured with captureToImage, frames side by side
 * with their time): 0, 150, 350, 600, 900, 1250, 1750 ms, phone size, start_reveal_draft_light.png and
 * start_reveal_draft_dark.png, and one full size frame at 600 ms, start_reveal_draft_detail_light.png. The guide
 * (WelcomeGuideDraftScreenshotTest's layout and tiles) lies over Home's feed (PullOriginalDraftScreenshotTest's posts);
 * the hole cut in the guide is centred on the Start button and its radius is the first ripple's radius (the shader's
 * in_size.x * 0.5), Home shows through it.
 *
 * The ripple: colour colorScheme.primary at RIPPLE_DEFAULT_ALPHA, maxSize twice the distance from the Start button to
 * the farthest corner, duration 1750 ms (RippleView), sparkle strength RIPPLE_SPARKLE_STRENGTH, time in ms as RippleView
 * passes it. The waves: as MultiRippleController plays several RippleAnimations, two more ripples start from the same
 * centre 150 ms and 300 ms after the first, same config, no hole. These two delays are OUR choice: SystemUI sets none.
 *
 * Ports:
 * https://github.com/LineageOS/android_frameworks_base/blob/lineage-23.0/packages/SystemUI/animation/src/com/android/systemui/surfaceeffects/ripple/RippleShader.kt#RippleShader
 * https://github.com/LineageOS/android_frameworks_base/blob/lineage-23.0/packages/SystemUI/animation/src/com/android/systemui/surfaceeffects/shaderutil/ShaderUtilLibrary.kt#ShaderUtilLibrary
 * https://github.com/LineageOS/android_frameworks_base/blob/lineage-23.0/packages/SystemUI/animation/src/com/android/systemui/surfaceeffects/shaderutil/SdfShaderLibrary.kt#SdfShaderLibrary
 * https://github.com/LineageOS/android_frameworks_base/blob/lineage-23.0/packages/SystemUI/animation/src/com/android/systemui/surfaceeffects/ripple/RippleView.kt#RippleView
 * https://github.com/LineageOS/android_frameworks_base/blob/lineage-23.0/packages/SystemUI/animation/src/com/android/systemui/surfaceeffects/ripple/MultiRippleController.kt#MultiRippleController
 * https://github.com/LineageOS/android_frameworks_base/blob/lineage-23.0/packages/SystemUI/animation/src/com/android/systemui/surfaceeffects/ripple/RippleAnimationConfig.kt#RippleAnimationConfig
 * https://github.com/LineageOS/android_frameworks_base/blob/lineage-23.0/packages/SystemUI/animation/src/com/android/systemui/surfaceeffects/ripple/RippleAnimation.kt#RippleAnimation
 * (what MultiRippleController plays: applyConfigToShader, assignFadeParams, the update listener's body)
 * not ported: RippleShape ROUNDED_BOX and ELLIPSE with SHADER_ROUNDED_BOX_MAIN, SHADER_ELLIPSE_MAIN, ROUNDED_BOX_SHADER,
 * ELLIPSE_SHADER, buildShader, BOX_SDF, ROUNDED_BOX_SDF, ELLIPSE_SDF (the reveal is a circle), RippleView's
 * ValueAnimator, View callbacks, setBaseRingFadeParams, setSparkleRingFadeParams, setCenterFillFadeParams, setBlur,
 * setColor (defaults only, the test clock steps the animation, no View), the ripple's hardware acceleration check,
 * MultiRippleView's View (a list holder stands in), RippleAnimation's ValueAnimator (the clock steps update),
 * RippleAnimationConfig's shouldDistort false case (kept as a field), SparkleShader and TurbulenceNoiseShader (not used by
 * the ripple).
 */
@OptIn(ExperimentalRoborazziApi::class, ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = android.app.Application::class, sdk = [36])
class StartRevealDraftScreenshotTest : ScreenshotBase() {

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

    private val times = listOf(0, 150, 350, 600, 900, 1250, 1750)

    private val options = RoborazziOptions(
        compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0f)
    )

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

    private fun tile(id: String, name: String, icon: androidx.compose.ui.graphics.vector.ImageVector, state: String, summary: String, done: Boolean) =
        SetupTile(
            id = id, name = name, title = name, icon = icon, state = state, summary = summary, done = done,
            required = false, error = false, onClick = {}
        )

    private val tiles = listOf(
        tile("media", "Save media", MtgaIcons.Download, "Images and videos",
            "Images and videos you save go to the Pictures and Movies folders. Tap to choose.", done = true),
        tile("source", "Sources", SettingsIcons.Dns, "4 of 5",
            "The servers the reader tries for each post. Tap to choose.", done = true),
        tile("notify", "New posts", MtgaIcons.Bell, "Off", "New posts of the accounts you follow. Tap to turn on.", done = false),
        tile("links", "X links", SettingsIcons.Link, "Off",
            "x.com and twitter.com links tapped anywhere open in MTGA once Android's \"Open supported links\" is on for it. " +
                "Tap to open that page.", done = false)
    )

    // One test per mode, each a Roborazzi test as in https://github.com/takahirom/roborazzi/blob/main/sample-android/src/test/java/com/github/takahirom/roborazzi/sample/ManualTest.kt#captureRoboImage
    // (a second setContent in the same test kept the first row's state).
    @Test
    fun filmstripLight() = filmstrip(false)

    @Test
    fun filmstripDark() = filmstrip(true)

    private fun filmstrip(night0: Boolean) {
        RuntimeEnvironment.setQualifiers("w411dp-h914dp-420dpi")
        listOf(night0).forEach { night ->
            val frames = row(night)
            sheet(night, frames)
            if (!night) {
                frames[times.indexOf(600)].captureRoboImage(
                    "src/test/screenshots/start_reveal_draft_detail_light.png",
                    roborazziOptions = options
                )
            }
        }
    }

    // One row: the guide over Home, the reveal started ([go] true), a frame at each instant on the stopped clock.
    private fun row(night: Boolean): List<Bitmap> {
        val go = mutableStateOf(false)
        compose.mainClock.autoAdvance = false
        compose.activity.setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                MtgaTheme(darkTheme = night, dynamicColor = false) {
                    val look = rememberGlassLook(MaterialTheme.colorScheme, true, quiet = true)
                    CompositionLocalProvider(
                        LocalGlass provides look,
                        LocalContentColor provides MaterialTheme.colorScheme.onBackground
                    ) {
                        Box(
                            Modifier
                                .testTag("stage")
                                .fillMaxSize()
                                .clipToBounds()
                                .background(MaterialTheme.colorScheme.background)
                        ) { Reveal(go.value) }
                    }
                }
            }
        }
        compose.waitForIdle()
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

    // The frames joined, the ms under each.
    private fun sheet(night: Boolean, frames: List<Bitmap>) {
        val scale = 0.6f
        val w = (frames[0].width * scale).toInt()
        val h = (frames[0].height * scale).toInt()
        val ms = 40
        val gap = 12
        val sheet = Bitmap.createBitmap((w + gap) * times.size + gap, h + ms + gap, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(sheet)
        canvas.drawColor(if (night) 0xFF000000.toInt() else 0xFFFFFFFF.toInt())
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (night) 0xFFFFFFFF.toInt() else 0xFF000000.toInt(); textSize = 26f }
        val smooth = Paint(Paint.FILTER_BITMAP_FLAG)
        frames.forEachIndexed { i, frame ->
            val left = gap + i * (w + gap)
            canvas.drawBitmap(frame, null, AndroidRect(left, gap, left + w, gap + h), smooth)
            canvas.drawText("${times[i]} ms", left + 4f, gap + h + 28f, text)
        }
        sheet.captureRoboImage(
            "src/test/screenshots/start_reveal_draft_${if (night) "dark" else "light"}.png",
            roborazziOptions = options
        )
    }

    // Home under, the guide over it with the ripple's hole.
    @Composable
    private fun Reveal(go: Boolean) {
        // Milliseconds since Start: the first ripple plays 0 to 1750, the last wave ends at 1750 plus its delay.
        val clock = remember { Animatable(0f) }
        LaunchedEffect(go) {
            if (go) clock.animateTo(WAVE_TOTAL.toFloat(), tween(durationMillis = WAVE_TOTAL, easing = LinearEasing))
        }
        var centre by remember { mutableStateOf(Offset.Zero) }
        var area by remember { mutableStateOf(IntSize.Zero) }
        val colour = MaterialTheme.colorScheme.primary
        val density = LocalDensity.current.density
        // RippleView's setCenter, setMaxSize (twice the distance to the farthest corner), duration, pixelDensity, colour.
        val waves = remember(centre, area, colour, density) {
            if (area == IntSize.Zero || centre == Offset.Zero) null else {
                val farthest = hypot(max(centre.x, area.width - centre.x), max(centre.y, area.height - centre.y))
                val config = RippleAnimationConfig(
                    duration = RIPPLE_DURATION.toLong(),
                    centerX = centre.x,
                    centerY = centre.y,
                    maxWidth = farthest * 2f,
                    maxHeight = farthest * 2f,
                    pixelDensity = density,
                    color = colour.toArgb()
                )
                Waves(config)
            }
        }
        Box(Modifier.fillMaxSize().onSizeChanged { area = it }) {
            Feed()
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        // The hole follows the first ripple's radius, the shader's in_size.x * 0.5.
                        val first = waves?.also { it.advance(clock.value) }?.first
                        val radius = (first?.rippleShader?.rippleSize?.currentWidth ?: 0f) * 0.5f
                        if (radius > 0f) {
                            drawCircle(Color0, radius = radius, center = centre, blendMode = BlendMode.Clear)
                        }
                    }
            ) {
                Guide(onStartCentre = { centre = it })
            }
            // The real shader, over both layers: RippleView's onDraw for the circle shape, once per playing ripple.
            Box(
                Modifier.fillMaxSize().drawBehind {
                    val w = waves ?: return@drawBehind
                    w.advance(clock.value)
                    w.view.ripples.toList().forEach { ripple ->
                        drawCircle(
                            brush = ShaderBrush(ripple.rippleShader),
                            radius = ripple.rippleShader.rippleSize.currentWidth,
                            center = centre
                        )
                    }
                }
            )
        }
    }

    // Home's cards, as PullOriginalDraftScreenshotTest draws them.
    @Composable
    private fun Feed() {
        Column(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 16.dp + LocalReadableInset.current, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PostCard(post = posts[0], onClick = {}, onOpenLink = {}, onDownload = {}, unread = true)
            PostCard(post = posts[1], onClick = {}, onOpenLink = {}, onDownload = {})
            PostCard(post = posts[2], onClick = {}, onOpenLink = {}, onDownload = {})
        }
    }

    // WelcomeGuideDraftScreenshotTest's WelcomeGuideDraft (private to that file), on an opaque ground, reporting the
    // Start button's centre.
    @Composable
    private fun Guide(onStartCentre: (Offset) -> Unit) {
        BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .widthIn(max = 720.dp).fillMaxSize()
                    .windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 8.dp)
            ) {
                LoadingMark(size = 160.dp)
                Spacer(Modifier.height(16.dp))
                Text(
                    "X reader",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(Modifier.height(24.dp))
                SetupTileGrid(tiles) {}
                Spacer(Modifier.height(88.dp))
                Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
            }
            Column(Modifier.align(Alignment.BottomCenter).widthIn(max = 720.dp).fillMaxWidth().padding(horizontal = 24.dp)) {
                BoldButton(
                    filled = true,
                    onClick = {},
                    modifier = Modifier.fillMaxWidth().onGloballyPositioned { onStartCentre(it.boundsInRoot().center) }
                ) { Text("Start") }
                Spacer(Modifier.height(16.dp))
                Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
            }
        }
    }
}

private val Color0 = androidx.compose.ui.graphics.Color.Black

private const val RIPPLE_DURATION = 1750

// Our choice: the two waves start 150 ms and 300 ms after the first ripple.
private val WAVE_DELAYS = listOf(0, 150, 300)
private const val WAVE_TOTAL = 1750 + 300

// MultiRippleController plays the three RippleAnimations on one MultiRippleView, the clock of the test steps them.
private class Waves(config: RippleAnimationConfig) {
    val view = MultiRippleView()
    private val controller = MultiRippleController(view)
    private val animations = WAVE_DELAYS.map { RippleAnimation(config) }
    private val started = BooleanArray(animations.size)
    val first: RippleAnimation get() = animations[0]

    /** [t]: milliseconds since Start. */
    fun advance(t: Float) {
        animations.forEachIndexed { i, animation ->
            val playTime = (t - WAVE_DELAYS[i]).toLong()
            if (playTime >= 0 && !started[i]) {
                started[i] = true
                controller.play(animation)
            }
            if (started[i]) animation.update(min(playTime, RIPPLE_DURATION.toLong()))
        }
    }
}

// MultiRippleView without the View: the list of the playing ripples.
private class MultiRippleView {
    val ripples = mutableListOf<RippleAnimation>()
    fun invalidate() {}
}

// https://github.com/LineageOS/android_frameworks_base/blob/lineage-23.0/packages/SystemUI/animation/src/com/android/systemui/surfaceeffects/ripple/MultiRippleController.kt#MultiRippleController
private class MultiRippleController(private val multipleRippleView: MultiRippleView) {

    companion object {
        const val MAX_RIPPLE_NUMBER = 10
    }

    fun updateColor(color: Int) {
        multipleRippleView.ripples.forEach { anim -> anim.updateColor(color) }
    }

    fun play(rippleAnimation: RippleAnimation) {
        if (multipleRippleView.ripples.size >= MAX_RIPPLE_NUMBER) {
            return
        }

        multipleRippleView.ripples.add(rippleAnimation)

        rippleAnimation.play {
            // Remove ripple once the animation is done
            multipleRippleView.ripples.remove(rippleAnimation)
        }

        // Trigger drawing
        multipleRippleView.invalidate()
    }
}

// https://github.com/LineageOS/android_frameworks_base/blob/lineage-23.0/packages/SystemUI/animation/src/com/android/systemui/surfaceeffects/ripple/RippleAnimationConfig.kt#RippleAnimationConfig
private data class RippleAnimationConfig(
    val duration: Long = 0L,
    val centerX: Float = 0f,
    val centerY: Float = 0f,
    val maxWidth: Float = 0f,
    val maxHeight: Float = 0f,
    val pixelDensity: Float = 1f,
    var color: Int = android.graphics.Color.WHITE,
    val opacity: Int = RippleShader.RIPPLE_DEFAULT_ALPHA,
    val sparkleStrength: Float = RippleShader.RIPPLE_SPARKLE_STRENGTH,
    // Null means it uses default fade parameter values.
    val baseRingFadeParams: RippleShader.FadeParams? = null,
    val sparkleRingFadeParams: RippleShader.FadeParams? = null,
    val centerFillFadeParams: RippleShader.FadeParams? = null,
    val shouldDistort: Boolean = true
)

// https://github.com/LineageOS/android_frameworks_base/blob/lineage-23.0/packages/SystemUI/animation/src/com/android/systemui/surfaceeffects/ripple/RippleAnimation.kt#RippleAnimation
private class RippleAnimation(private val config: RippleAnimationConfig) {
    val rippleShader: RippleShader = RippleShader()
    private var playing = false
    private var onAnimationEnd: Runnable? = null

    init {
        applyConfigToShader()
    }

    fun updateColor(color: Int) {
        config.apply { config.color = color }
        applyConfigToShader()
    }

    fun play(onAnimationEnd: Runnable? = null) {
        if (isPlaying()) {
            return // Ignore if ripple effect is already playing
        }
        this.onAnimationEnd = onAnimationEnd
        playing = true
    }

    fun isPlaying(): Boolean = playing

    // The update listener of RippleAnimation.play: currentPlayTime in ms, animatedValue from ValueAnimator's default
    // AccelerateDecelerateInterpolator over the duration.
    fun update(now: Long) {
        if (!playing) return
        val progress = AccelerateDecelerateInterpolator().getInterpolation(now.toFloat() / config.duration)
        rippleShader.rawProgress = progress
        rippleShader.distortionStrength = if (config.shouldDistort) 1 - progress else 0f
        rippleShader.time = now.toFloat()
        if (now >= config.duration) {
            playing = false
            onAnimationEnd?.run()
        }
    }

    private fun applyConfigToShader() {
        with(rippleShader) {
            setCenter(config.centerX, config.centerY)
            rippleSize.setMaxSize(config.maxWidth, config.maxHeight)
            pixelDensity = config.pixelDensity
            // ColorUtils.setAlphaComponent(config.color, config.opacity)
            color = (config.color and 0x00ffffff) or (config.opacity shl 24)
            sparkleStrength = config.sparkleStrength

            assignFadeParams(baseRingFadeParams, config.baseRingFadeParams)
            assignFadeParams(sparkleRingFadeParams, config.sparkleRingFadeParams)
            assignFadeParams(centerFillFadeParams, config.centerFillFadeParams)
        }
    }

    private fun assignFadeParams(
        destFadeParams: RippleShader.FadeParams,
        srcFadeParams: RippleShader.FadeParams?
    ) {
        srcFadeParams?.let {
            destFadeParams.fadeInStart = it.fadeInStart
            destFadeParams.fadeInEnd = it.fadeInEnd
            destFadeParams.fadeOutStart = it.fadeOutStart
            destFadeParams.fadeOutEnd = it.fadeOutEnd
        }
    }
}

// https://github.com/LineageOS/android_frameworks_base/blob/lineage-23.0/packages/SystemUI/animation/src/com/android/systemui/surfaceeffects/shaderutil/ShaderUtilLibrary.kt#ShaderUtilLibrary
private object ShaderUtilLibrary {
    // language=AGSL
    const val SHADER_LIB =
        """
        float triangleNoise(vec2 n) {
            n  = fract(n * vec2(5.3987, 5.4421));
            n += dot(n.yx, n.xy + vec2(21.5351, 14.3137));
            float xy = n.x * n.y;
            // compute in [0..2[ and remap to [-1.0..1.0[
            return fract(xy * 95.4307) + fract(xy * 75.04961) - 1.0;
        }

        const float PI = 3.1415926535897932384626;

        float sparkles(vec2 uv, float t) {
            float n = triangleNoise(uv);
            float s = 0.0;
            for (float i = 0; i < 4; i += 1) {
                float l = i * 0.01;
                float h = l + 0.1;
                float o = smoothstep(n - l, h, n);
                o *= abs(sin(PI * o * (t + 0.55 * i)));
                s += o;
            }
            return s;
        }

        vec2 distort(vec2 p, float time, float distort_amount_radial,
            float distort_amount_xy) {
                float angle = atan(p.y, p.x);
                  return p + vec2(sin(angle * 8 + time * 0.003 + 1.641),
                            cos(angle * 5 + 2.14 + time * 0.00412)) * distort_amount_radial
                     + vec2(sin(p.x * 0.01 + time * 0.00215 + 0.8123),
                            cos(p.y * 0.01 + time * 0.005931)) * distort_amount_xy;
        }

        // Perceived luminosity (L′), not absolute luminosity.
        half getLuminosity(vec3 c) {
            return 0.3 * c.r + 0.59 * c.g + 0.11 * c.b;
        }

        // Creates a luminosity mask and clamp to the legal range.
        vec3 maskLuminosity(vec3 dest, float lum) {
            dest.rgb *= vec3(lum);
            // Clip back into the legal range
            dest = clamp(dest, vec3(0.), vec3(1.0));
            return dest;
        }

        // Integer mod. GLSL es 1.0 doesn't have integer mod :(
        int imod(int a, int b) {
            return a - (b * (a / b));
        }

        ivec3 imod(ivec3 a, int b) {
            return ivec3(imod(a.x, b), imod(a.y, b), imod(a.z, b));
        }

        // Integer based hash function with the return range of [-1, 1].
        vec3 hash(vec3 p) {
            ivec3 v = ivec3(p);
            v = v * 1671731 + 10139267;

            v.x += v.y * v.z;
            v.y += v.z * v.x;
            v.z += v.x * v.y;

            ivec3 v2 = v / 65536; // v >> 16
            v = imod((10 - imod((v + v2), 10)), 10); // v ^ v2

            v.x += v.y * v.z;
            v.y += v.z * v.x;
            v.z += v.x * v.y;

            // Use sin and cos to map the range to [-1, 1].
            return vec3(sin(float(v.x)), cos(float(v.y)), sin(float(v.z)));
        }

        // Skew factors (non-uniform).
        const half SKEW = 0.3333333;  // 1/3
        const half UNSKEW = 0.1666667;  // 1/6

        // Return range roughly [-1,1].
        half simplex3d(vec3 p) {
            // Skew the input coordinate, so that we get squashed cubical grid
            vec3 s = floor(p + (p.x + p.y + p.z) * SKEW);

            // Unskew back
            vec3 u = s - (s.x + s.y + s.z) * UNSKEW;

            // Unskewed coordinate that is relative to p, to compute the noise contribution
            // based on the distance.
            vec3 c0 = p - u;

            // Encode x0-y0, y0-z0, z0-x0 in a vec3
            vec3 en = c0 - c0.yzx;
            // Each represents whether x0>y0, y0>z0, z0>x0
            en = step(vec3(0.), en);
            // en.zxy encodes z0>x0, x0>y0, y0>x0
            vec3 offset1 = en * (1. - en.zxy); // find max
            vec3 offset2 = 1. - en.zxy * (1. - en); // 1-(find min)
            vec3 offset3 = vec3(1.);

            vec3 c1 = c0 - offset1 + UNSKEW;
            vec3 c2 = c0 - offset2 + UNSKEW * 2.;
            vec3 c3 = c0 - offset3 + UNSKEW * 3.;

            // Kernel summation: dot(max(0, r^2-d^2))^4, noise contribution)
            vec4 w; // w = max(0, r^2 - d^2))
            w.x = dot(c0, c0);
            w.y = dot(c1, c1);
            w.z = dot(c2, c2);
            w.w = dot(c3, c3);

            w = max(0.6 - w, 0.);

            // Noise contribution from each point.
            vec4 nc;
            nc.x = dot(hash(s), c0);
            nc.y = dot(hash(s + offset1), c1);
            nc.z = dot(hash(s + offset2), c2);
            nc.w = dot(hash(s + offset3), c3);

            nc *= w*w*w*w;

            // Add all the noise contributions.
            return dot(vec4(32.), nc);
        }

        // Random rotations.
        const mat3 rot1 = mat3(1.0, 0. ,0., 0., 0.15, -0.98, 0., 0.98, 0.15);
        const mat3 rot2 = mat3(-0.95, 0. ,-0.3, 0., 1., 0., 0.3, 0., -0.95);
        const mat3 rot3 = mat3(1.0, 0. ,0., 0., -0.44, -0.89, 0., 0.89, -0.44);

        // Octave = 4
        // Divide each coefficient by 3 to produce more grainy noise.
        half simplex3d_fractal(vec3 p) {
            return 0.675 * simplex3d(p * rot1) + 0.225 * simplex3d(2.0 * p * rot2)
                    + 0.075 * simplex3d(4.0 * p * rot3) + 0.025 * simplex3d(8.0 * p);
        }

        // Screen blend
        vec3 screen(vec3 dest, vec3 src) {
            return dest + src - dest * src;
        }
    """
}

// https://github.com/LineageOS/android_frameworks_base/blob/lineage-23.0/packages/SystemUI/animation/src/com/android/systemui/surfaceeffects/shaderutil/SdfShaderLibrary.kt#SdfShaderLibrary
private class SdfShaderLibrary {
    // language=AGSL
    companion object {
        const val CIRCLE_SDF =
            """
            float sdCircle(vec2 p, float r) {
                return (length(p)-r) / r;
            }

            float circleRing(vec2 p, float radius) {
                float thicknessHalf = radius * 0.25;

                float outerCircle = sdCircle(p, radius + thicknessHalf);
                float innerCircle = sdCircle(p, radius);

                return subtract(outerCircle, innerCircle);
            }
        """

        const val SHADER_SDF_OPERATION_LIB =
            """
            float soften(float d, float blur) {
                float blurHalf = blur * 0.5;
                return smoothstep(-blurHalf, blurHalf, d);
            }

            float subtract(float outer, float inner) {
                return max(outer, -inner);
            }
        """
    }
}

// https://github.com/LineageOS/android_frameworks_base/blob/lineage-23.0/packages/SystemUI/animation/src/com/android/systemui/surfaceeffects/ripple/RippleShader.kt#RippleShader
private class RippleShader : RuntimeShader(CIRCLE_SHADER) {

    // language=AGSL
    companion object {
        private val TAG = RippleShader::class.simpleName

        // Default fade in/ out values. The value range is [0,1].
        const val DEFAULT_FADE_IN_START = 0f
        const val DEFAULT_FADE_OUT_END = 1f

        const val DEFAULT_BASE_RING_FADE_IN_END = 0.1f
        const val DEFAULT_BASE_RING_FADE_OUT_START = 0.3f

        const val DEFAULT_SPARKLE_RING_FADE_IN_END = 0.1f
        const val DEFAULT_SPARKLE_RING_FADE_OUT_START = 0.4f

        const val DEFAULT_CENTER_FILL_FADE_IN_END = 0f
        const val DEFAULT_CENTER_FILL_FADE_OUT_START = 0f
        const val DEFAULT_CENTER_FILL_FADE_OUT_END = 0.6f

        const val RIPPLE_SPARKLE_STRENGTH: Float = 0.3f
        const val RIPPLE_DEFAULT_COLOR: Int = 0xffffffff.toInt()
        const val RIPPLE_DEFAULT_ALPHA: Int = 115 // full opacity is 255.

        private const val SHADER_UNIFORMS =
            """
            uniform vec2 in_center;
            uniform vec2 in_size;
            uniform float in_cornerRadius;
            uniform float in_thickness;
            uniform float in_time;
            uniform float in_distort_radial;
            uniform float in_distort_xy;
            uniform float in_fadeSparkle;
            uniform float in_fadeFill;
            uniform float in_fadeRing;
            uniform float in_blur;
            uniform float in_pixelDensity;
            layout(color) uniform vec4 in_color;
            uniform float in_sparkle_strength;
        """

        private const val SHADER_CIRCLE_MAIN =
            """
            vec4 main(vec2 p) {
                vec2 p_distorted = distort(p, in_time, in_distort_radial, in_distort_xy);
                float radius = in_size.x * 0.5;
                float sparkleRing = soften(circleRing(p_distorted-in_center, radius), in_blur);
                float inside = soften(sdCircle(p_distorted-in_center, radius * 1.25), in_blur);
                float sparkle = sparkles(p - mod(p, in_pixelDensity * 0.8), in_time * 0.00175)
                    * (1.-sparkleRing) * in_fadeSparkle;

                float rippleInsideAlpha = (1.-inside) * in_fadeFill;
                float rippleRingAlpha = (1.-sparkleRing) * in_fadeRing;
                float rippleAlpha = max(rippleInsideAlpha, rippleRingAlpha) * in_color.a;
                vec4 ripple = vec4(in_color.rgb, 1.0) * rippleAlpha;
                return mix(ripple, vec4(sparkle), sparkle * in_sparkle_strength);
            }
        """

        private const val CIRCLE_SHADER =
            SHADER_UNIFORMS +
                ShaderUtilLibrary.SHADER_LIB +
                SdfShaderLibrary.SHADER_SDF_OPERATION_LIB +
                SdfShaderLibrary.CIRCLE_SDF +
                SHADER_CIRCLE_MAIN

        private fun subProgress(start: Float, end: Float, progress: Float): Float {
            // Avoid division by 0.
            if (start == end) {
                // If start and end are the same and progress has exceeded the start/ end point,
                // treat it as 1, otherwise 0.
                return if (progress > start) 1f else 0f
            }

            val min = Math.min(start, end)
            val max = Math.max(start, end)
            val sub = Math.min(Math.max(progress, min), max)
            return (sub - start) / (end - start)
        }

        private fun getFade(fadeParams: FadeParams, rawProgress: Float): Float {
            val fadeIn = subProgress(fadeParams.fadeInStart, fadeParams.fadeInEnd, rawProgress)
            val fadeOut =
                1f - subProgress(fadeParams.fadeOutStart, fadeParams.fadeOutEnd, rawProgress)

            return Math.min(fadeIn, fadeOut)
        }

        private fun lerp(start: Float, stop: Float, amount: Float): Float {
            return start + (stop - start) * amount
        }

        // Copied from [Interpolators#STANDARD]. This is to remove dependency on AnimationLib.
        private val STANDARD: Interpolator = PathInterpolator(0.2f, 0f, 0f, 1f)
    }

    fun setCenter(x: Float, y: Float) {
        setFloatUniform("in_center", x, y)
    }

    var blurStart: Float = 1.25f
    var blurEnd: Float = 0.5f

    val rippleSize = RippleSize()

    var rawProgress: Float = 0.0f
        set(value) {
            field = value
            progress = STANDARD.getInterpolation(value)

            setFloatUniform("in_fadeSparkle", getFade(sparkleRingFadeParams, value))
            setFloatUniform("in_fadeRing", getFade(baseRingFadeParams, value))
            setFloatUniform("in_fadeFill", getFade(centerFillFadeParams, value))
        }

    private var progress: Float = 0.0f
        set(value) {
            field = value

            rippleSize.update(value)

            setFloatUniform("in_size", rippleSize.currentWidth, rippleSize.currentHeight)
            setFloatUniform("in_thickness", rippleSize.currentHeight * 0.5f)
            // Corner radius is always max of the min between the current width and height.
            setFloatUniform(
                "in_cornerRadius",
                Math.min(rippleSize.currentWidth, rippleSize.currentHeight)
            )
            setFloatUniform("in_blur", lerp(1.25f, 0.5f, value))
        }

    var time: Float = 0.0f
        set(value) {
            field = value
            setFloatUniform("in_time", value)
        }

    var color: Int = 0xffffff
        set(value) {
            field = value
            setColorUniform("in_color", value)
        }

    var sparkleStrength: Float = 0.0f
        set(value) {
            field = value
            setFloatUniform("in_sparkle_strength", value)
        }

    var distortionStrength: Float = 0.0f
        set(value) {
            field = value
            setFloatUniform("in_distort_radial", 75 * rawProgress * value)
            setFloatUniform("in_distort_xy", 75 * value)
        }

    var pixelDensity: Float = 1.0f
        set(value) {
            field = value
            setFloatUniform("in_pixelDensity", value)
        }

    val sparkleRingFadeParams =
        FadeParams(
            DEFAULT_FADE_IN_START,
            DEFAULT_SPARKLE_RING_FADE_IN_END,
            DEFAULT_SPARKLE_RING_FADE_OUT_START,
            DEFAULT_FADE_OUT_END
        )

    val baseRingFadeParams =
        FadeParams(
            DEFAULT_FADE_IN_START,
            DEFAULT_BASE_RING_FADE_IN_END,
            DEFAULT_BASE_RING_FADE_OUT_START,
            DEFAULT_FADE_OUT_END
        )

    val centerFillFadeParams =
        FadeParams(
            DEFAULT_FADE_IN_START,
            DEFAULT_CENTER_FILL_FADE_IN_END,
            DEFAULT_CENTER_FILL_FADE_OUT_START,
            DEFAULT_CENTER_FILL_FADE_OUT_END
        )

    data class FadeParams(
        var fadeInStart: Float = DEFAULT_FADE_IN_START,
        var fadeInEnd: Float,
        var fadeOutStart: Float,
        var fadeOutEnd: Float = DEFAULT_FADE_OUT_END,
    )

    data class SizeAtProgress(
        var t: Float,
        var width: Float,
        var height: Float
    )

    inner class RippleSize {
        var sizes = mutableListOf<SizeAtProgress>()
        var currentSizeIndex = 0
        val initialSize = SizeAtProgress(0f, 0f, 0f)

        var currentWidth: Float = 0f
            private set
        var currentHeight: Float = 0f
            private set

        fun setMaxSize(width: Float, height: Float) {
            setSizeAtProgresses(initialSize, SizeAtProgress(1f, width, height))
        }

        fun setSizeAtProgresses(vararg sizes: SizeAtProgress) {
            // Reset everything.
            this.sizes.clear()
            currentSizeIndex = 0

            this.sizes.addAll(sizes)
            this.sizes.sortBy { it.t }
        }

        fun update(progress: Float) {
            val targetIndex = updateTargetIndex(progress)
            val prevIndex = Math.max(targetIndex - 1, 0)

            val targetSize = sizes[targetIndex]
            val prevSize = sizes[prevIndex]

            val subProgress = subProgress(prevSize.t, targetSize.t, progress)

            currentWidth = targetSize.width * subProgress + prevSize.width
            currentHeight = targetSize.height * subProgress + prevSize.height
        }

        private fun updateTargetIndex(progress: Float): Int {
            if (sizes.isEmpty()) {
                // It could be empty on init.
                if (progress > 0f) {
                    Log.e(
                        TAG,
                        "Did you forget to set the ripple size? Use [setMaxSize] or " +
                            "[setSizeAtProgresses] before playing the animation."
                    )
                }
                // If there's no size is set, we set everything to 0 and return early.
                setSizeAtProgresses(initialSize)
                return currentSizeIndex
            }

            var candidate = sizes[currentSizeIndex]

            while (progress > candidate.t) {
                currentSizeIndex = Math.min(currentSizeIndex + 1, sizes.size - 1)
                candidate = sizes[currentSizeIndex]
            }

            return currentSizeIndex
        }
    }
}
