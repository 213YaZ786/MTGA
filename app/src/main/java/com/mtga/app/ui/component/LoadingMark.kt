package com.mtga.app.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/**
 * MTGA's loading mark: the feather of the launcher icon, in relief as on
 * the icon, three of them drifting down one after the other and rocking from
 * side to side the way a feather falls, like down let go by a bird. In the
 * accent's tones, from a light base to the accent itself at the tip, so it
 * follows the theme like the rest of the app.
 *
 * [progress] from 0 to 1 lets the first feathers in as far as a gesture has
 * gone. While [running] they fall on their own.
 */
@Composable
fun LoadingMark(
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    running: Boolean = true,
    progress: Float = 0f
) = LoadingMark(modifier, size, running) { progress }

/** The same mark with [progress] read while drawing, so a gesture moving it does not recompose its caller. */
@Composable
fun LoadingMark(
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    running: Boolean = true,
    progress: () -> Float
) {
    val accent = MaterialTheme.colorScheme.primary
    val ink = remember(accent) {
        FeatherInk(
            base = lerp(accent, Color.White, 0.72f),
            middle = lerp(accent, Color.White, 0.38f),
            tip = accent,
            shaft = lerp(accent, Color.White, 0.3f)
        )
    }
    val art = remember { FeatherArt() }
    val centre = art.centre

    // One time source for both ways: the clock continues from where it stands, and a gesture's progress glides from
    // there, so the feathers never jump when `running` changes. Nothing runs at rest.
    // https://github.com/androidx/androidx/blob/androidx-main/compose/animation/animation-core/samples/src/main/java/androidx/compose/animation/core/samples/AnimatableSamples.kt#AnimatableAnimateToGenericsType
    // (the sample's Animatable remembered, moved by animateTo in a coroutine; its tap detection and Offset are not ported:
    // the target is the clock or the pull, not a touch).
    val currentProgress by rememberUpdatedState(progress)
    val clock = remember { Animatable(if (running) 0f else currentProgress() / 3f) }
    LaunchedEffect(running) {
        if (running) {
            // The infinite frame clock, as InfiniteTransition runs on: a test waiting for idle does not wait for it.
            var last = withInfiniteAnimationFrameMillis { it }
            while (true) {
                val now = withInfiniteAnimationFrameMillis { it }
                val next = clock.value + (now - last).toFloat() / FALL_MILLIS
                clock.snapTo(if (next >= 1f) next - 1f else next)
                last = now
            }
        } else {
            // A gesture lets the feathers in a third of a fall at most.
            snapshotFlow { currentProgress() / 3f }.collectLatest { clock.animateTo(it, tween(90, easing = LinearEasing)) }
        }
    }

    // Not clipped to its box: each feather enters and leaves by its own fade (`seen`), so nothing is cut flat at the edges.
    Canvas(modifier.size(size)) {
        val time = clock.value
        val unit = this.size.minDimension / 100f
        FEATHERS.forEachIndexed { i, (lane, scale) ->
            val t = (time + i / FEATHERS.size.toFloat()) % 1f
            if (!running && time + i / FEATHERS.size.toFloat() >= 1f) return@forEachIndexed
            // Rocking: side to side and tilting with it, a little over one
            // swing per fall, each feather out of step with the others.
            val swing = sin(t * PI.toFloat() * 2.2f + i)
            val x = 50f + lane + swing * 10f
            val y = -25f + t * 130f
            val seen = min(1f, min(t * 5f, (1f - t) * 5f))
            translate(x * unit - centre.x, y * unit - centre.y) {
                rotate(swing * 28f - 35f, pivot = centre) {
                    scale(scale * unit, pivot = centre) {
                        drawFeather(art, ink, seen)
                    }
                }
            }
        }
    }
}


/** Each feather's lane from the middle and its size, in hundredths of the mark. */
private val FEATHERS = listOf(0f to 0.42f, -18f to 0.3f, 16f to 0.35f)

private const val FALL_MILLIS = 2400
