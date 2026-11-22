package com.mtga.app.ui.component

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** The feathers round the circle. */
private const val FeatherSlots = 16

/** The pull's diameter; a smaller ring scales the feather with it. */
private val CircularIndicatorDiameter = 168.dp

// The indeterminate motion's constants, as ProgressIndicator.kt has them.
private val CircularProgressEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
private val CircularEmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
private const val CircularIndeterminateMinProgress = 0.1f
private const val CircularIndeterminateMaxProgress = 0.87f
private const val CircularAnimationProgressDuration = 6000
private const val CircularAnimationAdditionalRotationDelay = 1500
private const val CircularAnimationAdditionalRotationDuration = 300
private const val CircularAdditionalRotationDegreesTarget = 360f
private const val CircularGlobalRotationDegreesTarget = 1080f

/**
 * CircularProgressIndicator's determinate form, [size] wide (168 dp in the pull): start at 12 o'clock (270 degrees),
 * sweep = progress x 360, the slots inside the sweep in full colour, the others as the track (outlineVariant at low
 * alpha, the indicator's trackColor); each feather laid radially, quill to the centre, tip outwards, drawn by
 * LoadingMark's drawFeather. The feather at the end of the sweep comes in with its share of the slot, so a fill grows
 * without a step. [centre] is the text in the middle.
 * https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/ProgressIndicator.kt#CircularProgressIndicator
 * not ported: strokeCap and gapSize (no stroke, the feathers are the slots), drawIndeterminateCircularIndicator (the
 * deprecated overload's stroke cap offset).
 */
@Composable
fun FeatherRing(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    size: Dp = CircularIndicatorDiameter,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.outlineVariant,
    centre: @Composable ColumnScope.() -> Unit = {}
) {
    val diameter = size
    val coercedProgress = { progress().coerceIn(0f, 1f) }
    val ink = rememberRingInk(color)
    val trackInk = remember(trackColor) { FeatherInk(trackColor, trackColor, trackColor, trackColor) }
    val art = remember { FeatherArt() }
    Box(
        modifier
            .semantics(mergeDescendants = true) {
                progressBarRangeInfo = ProgressBarRangeInfo(coercedProgress().takeUnless { it.isNaN() } ?: 0f, 0f..1f)
            }
            .size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            // Start at 12 o'clock
            val startAngle = 270f
            val sweep = coercedProgress() * 360f
            // One unit of the feather's 108 grid, 0.4 dp at 168 dp.
            val strokeWidth = 0.4.dp.toPx() * diameter.value / CircularIndicatorDiameter.value
            drawCircularIndicatorTrack(art, trackInk, strokeWidth)
            drawDeterminateCircularIndicator(art, startAngle, sweep, ink, strokeWidth)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, content = centre)
    }
}

/**
 * CircularProgressIndicator's indeterminate form: a global rotation (1080 degrees in 6 s), an additional quarter turn
 * every 1.5 s (300 ms of emphasized decelerate, then at rest), and an arc that grows from 10 % to 87 % of the circle
 * and back over 6 s, all infinite and continuous, no restart jump. [intro] is how far the arc has come in, 0 to 1,
 * read while drawing: the arc grows from nothing when the ring starts to run.
 * https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/ProgressIndicator.kt#CircularProgressIndicator
 * not ported: strokeCap and gapSize (no stroke), the gap sweep between the arc and the track (the track is the whole
 * circle of faint feathers under the arc).
 */
@Composable
fun FeatherRing(
    modifier: Modifier = Modifier,
    size: Dp = CircularIndicatorDiameter,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.outlineVariant,
    intro: () -> Float = { 1f },
    centre: @Composable ColumnScope.() -> Unit = {}
) {
    val diameter = size
    val ink = rememberRingInk(color)
    val trackInk = remember(trackColor) { FeatherInk(trackColor, trackColor, trackColor, trackColor) }
    val art = remember { FeatherArt() }

    val infiniteTransition = rememberInfiniteTransition()
    // A global rotation that does a 360 degrees rotation in 6 seconds.
    val globalRotation =
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = CircularGlobalRotationDegreesTarget,
            animationSpec = infiniteRepeatable(tween(CircularAnimationProgressDuration, easing = LinearEasing))
        )

    // An additional rotation that moves by 90 degrees in 300 ms and then rests for 1.2 seconds.
    val additionalRotation =
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = CircularAdditionalRotationDegreesTarget,
            animationSpec = infiniteRepeatable(
                keyframes {
                    durationMillis = CircularAnimationProgressDuration
                    90f at CircularAnimationAdditionalRotationDuration using CircularEmphasizedDecelerate
                    90f at CircularAnimationAdditionalRotationDelay
                    180f at CircularAnimationAdditionalRotationDuration + CircularAnimationAdditionalRotationDelay
                    180f at CircularAnimationAdditionalRotationDelay * 2
                    270f at CircularAnimationAdditionalRotationDuration + CircularAnimationAdditionalRotationDelay * 2
                    270f at CircularAnimationAdditionalRotationDelay * 3
                    360f at CircularAnimationAdditionalRotationDuration + CircularAnimationAdditionalRotationDelay * 3
                    360f at CircularAnimationProgressDuration
                }
            )
        )

    // Indicator progress animation that will be changing the progress up and down as the indicator rotates.
    val progressAnimation =
        infiniteTransition.animateFloat(
            initialValue = CircularIndeterminateMinProgress,
            targetValue = CircularIndeterminateMaxProgress,
            animationSpec = infiniteRepeatable(
                keyframes {
                    durationMillis = CircularAnimationProgressDuration
                    CircularIndeterminateMaxProgress at CircularAnimationProgressDuration / 2 using CircularProgressEasing
                    CircularIndeterminateMinProgress at CircularAnimationProgressDuration
                }
            )
        )

    Box(
        modifier
            .semantics(mergeDescendants = true) { progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate }
            .size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val sweep = progressAnimation.value * 360f * intro().coerceIn(0f, 1f)
            val strokeWidth = 0.4.dp.toPx() * diameter.value / CircularIndicatorDiameter.value
            rotate(globalRotation.value + additionalRotation.value) {
                drawCircularIndicatorTrack(art, trackInk, strokeWidth)
                drawDeterminateCircularIndicator(art, 270f, sweep, ink, strokeWidth)
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, content = centre)
    }
}

/** LoadingMark's ink. */
@Composable
private fun rememberRingInk(color: Color): FeatherInk = remember(color) {
    FeatherInk(
        base = lerp(color, Color.White, 0.72f), middle = lerp(color, Color.White, 0.38f),
        tip = color, shaft = lerp(color, Color.White, 0.3f)
    )
}

/** One feather per slot from [startAngle], those inside [sweep] in [ink], the last one by its share of its slot. */
private fun DrawScope.drawCircularIndicator(
    art: FeatherArt,
    startAngle: Float,
    sweep: Float,
    ink: FeatherInk,
    strokeWidth: Float,
    alpha: Float
) {
    val featherLength = 72f * strokeWidth
    val diameterOffset = featherLength / 2f
    val radius = size.minDimension / 2f - diameterOffset
    val c = art.centre
    val slot = 360f / FeatherSlots
    for (i in 0 until FeatherSlots) {
        val angle = i * slot
        val share = min((sweep - angle) / slot, 1f)
        if (share <= 0f) break
        val rad = Math.toRadians((startAngle + angle).toDouble())
        val x = size.width / 2f + radius * cos(rad).toFloat()
        val y = size.height / 2f + radius * sin(rad).toFloat()
        // The art's tip points 45 degrees clockwise from up: turned to the slot's direction.
        translate(x - c.x, y - c.y) {
            rotate(angle - 45f, pivot = c) {
                scale(strokeWidth, pivot = c) { drawFeather(art, ink, alpha * share) }
            }
        }
    }
}

/** The track: every slot, faint. */
private fun DrawScope.drawCircularIndicatorTrack(art: FeatherArt, ink: FeatherInk, strokeWidth: Float) =
    drawCircularIndicator(art, 270f, 360f, ink, strokeWidth, 0.35f)

private fun DrawScope.drawDeterminateCircularIndicator(
    art: FeatherArt,
    startAngle: Float,
    sweep: Float,
    ink: FeatherInk,
    strokeWidth: Float
) = drawCircularIndicator(art, startAngle, sweep, ink, strokeWidth, 1f)
