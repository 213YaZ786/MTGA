package com.mtga.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/**
 * Material 3 with the expressive motion scheme.
 *
 * Every Material component reads its animation timings from the theme's
 * motion scheme. The standard one is linear and utilitarian, the expressive
 * one is spring based, so buttons, sheets, dialogs, switches, the rail and
 * the pull to refresh all gain physical motion from this one parameter
 * rather than from animation code scattered through the screens.
 *
 * On material3 1.4.0, the version Compose BOM 2026.08.00 ships, this is still
 * @ExperimentalMaterial3ExpressiveApi. It was graduated in 1.5.0-alpha15,
 * which is not in our BOM yet. The opt-in is the whole cost, and the rollback
 * is one line: swap MaterialExpressiveTheme back for MaterialTheme and drop
 * the motionScheme argument. Colours and typography are ours either way, so
 * nothing else moves.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MtgaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    pureBlack: Boolean = false,
    textScale: Float = 1f,
    display: DisplayPrefs = DisplayPrefs(),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colors = when {
        // minSdk is 31, so dynamic colour is always available. The flag exists
        // so the user can turn it off in Settings.
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> MtgaDarkColors
        else -> MtgaLightColors
    }.let { scheme ->
        if (darkTheme && pureBlack) {
            scheme.copy(
                background = Color.Black,
                surface = Color.Black,
                surfaceContainerLowest = Color.Black
            )
        } else {
            scheme
        }
    }

    val typography = remember(textScale) { MtgaTypography.scaled(textScale) }

    MaterialExpressiveTheme(
        colorScheme = colors,
        motionScheme = MotionScheme.expressive(),
        typography = typography
    ) {
        CompositionLocalProvider(LocalDisplayPrefs provides display, content = content)
    }
}
