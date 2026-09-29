package com.mtga.app.ui.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
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

    val transition = rememberInfiniteTransition(label = "falling feathers")
    val clock by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(FALL_MILLIS, easing = LinearEasing), RepeatMode.Restart),
        label = "fall"
    )
    // A gesture lets the feathers in a third of a fall at most.
    val time = if (running) clock else progress / 3f

    Canvas(modifier.size(size).clipToBounds()) {
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
