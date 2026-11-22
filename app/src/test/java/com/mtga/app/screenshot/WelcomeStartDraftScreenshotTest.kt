package com.mtga.app.screenshot

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mtga.app.feature.settings.SettingsIcons
import com.mtga.app.ui.component.LoadingMark
import com.mtga.app.ui.component.SetupTile
import com.mtga.app.ui.component.SetupTileGrid
import com.mtga.app.ui.glass.LocalGlass
import com.mtga.app.ui.glass.glassZone
import com.mtga.app.ui.icon.MtgaIcons
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * DRAFT for the user's yes (requests.md point 26), nothing of app/src/main changes: WelcomeGuideDraftScreenshotTest's
 * layout with a bigger, more coloured Start (StartDraftButton) in place of BoldButton(filled = true).
 * Layout ported from https://github.com/SvenTiigi/WhatsNewKit/blob/main/Sources/View/WhatsNewView.swift#body
 * (carried from WelcomeGuideDraftScreenshotTest.kt#WelcomeGuideDraft).
 * not ported: WhatsNewView's footer band, secondary action sheet, version store, title and feature helper views
 * (the app's title and SetupTileGrid take their place).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = android.app.Application::class, sdk = [36])
class WelcomeStartDraftScreenshotTest : ScreenshotBase() {

    private fun tile(
        id: String, name: String, icon: ImageVector, state: String, summary: String,
        done: Boolean, error: Boolean = false, onClick: () -> Unit = {}
    ) = SetupTile(
        id = id, name = name, title = name, icon = icon, state = state, summary = summary, done = done,
        required = false, error = error, onClick = onClick
    )

    private val tiles = listOf(
        tile("media", "Save media", MtgaIcons.Download, "Images and videos",
            "Images and videos you save go to the Pictures and Movies folders. Tap to choose.", done = true),
        tile("source", "Sources", SettingsIcons.Dns, "4 of 5",
            "The servers the reader tries for each post. Tap to choose.", done = true),
        tile("notify", "New posts", MtgaIcons.Bell, "Off", "New posts of the accounts you follow. Tap to turn on.", done = false),
        tile("links", "X links", SettingsIcons.Link, "Off",
            "x.com and twitter.com links tapped anywhere open in MTGA once Android's \"Open supported links\" is on for it. " +
                "Tap to open that page.", done = false)
    )

    @Test
    fun draft() = capture("welcome_start_draft") { WelcomeStartDraft(tiles, onStart = {}) }

    @Test
    fun edge() = captureEdge("welcome_start_draft") { WelcomeStartDraft(tiles, onStart = {}) }
}

/**
 * ButtonMediumTokens: ContainerHeight 56 dp, shape round (CircleShape here, as every button of the apps).
 * https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/tokens/ButtonMediumTokens.kt#ButtonMediumTokens
 * not ported: ContainerShapeSquare, PressedContainerShape, Selected shapes, LeadingSpace/TrailingSpace (toggle and icon
 * buttons, a 24 dp inset is used), icon sizes (no icon).
 */
private val StartContainerHeight: Dp = 56.dp

/**
 * Button.kt's textStyleFor: the label style by the button's height, titleMedium from 56 dp up.
 * https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/Button.kt#textStyleFor
 */
@Composable
private fun textStyleFor(buttonHeight: Dp): TextStyle {
    val mediumHeight = 56.dp
    val largeHeight = 96.dp
    val xLargeHeight = 136.dp
    return when {
        buttonHeight < mediumHeight -> MaterialTheme.typography.labelLarge
        buttonHeight < largeHeight -> MaterialTheme.typography.titleMedium
        buttonHeight < xLargeHeight -> MaterialTheme.typography.headlineSmall
        else -> MaterialTheme.typography.headlineLarge
    }
}

/**
 * FilledButtonTokens: ContainerColor Primary, LabelTextColor OnPrimary. In glass the primary is kept at 0.85 alpha over
 * the pane so the rim shows; the pane is BoldButton's glass branch (glassZone, lens = 1f, CircleShape).
 * https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/tokens/FilledButtonTokens.kt#FilledButtonTokens
 * not ported: disabled, focused, hovered, pressed and toggle colours, elevations (a draft, nothing is pressed).
 */
@Composable
private fun StartDraftButton(onClick: () -> Unit, modifier: Modifier = Modifier, label: String) {
    val glass = LocalGlass.current
    val shape = CircleShape
    val colors = MaterialTheme.colorScheme
    val look = if (glass != null) {
        Modifier.glassZone(shape, glass, lens = 1f).background(colors.primary.copy(alpha = 0.85f), shape)
    } else {
        Modifier.background(colors.primary, shape)
    }
    Box(
        modifier = modifier.heightIn(min = StartContainerHeight).then(look)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, style = textStyleFor(StartContainerHeight), color = colors.onPrimary, textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp))
    }
}

/**
 * WhatsNewView's body: a ZStack of the scrolling content and the footer pinned at the bottom; the scroll ends with a
 * bottom content inset so nothing ends under the footer; the content block is centred when shorter than the screen.
 * https://github.com/SvenTiigi/WhatsNewKit/blob/main/Sources/View/WhatsNewView.swift#body
 */
@Composable
private fun WelcomeStartDraft(tiles: List<SetupTile>, onStart: () -> Unit) {
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
                Spacer(Modifier.height(104.dp))
                Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
            }
        }
        // Footer, pinned at the bottom, no band behind it.
        Column(Modifier.align(Alignment.BottomCenter).widthIn(max = 720.dp).fillMaxWidth().padding(horizontal = 24.dp)) {
            StartDraftButton(onClick = onStart, modifier = Modifier.fillMaxWidth(), label = "Start")
            Spacer(Modifier.height(16.dp))
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
}
