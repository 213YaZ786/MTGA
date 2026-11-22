package com.mtga.app.feature.welcome

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.semantics.Role
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
import com.mtga.app.ui.component.rememberHaptics
import kotlin.math.max

// Renamed, not left out: cookie9 is Cookie9Sided, circle is CircleShape, PlayButton is StartCookie, below.
// not ported: cookie9, circle, PlayButton (their names only: the code is below under the names this app uses)

/**
 * FabMediumTokens: ContainerHeight 80 dp, IconSize 28 dp (the container is as wide as it is high).
 * https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/tokens/FabMediumTokens.kt#FabMediumTokens
 * not ported: ContainerShape (the cookie here), ContainerWidth, container colours, focus, hover, pressed and icon
 * colours, elevations (the button draws its own).
 */
private val FabMediumContainerSize: Dp = 80.dp
private val FabMediumIconSize: Dp = 28.dp

/**
 * arrow_forward, Material Symbols Rounded 24px: viewport 960, mirrored in right-to-left as MtgaIcons does for
 * ArrowBack (autoMirror).
 * https://github.com/google/material-design-icons/blob/master/symbols/android/arrow_forward/materialsymbolsrounded/arrow_forward_24px.xml
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
// https://github.com/PaulWoitaschek/Voice/blob/main/core/ui/src/main/kotlin/voice/core/ui/PlayButton.kt#MorphShape
// The progress is a lambda read when the outline is made (draw phase): the spring does not recompose the button.
// A new shape is built from each progress (graphicsLayer's block below), so the clip is redrawn for certain; the bounds are
// worked out once by the caller.
private class MorphShape(private val morph: Morph, private val bounds: Rect, private val progress: Float) : Shape {
    private val matrix = Matrix()

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = morph.toPath(progress = progress.coerceIn(0f, 1f)).asComposePath()
        matrix.reset()
        val maxDimension = max(bounds.width, bounds.height)
        matrix.scale(size.width / maxDimension, size.height / maxDimension)
        matrix.translate(-bounds.left, -bounds.top)
        path.transform(matrix)
        return Outline.Generic(path)
    }
}

/**
 * Start of the guide: Voice's PlayButton, an 80 dp cookie that morphs to the circle while pressed, shrinks to .86,
 * turns a quarter at each tap, the arrow turning back against it. A clickable Box with a ripple, not a Material
 * control. Voice's morph follows `playing`, here it follows the press, so the button rests as a cookie.
 * https://github.com/PaulWoitaschek/Voice/blob/main/core/ui/src/main/kotlin/voice/core/ui/PlayButton.kt#PlayButton
 * not ported: animationsEnabled (MTGA has no animations-off check, the springs always run), PlayPauseIcon and
 * rememberPlayPausePainter (Start has one icon, ArrowForward), the size parameter (FabMediumContainerSize),
 * ToggleOff (nothing to switch off).
 */
@Composable
internal fun StartCookie(
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = rememberHaptics()
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val morph = remember { Morph(Cookie9Sided, CircleShape) }
    val morphProgress = animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow),
        label = "startMorph"
    )
    val morphBounds = remember(morph) { morph.calculateBounds().let { Rect(it[0], it[1], it[2], it[3]) } }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.86f else 1f,
        animationSpec = spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMedium),
        label = "startScale"
    )
    var turns by remember { mutableIntStateOf(0) }
    val rotation by animateFloatAsState(
        targetValue = turns * 90f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow),
        label = "startRotation"
    )
    val containerColor = MaterialTheme.colorScheme.primary
    val contentColor = MaterialTheme.colorScheme.onPrimary
    Box(
        modifier = modifier
            .size(FabMediumContainerSize)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                rotationZ = rotation
            }
            // The clip is drawn through the layer: its block reads the progress (snapshot observed, no recomposition
            // per frame) and builds the shape from it.
            .graphicsLayer {
                shape = MorphShape(morph, morphBounds, morphProgress.value)
                clip = true
            }
            .background(containerColor)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(color = contentColor),
                role = Role.Button
            ) {
                turns++
                haptics.toggle(true)
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = rememberVectorPainter(ArrowForward),
            contentDescription = description,
            colorFilter = ColorFilter.tint(contentColor),
            modifier = Modifier.size(FabMediumIconSize).graphicsLayer { rotationZ = -rotation }
        )
    }
}
