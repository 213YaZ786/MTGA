package com.mtga.app

import android.content.Intent
import android.graphics.Color
import android.widget.Toast
import com.mtga.app.core.link.LinkRouter
import org.koin.android.ext.android.inject
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
import com.mtga.app.ui.theme.DisplayPrefs
import com.mtga.app.ui.theme.MtgaTheme

class MainActivity : ComponentActivity() {

    private val links: LinkRouter by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Only on a real launch. A recreated activity already showed its link.
        if (savedInstanceState == null) receive(intent)
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

            MtgaTheme(
                darkTheme = dark,
                pureBlack = settings.pureBlack,
                textScale = settings.textScale,
                display = DisplayPrefs(
                    compact = settings.compactPosts,
                    squareAvatars = settings.squareAvatars
                )
            ) {
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

    /** singleTask: a link tapped elsewhere while MTGA runs arrives here. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        receive(intent)
    }

    private fun receive(intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_VIEW && action != Intent.ACTION_SEND) return
        if (links.offer(intent)) return
        val url = intent.dataString
        if (action == Intent.ACTION_VIEW && url != null) {
            // An X page MTGA cannot show, a search or a list. Hand it on
            // rather than leaving the reader stuck here.
            LinkRouter.openOutside(this, url)
        } else {
            Toast.makeText(this, "No X profile or post in what was shared", Toast.LENGTH_SHORT).show()
        }
    }
}
