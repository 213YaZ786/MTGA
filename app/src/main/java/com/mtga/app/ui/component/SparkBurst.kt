package com.mtga.app.ui.component

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The like animation of a post: a disc bursts out of the icon and hollows
 * into a ring, the icon pops in with a strong overshoot, and a crown of
 * dots flies outwards and fades. Played once when it enters, then [onEnd]
 * is called so the caller can remove it.
 *
 * Ported from SparkButton (Apache-2.0), https://github.com/varunest/sparkbutton :
 * SparkButton.java (playAnimation: the delays, durations and interpolators of
 * the five animators, the size factors), helpers/CircleView.java (outer and
 * inner radius progress, the colour change of the disc), helpers/DotsView.java
 * (the two rings of dots, their radii, sizes, colours and fade over progress)
 * and helpers/Utils.java (mapValueFromRangeToRange, clamp).
 *
 * Improvements taken from the fork Tusky uses, https://github.com/connyduck/sparkbutton
 * (Apache-2.0): helpers/SparkAnimationView.java (12 dots instead of 10, dots
 * kept at float positions, the disc drawn without an offscreen bitmap and
 * its colour going from primary to secondary, a darker shade of each colour
 * for the dots) and sparkbutton-compose Easing.kt and Utils.kt (the
 * interpolators written as Compose Easings, the colour mix and the darker
 * shade).
 *
 * The three interpolators are those of Android, AOSP frameworks/base
 * core/java/android/view/animation/DecelerateInterpolator.java,
 * OvershootInterpolator.java and AccelerateDecelerateInterpolator.java.
 *
 * One clock runs the whole burst and every part reads its own slice of it,
 * so the parts stay in step, and the system's animation speed scales all of
 * them together. The colours come from the caller's theme: [primary] tints
 * the icon, [secondary] ends the disc and the dots.
 *
 * The canvas is [iconSize] times three, centred on the icon, and overflows
 * the space of the icon without taking any.
 */
@Composable
fun SparkBurst(
    icon: ImageVector,
    iconSize: Dp,
    primary: Color,
    secondary: Color,
    onEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    val clock = remember { Animatable(0f) }
    val finished by rememberUpdatedState(onEnd)
    val resolver = LocalContext.current.contentResolver
    LaunchedEffect(Unit) {
        // With animations switched off in the system, jump to the end state.
        val speed = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        if (speed == 0f) {
            clock.snapTo(SPARK_TOTAL_MS)
        } else {
            clock.animateTo(SPARK_TOTAL_MS, tween(SPARK_TOTAL_MS.toInt(), easing = LinearEasing))
        }
        finished()
    }
    val primaryDark = remember(primary) { primary.darken(SPARK_DARKEN) }
    val secondaryDark = remember(secondary) { secondary.darken(SPARK_DARKEN) }
    Box(modifier.size(iconSize), contentAlignment = Alignment.Center) {
        Canvas(Modifier.requiredSize(iconSize * DOTVIEW_SIZE_FACTOR)) {
            drawBurst(clock.value, iconSize.toPx(), primary, primaryDark, secondary, secondaryDark)
        }
        Image(
            painter = rememberVectorPainter(icon),
            contentDescription = null,
            colorFilter = ColorFilter.tint(primary),
            modifier = Modifier.size(iconSize).graphicsLayer {
                val scale = iconScale(clock.value)
                scaleX = scale
                scaleY = scale
            }
        )
    }
}

/** SparkButton.playAnimation: the disc, then the dots, as one picture at [ms]. */
private fun DrawScope.drawBurst(
    ms: Float,
    iconPx: Float,
    primary: Color,
    primaryDark: Color,
    secondary: Color,
    secondaryDark: Color
) {
    // The disc: the outer radius grows for 250 ms, the inner one (the hole)
    // follows 200 ms later, so the disc turns into a ring that thins out.
    val outer = 0.1f + 0.9f * DecelerateEasing.transform(slice(ms, 0f, 250f))
    val inner = if (ms < 200f) 0f else 0.1f + 0.9f * DecelerateEasing.transform(slice(ms, 200f, 200f))
    val maxCircle = iconPx * CIRCLEVIEW_SIZE_FACTOR / 2f
    if (outer > inner) {
        // A stroke centred between both radii is the ring between them, with
        // no offscreen bitmap to clear as CircleView does.
        val width = (outer - inner) * maxCircle
        val colorProgress = mapRange(outer.coerceIn(0.5f, 1f), 0.5f, 1f, 0f, 1f)
        drawCircle(
            color = mix(primary, secondary, colorProgress),
            radius = (outer + inner) / 2f * maxCircle,
            center = center,
            style = Stroke(width)
        )
    }

    // The dots: two rings, the outer one slower, the inner one smaller.
    val p = AccelerateDecelerateEasing.transform(slice(ms, 50f, 900f))
    val maxDot = iconPx * DOTS_SIZE_FACTOR
    val maxOuterRadius = size.width / 2f - maxDot * 2f
    val maxInnerRadius = 0.8f * maxOuterRadius

    val radius1 = if (p < 0.3f) mapRange(p, 0f, 0.3f, 0f, maxOuterRadius * 0.8f)
    else mapRange(p, 0.3f, 1f, 0.8f * maxOuterRadius, maxOuterRadius)
    val dot1 = if (p < 0.7f) maxDot else mapRange(p, 0.7f, 1f, maxDot, 0f)

    val radius2 = if (p < 0.3f) mapRange(p, 0f, 0.3f, 0f, maxInnerRadius) else maxInnerRadius
    val dot2 = when {
        p < 0.2f -> maxDot
        p < 0.5f -> mapRange(p, 0.2f, 0.5f, maxDot, 0.3f * maxDot)
        else -> mapRange(p, 0.5f, 1f, 0.3f * maxDot, 0f)
    }

    // The dots fade out over the last 40 % of the progress.
    val alpha = mapRange(p.coerceIn(0.6f, 1f), 0.6f, 1f, 1f, 0f)
    if (alpha <= 0f) return
    for (i in 0 until DOTS_COUNT) {
        val outerAngle = i * DOTS_ANGLE * PI / 180.0
        drawCircle(
            color = dotColor(i % 4, p, primary, primaryDark, secondary, secondaryDark).copy(alpha = alpha),
            radius = dot1,
            center = Offset(
                (center.x + radius1 * cos(outerAngle)).toFloat(),
                (center.y + radius1 * sin(outerAngle)).toFloat()
            )
        )
        val innerAngle = (i * DOTS_ANGLE - 10f) * PI / 180.0
        drawCircle(
            color = dotColor((i + 1) % 4, p, primary, primaryDark, secondary, secondaryDark).copy(alpha = alpha),
            radius = dot2,
            center = Offset(
                (center.x + radius2 * cos(innerAngle)).toFloat(),
                (center.y + radius2 * sin(innerAngle)).toFloat()
            )
        )
    }
}

/**
 * DotsView.updateDotsPaints: four colours that each slide to the next one,
 * the first half of the progress then the second, so the crown shimmers.
 */
private fun dotColor(
    index: Int,
    p: Float,
    primary: Color,
    primaryDark: Color,
    secondary: Color,
    secondaryDark: Color
): Color {
    val ring = if (p < 0.5f) {
        arrayOf(primary, primaryDark, secondary, secondaryDark, primary)
    } else {
        arrayOf(primaryDark, secondary, secondaryDark, primary, primaryDark)
    }
    val progress = if (p < 0.5f) mapRange(p, 0f, 0.5f, 0f, 1f) else mapRange(p, 0.5f, 1f, 0f, 1f)
    return mix(ring[index], ring[index + 1], progress)
}

/** SparkButton.playAnimation: the icon is hidden 250 ms, then pops from 0.2 to 1 over 350 ms. */
private fun iconScale(ms: Float): Float =
    if (ms < 250f) 0f else 0.2f + 0.8f * IconOvershoot.transform(slice(ms, 250f, 350f))

/** How far [ms] is through the part that starts at [delay] and lasts [duration], from 0 to 1. */
private fun slice(ms: Float, delay: Float, duration: Float): Float = ((ms - delay) / duration).coerceIn(0f, 1f)

/** helpers/Utils.java mapValueFromRangeToRange. */
private fun mapRange(value: Float, fromLow: Float, fromHigh: Float, toLow: Float, toHigh: Float): Float =
    toLow + (value - fromLow) / (fromHigh - fromLow) * (toHigh - toLow)

/** sparkbutton-compose Utils.kt Color.interpolate: a plain mix in sRGB, as the Java ArgbEvaluator gave. */
private fun mix(from: Color, to: Color, fraction: Float): Color {
    val a = from.convert(ColorSpaces.Srgb)
    val b = to.convert(ColorSpaces.Srgb)
    return Color(
        red = a.red + fraction * (b.red - a.red),
        green = a.green + fraction * (b.green - a.green),
        blue = a.blue + fraction * (b.blue - a.blue),
        alpha = a.alpha + fraction * (b.alpha - a.alpha)
    )
}

/** sparkbutton-compose Utils.kt Color.darken: the value of the HSV colour lowered by [amount]. */
private fun Color.darken(amount: Float): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(toArgb(), hsv)
    return Color.hsv(hsv[0], hsv[1], hsv[2] * (1f - amount), alpha)
}

/** Android's DecelerateInterpolator with its default factor 1. */
private val DecelerateEasing = Easing { t -> 1f - (1f - t) * (1f - t) }

/** Android's AccelerateDecelerateInterpolator. */
private val AccelerateDecelerateEasing = Easing { t -> (cos((t + 1f) * PI) / 2.0).toFloat() + 0.5f }

/**
 * Android's OvershootInterpolator, whose [tension] sets how far past the end it swings.
 * Source: https://github.com/aosp-mirror/platform_frameworks_base/blob/master/core/java/android/view/animation/OvershootInterpolator.java
 * and the Compose version https://github.com/connyduck/sparkbutton sparkbutton-compose Easing.kt
 */
private fun overshootEasing(tension: Float) = Easing { t ->
    val u = t - 1f
    u * u * ((tension + 1f) * u + tension) + 1f
}

// SparkButton.java: OvershootInterpolator(4) for the icon.
private val IconOvershoot = overshootEasing(4f)

// SparkButton.java: the canvas of the dots is three icons wide, the disc 1.4
// icons, a dot is 8 % of the icon.
private const val DOTVIEW_SIZE_FACTOR = 3f
private const val DOTS_SIZE_FACTOR = 0.08f
private const val CIRCLEVIEW_SIZE_FACTOR = 1.4f

// SparkAnimationView.java: twelve dots a ring. The darker shade is the
// compose fork's 10 %.
private const val DOTS_COUNT = 12
private const val DOTS_ANGLE = 360f / DOTS_COUNT
private const val SPARK_DARKEN = 0.1f

// The last animator ends at 950 ms: the dots, 50 ms delay plus 900 ms.
private const val SPARK_TOTAL_MS = 950f
