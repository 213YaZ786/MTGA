package com.mtga.app.screenshot

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.materialkolor.blend.Blend
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.dynamiccolor.DynamicColor
import com.materialkolor.dynamiccolor.MaterialDynamicColors
import com.materialkolor.hct.Hct
import com.materialkolor.scheme.SchemeTonalSpot
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * DRAFT for the user's yes (requests.md point 27), nothing of app/src/main changes: with a brown Material You palette on
 * the phone, the four places that draw a fixed colour, today ("Now") and from the palette's roles ("Proposed").
 * The palette is Android's default dynamic scheme (tonal spot) from the seed 0xFF795548 (Material's Brown 500).
 * Values copied from the app: STAR_GOLD (PostActions.kt), SparkButton's defaults (SparkButton.kt), levelColor
 * (LinkSheet.kt), InstanceRowDefaults and StatusDot (DiagnosticsScreen.kt).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = android.app.Application::class, sdk = [36])
class PaletteRolesDraftScreenshotTest : ScreenshotBase() {

    @Test
    fun draft() = capture("palette_roles_draft") { Roles() }

    @Test
    fun edges() = captureEdge("palette_roles_draft") { Roles() }

    @Composable
    private fun Roles() {
        val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
        val scheme = brownScheme(dark)
        MaterialTheme(colorScheme = scheme) {
            Box(Modifier.fillMaxSize().background(scheme.background).padding(16.dp), contentAlignment = Alignment.Center) {
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    listOf("Now", "Roles", "Harmonized").forEach { title ->
                        Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                            Text(title, color = scheme.onBackground, style = MaterialTheme.typography.titleMedium)
                            Rows(title, scheme)
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun Rows(column: String, scheme: ColorScheme) {
        val now = column == "Now"
        val harmonized = column == "Harmonized"
        // Like's star: tertiary in Roles and Harmonized
        Image(
            imageVector = star,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            colorFilter = ColorFilter.tint(if (now) Color(0xFFFFB300) else scheme.tertiary)
        )
        // Like's sparks
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Dot(if (now) Color(0xFFFFC107) else scheme.tertiary, 20)
            Dot(if (now) Color(0xFFFF5722) else scheme.primary, 20)
        }
        // The link sheet's warning lines
        val caution = lerp(scheme.error, Color(0xFFE8890C), 0.4f)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Caution", color = when { now -> caution; harmonized -> harmonize(caution, scheme); else -> scheme.tertiary })
            Text("Danger", color = scheme.error)
            Text("Note", color = scheme.outline)
        }
        // Troubleshooting's status dots
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val fixed = listOf(Color(0xFF4CAF50), Color(0xFFFFB300), Color(0xFFE53935))
            val dots = when {
                now -> fixed + Color(0xFF9E9E9E)
                harmonized -> fixed.map { harmonize(it, scheme) } + scheme.outline
                else -> listOf(scheme.primary, scheme.tertiary, scheme.error, scheme.outline)
            }
            listOf("Healthy", "Slow", "Down", "Unknown").forEachIndexed { i, name ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Dot(dots[i], 12)
                    Text(name, color = scheme.onBackground)
                }
            }
        }
    }

    // MaterialColors.harmonizeWithPrimary: the design colour's hue moved toward the primary's, through Blend.harmonize
    // https://github.com/material-foundation/material-color-utilities/blob/main/java/blend/Blend.java#harmonize
    // https://github.com/material-components/material-components-android/blob/master/lib/java/com/google/android/material/color/MaterialColors.java#harmonizeWithPrimary
    private fun harmonize(design: Color, scheme: ColorScheme) =
        Color(Blend.harmonize(design.toArgb(), scheme.primary.toArgb()))

    @Composable
    private fun Dot(color: Color, size: Int) = Box(Modifier.size(size.dp).background(color, CircleShape))

    // PostActions.kt ActionIcons.star (private there): the same path.
    private val star: ImageVector = ImageVector.Builder("Star", 24.dp, 24.dp, 24f, 24f).apply {
        addPath(
            pathData = PathParser().parsePathString("M12,17.27L18.18,21l-1.64,-7.03L22,9.24l-7.19,-0.61L12,2 9.19,8.63 2,9.24l5.46,4.73L5.82,21z").toNodes(),
            fill = SolidColor(Color.Black)
        )
    }.build()

    // Port of SchemeTonalSpot, each role read through MaterialDynamicColors as Magisk's dynamicColorScheme does
    // https://github.com/material-foundation/material-color-utilities/blob/main/java/scheme/SchemeTonalSpot.java#SchemeTonalSpot
    // https://github.com/material-foundation/material-color-utilities/blob/main/java/dynamiccolor/MaterialDynamicColors.java#MaterialDynamicColors
    // https://github.com/topjohnwu/Magisk/blob/master/app/apk/src/main/java/com/topjohnwu/magisk/ui/MagiskTheme.kt#dynamicColorScheme
    // The 2021 spec is named as GutapK's accentScheme does, materialkolor 6 defaulting to the 2025 one.
    private fun brownScheme(dark: Boolean): ColorScheme {
        val scheme = SchemeTonalSpot(Hct.fromInt(0xFF795548.toInt()), dark, 0.0, ColorSpec.SpecVersion.SPEC_2021)
        val dyn = MaterialDynamicColors()
        fun c(role: DynamicColor) = Color(role.getArgb(scheme))
        return (if (dark) darkColorScheme() else lightColorScheme()).copy(
            primary = c(dyn.primary),
            onPrimary = c(dyn.onPrimary),
            primaryContainer = c(dyn.primaryContainer),
            onPrimaryContainer = c(dyn.onPrimaryContainer),
            inversePrimary = c(dyn.inversePrimary),
            secondary = c(dyn.secondary),
            onSecondary = c(dyn.onSecondary),
            secondaryContainer = c(dyn.secondaryContainer),
            onSecondaryContainer = c(dyn.onSecondaryContainer),
            tertiary = c(dyn.tertiary),
            onTertiary = c(dyn.onTertiary),
            tertiaryContainer = c(dyn.tertiaryContainer),
            onTertiaryContainer = c(dyn.onTertiaryContainer),
            background = c(dyn.background),
            onBackground = c(dyn.onBackground),
            surface = c(dyn.surface),
            onSurface = c(dyn.onSurface),
            surfaceVariant = c(dyn.surfaceVariant),
            onSurfaceVariant = c(dyn.onSurfaceVariant),
            surfaceTint = c(dyn.surfaceTint),
            inverseSurface = c(dyn.inverseSurface),
            inverseOnSurface = c(dyn.inverseOnSurface),
            error = c(dyn.error),
            onError = c(dyn.onError),
            errorContainer = c(dyn.errorContainer),
            onErrorContainer = c(dyn.onErrorContainer),
            outline = c(dyn.outline),
            outlineVariant = c(dyn.outlineVariant),
            scrim = c(dyn.scrim),
            surfaceBright = c(dyn.surfaceBright),
            surfaceDim = c(dyn.surfaceDim),
            surfaceContainer = c(dyn.surfaceContainer),
            surfaceContainerHigh = c(dyn.surfaceContainerHigh),
            surfaceContainerHighest = c(dyn.surfaceContainerHighest),
            surfaceContainerLow = c(dyn.surfaceContainerLow),
            surfaceContainerLowest = c(dyn.surfaceContainerLowest)
        )
    }
}
