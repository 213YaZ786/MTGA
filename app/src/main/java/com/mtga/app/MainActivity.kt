package com.mtga.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.mtga.app.data.settings.SettingsStore
import com.mtga.app.data.settings.ThemeMode
import org.koin.compose.koinInject
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.mtga.app.feature.challenge.ChallengeBackstage
import com.mtga.app.feature.challenge.ChallengeOverlay
import com.mtga.app.navigation.MtgaApp
import com.mtga.app.ui.theme.MtgaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val store: SettingsStore = koinInject()
            val settings by store.settings.collectAsState()
            val dark = when (settings.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            // Status and navigation bar icons follow the app's theme, not only
            // the system's, so a forced light theme keeps dark icons.
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark }
                )
                onDispose { }
            }

            MtgaTheme(darkTheme = dark, pureBlack = settings.pureBlack) {
                Box(Modifier.fillMaxSize()) {
                    // Order matters. The check runs underneath the app, which
                    // hides it and takes every touch, and its status sits on top.
                    ChallengeBackstage()
                    MtgaApp()
                    ChallengeOverlay()
                }
            }
        }
    }
}
