package com.mtga.app.screenshot

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mtga.app.ui.component.BoldButton
import com.mtga.app.feature.settings.SettingsIcons
import com.mtga.app.ui.component.FeatherArt
import com.mtga.app.ui.component.FeatherInk
import com.mtga.app.ui.component.drawFeather
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.runtime.remember
import kotlin.math.cos
import kotlin.math.sin
import com.mtga.app.ui.component.SetupTile
import com.mtga.app.ui.component.SetupTileGrid
import com.mtga.app.ui.icon.MtgaIcons
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * DRAFT for the user's yes (requests.md point 26), nothing of app/src/main changes: the guide's first screen after
 * WhatsNewKit's WhatsNewView: the content in a vertical scroll (the mark as the hero, the title, the tiles), the
 * primary action pinned at the bottom of the screen with a bottom inset in the scroll so the last row is never under it.
 * https://github.com/SvenTiigi/WhatsNewKit/blob/main/Sources/View/WhatsNewView.swift#body
 * not ported: the footer's UIVisualEffectView band (no bar at the bottom in our apps), the secondary action sheet
 * (.sheet(item:)), the version store (onDisappear save), the title and feature helper views (WhatsNewView.title,
 * WhatsNewView.feature: the app's own title and SetupTileGrid take their place).
 * The tiles are WelcomeModelDraftScreenshotTest's draftRun.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = android.app.Application::class, sdk = [36])
class WelcomeHeroDraftScreenshotTest : ScreenshotBase() {

    private fun tile(
        id: String, name: String, icon: androidx.compose.ui.graphics.vector.ImageVector, state: String,
        done: Boolean, required: Boolean
    ) = SetupTile(
        id = id, name = name, title = name, icon = icon, state = state, summary = "", done = done,
        required = required, onClick = {}
    )

    private val tiles = listOf(
        tile("media", "Save media", MtgaIcons.Download, "Images and videos", done = true, required = false),
        tile("source", "Sources", SettingsIcons.Dns, "4 of 5", done = true, required = false),
        tile("notify", "New posts", MtgaIcons.Bell, "Off", done = false, required = false)
    )

    @Test
    fun draft() = capture("welcome_hero_draft") { WelcomeHeroDraft(tiles, onStart = {}) }

    @Test
    fun edge() = captureEdge("welcome_hero_draft") { WelcomeHeroDraft(tiles, onStart = {}) }
}

/**
 * The ring of PullRingDraftScreenshotTest, copied here: 200 dp, no centre text, the feathers larger (one spans 40 % of the
 * ring's radius). At progress 1 every slot is in full colour: it stands for the ring turning slowly in the app.
 * Ported from PullRingDraftScreenshotTest.kt#FeatherRing and
 * https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/ProgressIndicator.kt#CircularProgressIndicator
 */
@Composable
private fun FeatherRing(progress: () -> Float) {
    val coercedProgress = { progress().coerceIn(0f, 1f) }
    val accent = MaterialTheme.colorScheme.primary
    val ink = remember(accent) {
        FeatherInk(
            base = lerp(accent, Color.White, 0.72f), middle = lerp(accent, Color.White, 0.38f),
            tip = accent, shaft = lerp(accent, Color.White, 0.3f)
        )
    }
    val track = MaterialTheme.colorScheme.outlineVariant
    val trackInk = remember(track) { FeatherInk(track, track, track, track) }
    val art = remember { FeatherArt() }
    Canvas(
        Modifier
            .semantics(mergeDescendants = true) {
                progressBarRangeInfo = ProgressBarRangeInfo(coercedProgress().takeUnless { it.isNaN() } ?: 0f, 0f..1f)
            }
            .size(200.dp)
    ) {
        // Start at 12 o'clock
        val startAngle = 270f
        val sweep = coercedProgress() * 360f
        val featherLength = size.minDimension / 2f * 0.4f
        val unit = featherLength / 72f // one unit of the feather's 108 grid
        val radius = size.minDimension / 2f - featherLength / 2f
        val c = art.centre
        val slots = 16
        for (i in 0 until slots) {
            val angle = i * 360f / slots
            val on = angle < sweep || sweep >= 360f
            val rad = Math.toRadians((startAngle + angle).toDouble())
            val x = size.width / 2f + radius * cos(rad).toFloat()
            val y = size.height / 2f + radius * sin(rad).toFloat()
            translate(x - c.x, y - c.y) {
                rotate(angle - 45f, pivot = c) {
                    scale(unit, pivot = c) {
                        drawFeather(art, if (on) ink else trackInk, if (on) 1f else 0.35f)
                    }
                }
            }
        }
    }
}

/**
 * WhatsNewView's body: a ZStack of the scrolling content and the footer pinned at the bottom (Spacer above it); the
 * scroll ends with a bottom content inset (Color.clear padded by scrollViewBottomContentInset) so nothing ends under
 * the footer. The content block is centred when shorter than the screen.
 * https://github.com/SvenTiigi/WhatsNewKit/blob/main/Sources/View/WhatsNewView.swift#body
 */
@Composable
private fun WelcomeHeroDraft(tiles: List<SetupTile>, onStart: () -> Unit) {
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
                FeatherRing(progress = { 1f })
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
