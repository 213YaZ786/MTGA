package com.mtga.app

import android.os.Bundle
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
            MtgaTheme {
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
