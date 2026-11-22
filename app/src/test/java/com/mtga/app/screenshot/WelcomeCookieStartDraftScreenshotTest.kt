package com.mtga.app.screenshot

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.TransformResult
import androidx.graphics.shapes.circle
import androidx.graphics.shapes.star
import androidx.graphics.shapes.toPath
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mtga.app.feature.settings.SettingsIcons
import com.mtga.app.ui.component.LoadingMark
import com.mtga.app.ui.component.SetupTile
import com.mtga.app.ui.component.SetupTileGrid
import com.mtga.app.ui.icon.MtgaIcons
import kotlin.math.max
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * DRAFT for the user's yes (requests.md point 26), nothing of app/src/main changes: WelcomeRoundStartDraftScreenshotTest's
 * layout with Start as an 80 dp cookie that morphs to a circle when pressed, under the tiles, part of the centred
 * content block, not pinned. Rest picture and pressed picture (progress 1, scale .86, a quarter turn).
 * Layout ported from https://github.com/SvenTiigi/WhatsNewKit/blob/main/Sources/View/WhatsNewView.swift#body
 * (carried from WelcomeRoundStartDraftScreenshotTest.kt#WelcomeRoundStartDraft).
 * not ported: WhatsNewView's footer band, secondary action sheet, version store, title and feature helper views
 * (the app's title and SetupTileGrid take their place).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = android.app.Application::class, sdk = [36])
class WelcomeCookieStartDraftScreenshotTest : ScreenshotBase() {

    private fun tile(
        id: String, name: String, icon: ImageVector, state: String, summary: String,
        done: Boolean, error: Boolean = false, onClick: () -> Unit = {}
    ) = SetupTile(
        id = id, name = name, title = name, icon = icon, state = state, summary = summary, done = done,
        required = false, error = error, onClick = onClick
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

    @Test
    fun draft() = capture("welcome_cookie_start_draft") { WelcomeCookieStartDraft(tiles, onStart = {}) }

    @Test
    fun pressed() = capture("welcome_cookie_start_pressed_draft") {
        WelcomeCookieStartDraft(tiles, onStart = {}, morphProgress = 1f, scale = 0.86f, rotation = 90f)
    }

    @Test
    fun edge() = captureEdge("welcome_cookie_start_draft") { WelcomeCookieStartDraft(tiles, onStart = {}) }
}

/**
 * FabMediumTokens: ContainerHeight 80 dp, IconSize 28 dp (the container is as wide as it is high).
 * https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/tokens/FabMediumTokens.kt#FabMediumTokens
 * not ported: ContainerShape (the cookie here), ContainerWidth, container colours, focus, hover, pressed and icon
 * colours, elevations (a draft).
 */
private val FabMediumContainerSize: Dp = 80.dp
private val FabMediumIconSize: Dp = 28.dp

/**
 * arrow_forward, Material Symbols Rounded 24px: viewport 960, built as MtgaIcons' build does, mirrored in
 * right-to-left as MtgaIcons does for ArrowBack (autoMirror).
 * https://github.com/google/material-design-icons/blob/master/symbols/android/arrow_forward/materialsymbolsrounded/arrow_forward_24px.xml#arrow_forward
 * not ported: the tint attribute (the icon takes the colour of its use).
 */
private val ArrowForward: ImageVector by lazy {
    ImageVector.Builder(
        name = "ArrowForward",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 960f,
        viewportHeight = 960f,
        autoMirror = true
    ).apply {
        addPath(
            pathData = PathParser().parsePathString(
                "M647,520L200,520Q183,520 171.5,508.5Q160,497 160,480Q160,463 171.5,451.5Q183,440 200,440L647,440" +
                    "L451,244Q439,232 439.5,216Q440,200 452,188Q464,177 480,176.5Q496,176 508,188L772,452" +
                    "Q778,458 780.5,465Q783,472 783,480Q783,488 780.5,495Q778,502 772,508L508,772" +
                    "Q497,783 480.5,783Q464,783 452,772Q440,760 440,743.5Q440,727 452,715L647,520Z"
            ).toNodes(),
            fill = SolidColor(Color.Black)
        )
    }.build()
}

/**
 * Material 3 Expressive's Cookie9Sided: a nine pointed star, inner radius .8, rounding .5, turned -90 degrees, then
 * normalized as MaterialShapes' getter does. Built from graphics-shapes (MaterialShapes is experimental in material3).
 * https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/MaterialShapes.kt#cookie9
 * not ported: the other shapes of MaterialShapes and customPolygon (only the cookie and the circle are drawn).
 */
private val Cookie9Sided: RoundedPolygon by lazy {
    RoundedPolygon.star(numVerticesPerRadius = 9, innerRadius = .8f, rounding = CornerRounding(radius = .5f))
        .transformed { x, y -> TransformResult(y, -x) }
        .normalized()
}

/**
 * MaterialShapes' circle, ten vertices as its default.
 * https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/MaterialShapes.kt#circle
 */
private val CircleShape: RoundedPolygon by lazy { RoundedPolygon.circle(numVertices = 10).normalized() }

/**
 * A morph between two polygons as a clip shape at a progress, scaled to the size it is given as MTGA's
 * RoundedPolygonShape does (SunnyShape.kt).
 * https://github.com/PaulWoitaschek/Voice/blob/main/core/ui/src/main/kotlin/voice/core/ui/MorphShape.kt#MorphShape
 */
private class MorphShape(private val morph: Morph, private val progress: Float) : Shape {
    private val matrix = Matrix()
    private val bounds = morph.calculateBounds().let { Rect(it[0], it[1], it[2], it[3]) }

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = morph.toPath(progress = progress).asComposePath()
        matrix.reset()
        val maxDimension = max(bounds.width, bounds.height)
        matrix.scale(size.width / maxDimension, size.height / maxDimension)
        matrix.translate(-bounds.left, -bounds.top)
        path.transform(matrix)
        return Outline.Generic(path)
    }
}

/**
 * Voice's PlayButton: an 80 dp cookie that morphs to the circle at `morphProgress`, shrinks to `scale` and turns by
 * `rotation`, the arrow turning back against it. Fixed values here (the picture is still). A clickable Box with a
 * ripple, not a Material control; the haptic is Voice's ToggleOn.
 * https://github.com/PaulWoitaschek/Voice/blob/main/core/ui/src/main/kotlin/voice/core/ui/PlayButton.kt#PlayButton
 * not ported: the animateFloatAsState springs (morph .6 / MediumLow, scale .4 / Medium, turn .55 / Low),
 * collectIsPressedAsState, the turns counter, animationsEnabled, PlayPauseIcon and rememberPlayPausePainter (Start has
 * one icon, ArrowForward), the size parameter (FabMediumContainerSize).
 */
@Composable
private fun CookieStartDraft(
    onClick: () -> Unit,
    description: String,
    morphProgress: Float,
    scale: Float,
    rotation: Float,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val morph = remember { Morph(Cookie9Sided, CircleShape) }
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .size(FabMediumContainerSize)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                rotationZ = rotation
            }
            .clip(MorphShape(morph, morphProgress.coerceIn(0f, 1f)))
            .background(colors.primary)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(color = colors.onPrimary),
                role = Role.Button,
                onClickLabel = description
            ) {
                haptics.performHapticFeedback(HapticFeedbackType.ToggleOn)
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = rememberVectorPainter(ArrowForward),
            contentDescription = description,
            colorFilter = ColorFilter.tint(colors.onPrimary),
            modifier = Modifier.size(FabMediumIconSize).graphicsLayer { rotationZ = -rotation }
        )
    }
}

/**
 * WhatsNewView's body without the pinned footer: the button ends the content block, which is centred when shorter
 * than the screen, with no bottom reserve.
 * https://github.com/SvenTiigi/WhatsNewKit/blob/main/Sources/View/WhatsNewView.swift#body
 */
@Composable
private fun WelcomeCookieStartDraft(
    tiles: List<SetupTile>,
    onStart: () -> Unit,
    morphProgress: Float = 0f,
    scale: Float = 1f,
    rotation: Float = 0f
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val screen = maxHeight
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .widthIn(max = 720.dp).fillMaxWidth().heightIn(min = screen)
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
                Spacer(Modifier.height(40.dp))
                CookieStartDraft(
                    onClick = onStart, description = "Start",
                    morphProgress = morphProgress, scale = scale, rotation = rotation
                )
                Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
            }
        }
    }
}
