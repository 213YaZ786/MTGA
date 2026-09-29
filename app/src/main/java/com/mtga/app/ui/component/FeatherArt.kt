package com.mtga.app.ui.component

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.PathParser

/**
 * The feather of the launcher icon, drawn the same way in the app: lobes lit
 * like cushions over a gradient from a light base to a deep tip, a light from
 * the upper left, and a tapering shaft set in a faint groove. On the icon's
 * 108 grid; res/drawable/ic_launcher_foreground.xml is made from the same data.
 */
internal class FeatherArt {
    val vane = path(VANE)
    val lobes = LOBES.map { path(it.data) to it }
    val notches = NOTCHES.map(::path)
    val groove = path(GROOVE)
    val shaft = path(SHAFT)
    val centre: Offset = vane.getBounds().center

    private fun path(data: String): Path = PathParser().parsePathString(data).toPath()
}

internal class Lobe(val data: String, val peak: Offset, val base: Offset, val lit: Boolean)

/** The feather's colours: a light base, a middle, the tip, and the shaft. */
internal class FeatherInk(val base: Color, val middle: Color, val tip: Color, val shaft: Color)

internal fun DrawScope.drawFeather(art: FeatherArt, ink: FeatherInk, alpha: Float) {
    drawPath(art.vane, Brush.linearGradient(0.15f to ink.base, 0.6f to ink.middle, 1f to ink.tip, start = AXIS_START, end = AXIS_END), alpha = alpha)
    art.lobes.forEach { (path, lobe) ->
        val light = if (lobe.lit) 0.42f else 0.24f
        drawPath(
            path,
            Brush.linearGradient(
                0.14f to Color.White.copy(alpha = light), 0.5f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.16f),
                start = lobe.peak, end = lobe.base
            ),
            alpha = alpha
        )
    }
    drawPath(
        art.vane,
        Brush.linearGradient(
            0f to Color.White.copy(alpha = 0.18f), 0.5f to Color.Transparent, 0.6f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.2f),
            start = LIGHT_START, end = LIGHT_END
        ),
        alpha = alpha
    )
    art.notches.forEach { drawPath(it, Color.Black.copy(alpha = 0.12f * alpha), style = Stroke(width = 0.8f, cap = StrokeCap.Round)) }
    drawPath(art.groove, Color.Black.copy(alpha = 0.08f * alpha))
    drawPath(art.shaft, Brush.linearGradient(listOf(ink.shaft, ink.tip), start = AXIS_START, end = AXIS_END), alpha = alpha)
    drawPath(
        art.shaft,
        Brush.linearGradient(
            0f to Color.Black.copy(alpha = 0.12f), 0.38f to Color.White.copy(alpha = 0.45f), 0.62f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.18f),
            start = TUBE_START, end = TUBE_END
        ),
        alpha = alpha
    )
}

private val AXIS_START = Offset(30.00f, 80.00f)
private val AXIS_END = Offset(79.00f, 28.00f)
private val LIGHT_START = Offset(42.36f, 43.03f)
private val LIGHT_END = Offset(64.92f, 64.29f)
private val TUBE_START = Offset(52.84f, 52.90f)
private val TUBE_END = Offset(55.16f, 55.10f)

private const val VANE = "M36.69,71.58 C32.99,61.83 28.77,57.44 31.68,53.93 C34.45,50.69 34.56,48.3 35.22,49.33 C38.32,53.92 39.7,56.89 40,58.01 C38.3,50.15 37.24,44.56 40.33,41.21 C43.27,38.14 45.23,37.48 45.84,38.48 C48.79,42.93 49.69,45.44 50,46.57 C49.21,39.56 50.11,35.83 53.39,32.65 C56.49,29.73 59.48,30.05 59.85,30.82 C61.69,34.22 61.77,35.97 61.89,36.91 C62.86,31.57 66.33,30.24 69.72,27.19 C73.23,24.23 74.76,25.05 79,28 C81.2,31.64 82.72,34.01 77.71,38.68 C72.65,43.3 70.82,46.37 63.5,48.86 C64.49,48.96 66.53,49.22 70.21,51.01 C71.04,51.37 70.73,53.59 67.71,56.58 C64.52,59.83 59.23,61.53 52.4,61.35 C53.63,61.26 56.54,61.5 61.11,63.3 C62.15,63.65 60.03,65.4 55.79,70.17 C51.36,75.39 47.28,72.17 36.69,71.58 Z"
private const val GROOVE = "M39.12,68.06 L39.91,66.51 L40.72,64.99 L41.55,63.49 L42.4,62.01 L43.44,60.71 L44.57,59.49 L45.7,58.27 L46.84,57.07 L47.99,55.87 L49.15,54.67 L50.31,53.49 L51.48,52.3 L52.65,51.13 L53.83,49.96 L55.02,48.8 L56.22,47.64 L57.42,46.49 L58.63,45.35 L59.84,44.21 L61.07,43.08 L62.29,41.95 L63.52,40.83 L64.76,39.71 L66,38.6 L67.25,37.49 L68.49,36.39 L69.75,35.28 L71,34.18 L72.25,33.08 L73.51,31.98 L74.76,30.88 L75.74,31.8 L74.51,32.92 L73.29,34.05 L72.07,35.19 L70.85,36.33 L69.64,37.47 L68.44,38.62 L67.24,39.77 L66.05,40.93 L64.87,42.1 L63.69,43.27 L62.52,44.46 L61.36,45.64 L60.21,46.84 L59.06,48.04 L57.93,49.25 L56.8,50.47 L55.68,51.7 L54.57,52.93 L53.46,54.18 L52.37,55.43 L51.28,56.69 L50.21,57.95 L49.14,59.23 L48.08,60.51 L47.02,61.8 L45.98,63.1 L44.85,64.32 L43.56,65.39 L42.25,66.44 L40.93,67.48 L39.6,68.51 Z"
private const val SHAFT = "M28.87,78.94 L29.95,77.67 L31.02,76.4 L32.1,75.13 L33.17,73.86 L34.25,72.59 L35.33,71.33 L36.41,70.06 L37.5,68.8 L38.58,67.55 L39.68,66.29 L40.77,65.05 L41.88,63.8 L42.98,62.56 L44.1,61.33 L45.22,60.1 L46.34,58.88 L47.47,57.66 L48.61,56.45 L49.76,55.25 L50.91,54.05 L52.07,52.86 L53.23,51.68 L54.4,50.5 L55.58,49.33 L56.77,48.16 L57.96,47 L59.16,45.85 L60.37,44.7 L61.58,43.56 L62.79,42.43 L64.01,41.29 L65.24,40.17 L66.47,39.04 L67.71,37.93 L68.95,36.81 L70.19,35.7 L71.43,34.59 L72.67,33.48 L73.92,32.37 L75.17,31.26 L75.34,31.42 L74.1,32.54 L72.87,33.66 L71.64,34.78 L70.41,35.91 L69.19,37.04 L67.98,38.18 L66.77,39.33 L65.57,40.48 L64.38,41.64 L63.19,42.8 L62.01,43.97 L60.84,45.15 L59.68,46.34 L58.52,47.53 L57.38,48.73 L56.24,49.94 L55.11,51.16 L53.99,52.39 L52.87,53.62 L51.77,54.86 L50.67,56.11 L49.59,57.37 L48.51,58.64 L47.44,59.91 L46.37,61.19 L45.32,62.48 L44.27,63.77 L43.23,65.07 L42.19,66.38 L41.17,67.7 L40.14,69.02 L39.13,70.34 L38.11,71.67 L37.11,73 L36.1,74.34 L35.1,75.68 L34.11,77.02 L33.11,78.37 L32.12,79.71 L31.13,81.06 Q29.25,80.95 28.87,78.94 Z"

private val LOBES = listOf(
        Lobe("M36.69,71.58 C32.99,61.83 28.77,57.44 31.68,53.93 C34.45,50.69 34.56,48.3 35.22,49.33 C38.32,53.92 39.7,56.89 40,58.01 L44.45,62.2 L36.69,71.58 Z", Offset(31.68f, 53.93f), Offset(42.68f, 64.29f), lit = true),
        Lobe("M40,58.01 C38.3,50.15 37.24,44.56 40.33,41.21 C43.27,38.14 45.23,37.48 45.84,38.48 C48.79,42.93 49.69,45.44 50,46.57 L54.64,50.95 L44.45,62.2 Z", Offset(40.33f, 41.21f), Offset(52.76f, 52.93f), lit = true),
        Lobe("M50,46.57 C49.21,39.56 50.11,35.83 53.39,32.65 C56.49,29.73 59.48,30.05 59.85,30.82 C61.69,34.22 61.77,35.97 61.89,36.91 L65.46,40.28 L54.64,50.95 Z", Offset(53.39f, 32.65f), Offset(63.47f, 42.16f), lit = true),
        Lobe("M61.89,36.91 C62.86,31.57 66.33,30.24 69.72,27.19 C73.23,24.23 74.76,25.05 79,28 L65.46,40.28 Z", Offset(69.72f, 27.19f), Offset(74.68f, 31.85f), lit = true),
        Lobe("M36.69,71.58 C47.28,72.17 51.36,75.39 55.79,70.17 C60.03,65.4 62.15,63.65 61.11,63.3 C56.54,61.5 53.63,61.26 52.4,61.35 L48.45,57.63 L36.69,71.58 Z", Offset(55.79f, 70.17f), Offset(45.74f, 60.71f), lit = false),
        Lobe("M52.4,61.35 C59.23,61.53 64.52,59.83 67.71,56.58 C70.73,53.59 71.04,51.37 70.21,51.01 C66.53,49.22 64.49,48.96 63.5,48.86 L59.98,45.54 L48.45,57.63 Z", Offset(67.71f, 56.58f), Offset(58.04f, 47.47f), lit = false),
        Lobe("M63.5,48.86 C70.82,46.37 72.65,43.3 77.71,38.68 C82.72,34.01 81.2,31.64 79,28 L59.98,45.54 Z", Offset(77.71f, 38.68f), Offset(72.52f, 33.79f), lit = false)
)

private val NOTCHES = listOf(
        "M35.22,49.33 C38.32,53.92 39.7,56.89 40,58.01",
        "M45.84,38.48 C48.79,42.93 49.69,45.44 50,46.57",
        "M59.85,30.82 C61.69,34.22 61.77,35.97 61.89,36.91",
        "M61.11,63.3 C56.54,61.5 53.63,61.26 52.4,61.35",
        "M70.21,51.01 C66.53,49.22 64.49,48.96 63.5,48.86"
)
