package com.mtga.app.screenshot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mtga.app.feature.settings.SettingsIcons
import com.mtga.app.ui.component.BoldButton
import com.mtga.app.ui.component.LoadingMark
import com.mtga.app.ui.component.SetupTile
import com.mtga.app.ui.component.SetupTileGrid
import com.mtga.app.ui.icon.MtgaIcons
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * DRAFT for the user's yes (requests.md point 26), nothing of app/src/main changes: the guide of the first launch,
 * WelcomeHeroDraftScreenshotTest's layout with the guide's own mark (LoadingMark, 160 dp, running, as WelcomeScreen.kt
 * draws it), every refusal on its own tile (error flag), no message line, Start full width at the bottom.
 * Layout ported from https://github.com/SvenTiigi/WhatsNewKit/blob/main/Sources/View/WhatsNewView.swift#body
 * (carried from WelcomeHeroDraftScreenshotTest.kt#WelcomeHeroDraft).
 * not ported: FeatherRing (replaced by LoadingMark), WhatsNewView's footer band, secondary action sheet, version store,
 * title and feature helper views (the app's title and SetupTileGrid take their place).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = android.app.Application::class, sdk = [36])
class WelcomeGuideDraftScreenshotTest : ScreenshotBase() {

    private fun tile(
        id: String, name: String, icon: ImageVector, state: String, summary: String,
        done: Boolean, error: Boolean = false, onClick: () -> Unit = {}
    ) = SetupTile(
        id = id, name = name, title = name, icon = icon, state = state, summary = summary, done = done,
        required = false, error = error, onClick = onClick
    )

    private val sources = tile(
        "source", "Sources", SettingsIcons.Dns, "4 of 5",
        "The servers the reader tries for each post. Tap to choose.", done = true
    )
    private val links = tile(
        "links", "X links", SettingsIcons.Link, "Off",
        "x.com and twitter.com links tapped anywhere open in MTGA once Android's \"Open supported links\" is on for it. " +
            "Tap to open that page.", done = false
    )

    /** Store installed, nothing refused. */
    private val tilesDraft = listOf(
        tile("media", "Save media", MtgaIcons.Download, "Images and videos",
            "Images and videos you save go to the Pictures and Movies folders. Tap to choose.", done = true),
        sources,
        tile("notify", "New posts", MtgaIcons.Bell, "Off", "New posts of the accounts you follow. Tap to turn on.", done = false),
        links
    )

    /**
     * Store missing, the reader refused things. The blocked New posts tile's tap opens Android's notification page of
     * the app: Settings.ACTION_APP_NOTIFICATION_SETTINGS with EXTRA_APP_PACKAGE, as
     * https://github.com/AntennaPod/AntennaPod/blob/develop/app/src/main/java/de/danoeh/antennapod/ui/screen/preferences/PreferenceActivity.java#openScreen
     * opens a settings screen. Nothing runs in a picture: the tap is empty here.
     */
    private val tilesRefused = listOf(
        tile("media", "Save media", MtgaIcons.Download, "Saved, no progress bar",
            "Images and videos you save go to the Pictures and Movies folders. Tap to choose.", done = true),
        sources,
        tile("notify", "New posts", MtgaIcons.Bell, "Notifications blocked",
            "Android blocks this app's notifications. Tap to open its notification page.", done = false, error = true),
        links,
        tile("update", "Updates", SettingsIcons.Update, "Installs not allowed",
            "Android does not let MTGA install updates. Tap to open its install page.", done = false, error = true)
    )

    @Test
    fun draft() = capture("welcome_guide_draft") { WelcomeGuideDraft(tilesDraft, onStart = {}) }

    @Test
    fun refused() = capture("welcome_guide_refused_draft") { WelcomeGuideDraft(tilesRefused, onStart = {}) }

    @Test
    fun edge() = captureEdge("welcome_guide_refused_draft") { WelcomeGuideDraft(tilesRefused, onStart = {}) }
}

/**
 * WhatsNewView's body: a ZStack of the scrolling content and the footer pinned at the bottom; the scroll ends with a
 * bottom content inset so nothing ends under the footer; the content block is centred when shorter than the screen.
 * https://github.com/SvenTiigi/WhatsNewKit/blob/main/Sources/View/WhatsNewView.swift#body
 */
@Composable
private fun WelcomeGuideDraft(tiles: List<SetupTile>, onStart: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val screen = maxHeight
        // Content ScrollView
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .widthIn(max = 720.dp).fillMaxWidth().heightIn(min = screen)
                    .windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 8.dp)
            ) {
                LoadingMark(size = 160.dp)
                Spacer(Modifier.height(16.dp))
                Text(
                    "X reader",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(Modifier.height(24.dp))
                SetupTileGrid(tiles) {}
                // ScrollView bottom content inset: the button, its 16 dp and the navigation bar.
                Spacer(Modifier.height(88.dp))
                Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
            }
        }
        // Footer, pinned at the bottom, no band behind it.
        Column(Modifier.align(Alignment.BottomCenter).widthIn(max = 720.dp).fillMaxWidth().padding(horizontal = 24.dp)) {
            BoldButton(filled = true, onClick = onStart, modifier = Modifier.fillMaxWidth()) { Text("Start") }
            Spacer(Modifier.height(16.dp))
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
}
