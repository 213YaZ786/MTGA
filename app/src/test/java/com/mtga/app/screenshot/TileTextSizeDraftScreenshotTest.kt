package com.mtga.app.screenshot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mtga.app.feature.settings.SettingsIcons
import com.mtga.app.ui.component.BoldButton
import com.mtga.app.ui.component.LoadingMark
import com.mtga.app.ui.component.SettingsSectionTitle
import com.mtga.app.ui.component.ZoneSurface
import com.mtga.app.ui.component.rememberGroupLabelSize
import com.mtga.app.ui.icon.MtgaIcons
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * DRAFT for the user's yes (requests.md point 45), nothing of app/src/main changes: one font size in the tiles of the
 * guide and of Settings. The state line (TileFace's second line) takes the name's style at the name's size (both shrink
 * together), told apart by colour only: onSurfaceVariant, the error colour when the tile says so; the name keeps the
 * content colour. The grid and the face are the app's SetupTileGrid / SettingsTileGrid / TileFace copied below with
 * only the state's style and colour changed; the guide's layout is WelcomeGuideDraftScreenshotTest's.
 * labelLarge for both lines: Material 3's type scale
 * https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/Typography.kt#Typography
 * Pictures: tile_text_size_draft_guide_* and tile_text_size_draft_settings_*, with edge_font200 and edge_narrow.
 * not ported: Typography's other styles (unused here), TileFace's haptics, long press, dimming, semantics and the guide's
 * check and progress under the state (not part of the change).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = android.app.Application::class, sdk = [36])
class TileTextSizeDraftScreenshotTest : ScreenshotBase() {

    private val guide = listOf(
        DraftTile("Save media", MtgaIcons.Download, "Images and videos", on = true),
        DraftTile("Sources", SettingsIcons.Dns, "4 of 5", on = true),
        DraftTile("New posts", MtgaIcons.Bell, "Off"),
        DraftTile("X links", SettingsIcons.Link, "Off")
    )

    private val guideRefused = listOf(
        DraftTile("Save media", MtgaIcons.Download, "Saved, no progress bar", on = true),
        DraftTile("Sources", SettingsIcons.Dns, "4 of 5", on = true),
        DraftTile("New posts", MtgaIcons.Bell, "Notifications blocked", error = true),
        DraftTile("X links", SettingsIcons.Link, "Off"),
        DraftTile("Updates", SettingsIcons.Update, "Installs not allowed", error = true)
    )

    // The Reading section's tiles (LaunchRefreshDraftScreenshotTest), the switch tile showing its Off.
    private val reading = listOf(
        DraftTile("Start on", MtgaIcons.Home, "Home"),
        DraftTile("Refresh at launch", SettingsIcons.Timer, "1 h"),
        DraftTile("X links", SettingsIcons.Link, "Off"),
        DraftTile("Share as Nitter", MtgaIcons.Share, "Off")
    )

    @Test
    fun guideTiles() = capture("tile_text_size_draft_guide") { Guide(guide) }

    @Test
    fun settingsTiles() = capture("tile_text_size_draft_settings") { Settings(reading) }

    @Test
    fun guideEdge() = captureEdge("tile_text_size_draft_guide") { Guide(guideRefused) }

    @Test
    fun settingsEdge() = captureEdge("tile_text_size_draft_settings") { Settings(reading) }

    @Composable
    private fun Settings(items: List<DraftTile>) {
        Column(Modifier.fillMaxWidth().statusBarsPadding()) {
            SettingsSectionTitle("Reading")
            DraftTileGrid(items)
        }
    }

    // WelcomeGuideDraft's layout (the title and Start unchanged), the tiles drawn by the draft face.
    @Composable
    private fun Guide(items: List<DraftTile>) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val screen = maxHeight
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
                    DraftTileGrid(items)
                    Spacer(Modifier.height(88.dp))
                    Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                }
            }
            Column(Modifier.align(Alignment.BottomCenter).widthIn(max = 720.dp).fillMaxWidth().padding(horizontal = 24.dp)) {
                BoldButton(filled = true, onClick = {}, modifier = Modifier.fillMaxWidth()) { Text("Start") }
                Spacer(Modifier.height(16.dp))
                Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
            }
        }
    }
}

/**
 * SettingsTileGrid / SetupTileGrid, as the app has them (columns from the widest word, one name size for the group,
 * rows as tall as the tallest, a last row not full centred).
 * https://github.com/213YaZ786/MTGA/blob/main/app/src/main/java/com/mtga/app/ui/component/SettingsTiles.kt#SettingsTileGrid
 */
@Composable
private fun DraftTileGrid(tiles: List<DraftTile>) {
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        val style = MaterialTheme.typography.labelLarge
        val measurer = rememberTextMeasurer()
        val density = LocalDensity.current
        val wordPx = remember(tiles.map { it.name }, style, density) {
            tiles.maxOfOrNull { tile ->
                tile.name.split(' ').filter { it.isNotEmpty() }
                    .maxOfOrNull { measurer.measure(it, style, softWrap = false, maxLines = 1).size.width } ?: 0
            } ?: 0
        }
        val wordNeed = with(density) { wordPx.toDp() } + 24.dp
        val cellMin = maxOf(160.dp, wordNeed)
        val fit = (maxWidth / cellMin).toInt()
        val columns = (if (fit < 2 && (maxWidth - 12.dp) / 2 >= wordNeed) 2 else fit).coerceIn(1, 4)
        val cell = (maxWidth - 12.dp * (columns - 1)) / columns
        val nameSize = rememberGroupLabelSize(tiles.map { it.name }, style, cell - 24.dp)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            tiles.chunked(columns).forEach { row ->
                Row(
                    Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
                ) {
                    row.forEach { tile ->
                        DraftTileFace(tile, nameSize, Modifier.width(cell).fillMaxHeight())
                    }
                }
            }
        }
    }
}

private class DraftTile(
    val name: String, val icon: ImageVector, val state: String, val on: Boolean = false, val error: Boolean = false
)

/**
 * TileFace as the app has it, the state line only changed: the name's style at the name's size, colour apart
 * (onSurfaceVariant, error when the tile says so).
 * https://github.com/213YaZ786/MTGA/blob/main/app/src/main/java/com/mtga/app/ui/component/SettingsTiles.kt#TileFace
 */
@Composable
private fun DraftTileFace(tile: DraftTile, nameSize: TextUnit, modifier: Modifier) {
    val shape = RoundedCornerShape(28.dp)
    val quiet = MaterialTheme.colorScheme.onSurfaceVariant
    ZoneSurface(shape = shape, accent = tile.on, modifier = modifier.heightIn(min = 96.dp)) {
        Column(
            Modifier.fillMaxSize().clip(shape).padding(12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                tile.icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = if (tile.on) LocalContentColor.current else quiet
            )
            val nameStyle = MaterialTheme.typography.labelLarge
            BasicText(
                tile.name,
                style = nameStyle.copy(color = LocalContentColor.current, textAlign = TextAlign.Center, fontSize = nameSize),
                modifier = Modifier.padding(top = 6.dp)
            )
            // Changed: the state in the name's style and size, colour only tells it apart.
            BasicText(
                tile.state,
                style = nameStyle.copy(
                    color = if (tile.error) MaterialTheme.colorScheme.error else quiet,
                    textAlign = TextAlign.Center,
                    fontSize = nameSize
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}
