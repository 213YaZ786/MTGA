package com.mtga.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/**
 * Material 3, with our own colours and typography.
 *
 * The expressive motion scheme was tried here in 2.7.0 and reverted in 2.7.6.
 * On material3 1.4.0, the version Compose BOM 2026.08.00 ships, the whole
 * expressive surface is marked internal rather than merely experimental, so
 * MaterialExpressiveTheme, MotionScheme and their opt-in annotation cannot be
 * referenced at all: no opt-in makes an internal declaration visible. It was
 * graduated in 1.5.0-alpha15, which this BOM does not contain. Revisit when
 * material3 1.5.0 reaches stable, not before.
 */
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

    MaterialTheme(colorScheme = colors, typography = typography) {
        CompositionLocalProvider(LocalDisplayPrefs provides display, content = content)
    }
}
