package com.mtga.app.screenshot

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mtga.app.feature.settings.SOURCE_OPTIONS
import com.mtga.app.feature.settings.SettingsIcons
import com.mtga.app.ui.component.BoldButton
import com.mtga.app.ui.component.CheckableDropdownMenuItem
import com.mtga.app.ui.component.DropdownMenuGroup
import com.mtga.app.ui.component.LoadingMark
import com.mtga.app.ui.component.SelectableDropdownMenuItem
import com.mtga.app.ui.component.SetupTile
import com.mtga.app.ui.component.SetupTileGrid
import com.mtga.app.ui.icon.MtgaIcons
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * DRAFT for the user's yes (requests.md point 23), nothing of app/src/main changes: the sources in the guide. Today SourceChoices
 * keeps one tile, "Newest from X" ("dans le tuto tu ne proposes que X comme source il faudrait une tile qui ouvre un menu et
 * la possibilite de tick off certaines sources"). guide_sources_draft: one tile "Sources" in its place (the Dns icon of
 * Settings' Sources section, state "4 of 5", done), in the guide of WelcomeModelDraftScreenshotTest (the mark, "X reader",
 * the tiles, Start). guide_sources_menu_draft: the menu a tap on it opens, drawn as the feed folder menu is ("pour le choix
 * des sources enleve les square tick box et remplace par ce qu'on a fait dans le menu folder feed"): the app's GlassMenu
 * look under the tile (MenuOverScreen, as home_top_menu_draft), one DropdownMenuGroup, a CheckableDropdownMenuItem per entry
 * of SOURCE_OPTIONS (title only, the check in the selected container), the Internet Archive off, the others on.
 * The tiles are SourceChoices' SetupTile, the guide a private copy of WelcomeContent:
 * https://github.com/213YaZ786/MTGA/blob/main/app/src/main/java/com/mtga/app/feature/welcome/WelcomeScreen.kt#SourceChoices
 * The menu is TimelineScreen's feed menu (a GlassMenu, groups of items):
 * https://github.com/213YaZ786/MTGA/blob/main/app/src/main/java/com/mtga/app/feature/timeline/TimelineScreen.kt#TimelineScreen
 * guide_media_menu_draft: the Save media tile reads "Images and videos" (done, not required) and its menu has two choices,
 * "Images and videos" (saved on Wi-Fi, AutoDownload.UNMETERED, the default) and "Posts only" (AutoDownload.OFF).
 * not ported: SourceChoices' settings store and its per-source tile (the draft has the one tile), rememberSetupTiles'
 * other tiles' dialogs, WelcomeScreen's saved explain state; the tap that toggles an item (drawn, not wired);
 * TimelineScreen's list, pull to refresh, folders, search, alert and dialogs (only its menu is the model), GlassMenu's Popup
 * itself (Robolectric does not draw a Popup: MenuOverScreen stands for it)
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = android.app.Application::class, sdk = [36])
class GuideSourcesDraftScreenshotTest : ScreenshotBase() {

    private fun tile(
        id: String, name: String, icon: androidx.compose.ui.graphics.vector.ImageVector, state: String, done: Boolean
    ) = SetupTile(
        id = id, name = name, title = name, icon = icon, state = state, summary = "", done = done,
        required = false, onClick = {}
    )

    // The guide as WelcomeModelDraftScreenshotTest's draftRun, the third tile now "Sources".
    private val guide = listOf(
        tile("media", "Save media", MtgaIcons.Download, "Images and videos", done = true),
        tile("notify", "New posts", MtgaIcons.Bell, "Off", done = false),
        // As the menu's picture: four of five on (Internet Archive off).
        tile("sources", "Sources", SettingsIcons.Dns, "4 of 5", done = true)
    )

    // The reader unticked the Internet Archive.
    private val off = setOf("Internet Archive")

    // The last source left on: every entry of SOURCE_OPTIONS unticked but "Newest posts from X", the tile "1 of 5".
    private val offNone = SOURCE_OPTIONS.map { it.title }.filter { it != "Newest posts from X" }.toSet()
    private val guideNone = listOf(
        tile("media", "Save media", MtgaIcons.Download, "Images and videos", done = true),
        tile("notify", "New posts", MtgaIcons.Bell, "Off", done = false),
        tile("sources", "Sources", SettingsIcons.Dns, "1 of 5", done = true)
    )

    @Test
    fun tiles() = capture("guide_sources_draft") { Guide() }

    // The menu a tap on the Sources tile opens, as the feed folder menu is drawn (home_top_menu_draft): the app's GlassMenu
    // look under the tile, one group, a CheckableDropdownMenuItem per source.
    @Test
    fun menu() = capture("guide_sources_menu_draft") { Menu(guide, off) }

    // The last source left on: only "Newest posts from X" ticked, the tile reading "1 of 5" (normal font, as every picture).
    // Same menu as TimelineScreen's feed menu, the check drawn per entry:
    // https://github.com/213YaZ786/MTGA/blob/main/app/src/main/java/com/mtga/app/feature/timeline/TimelineScreen.kt#TimelineScreen
    @Test
    fun none() = capture("guide_sources_menu_draft_none") { Menu(guideNone, offNone) }

    // The menu open at 200 % text and on a 360 dp phone: guide_sources_menu_draft_edge_font200 and _edge_narrow. The font
    // scale set and restored as Roborazzi's captureRoboImage does, the device by qualifiers as Now in Android's captureMultiDevice
    // https://github.com/takahirom/roborazzi/blob/main/roborazzi-compose/src/main/java/com/github/takahirom/roborazzi/RoborazziCompose.kt#captureRoboImage
    // https://github.com/android/nowinandroid/blob/main/core/screenshot-testing/src/main/kotlin/com/google/samples/apps/nowinandroid/core/testing/util/ScreenshotHelper.kt#captureMultiDevice
    @Test
    fun edge() = captureEdge("guide_sources_menu_draft") { Menu(guide, off) }

    // The feed menu's one group of checkable items under the tile, as above
    // https://github.com/213YaZ786/MTGA/blob/main/app/src/main/java/com/mtga/app/feature/timeline/TimelineScreen.kt#TimelineScreen
    // The Save media tile's menu (guide_media_menu_draft): two choices, "Images and videos" (the default, saved on Wi-Fi:
    // AutoDownload.UNMETERED) selected and "Posts only" (AutoDownload.OFF), as the feed folder menu's single choice; the
    // network choice stays in Settings. The menu opens under the first tile. At 200 % text and on a 360 dp phone:
    // guide_media_menu_draft_edge_font200 and _edge_narrow.
    // https://github.com/213YaZ786/MTGA/blob/main/app/src/main/java/com/mtga/app/feature/welcome/WelcomeScreen.kt#SourceChoices
    // https://github.com/213YaZ786/MTGA/blob/main/app/src/main/java/com/mtga/app/feature/timeline/TimelineScreen.kt#TimelineScreen
    @Test
    fun mediaMenu() = capture("guide_media_menu_draft") { MediaMenu() }

    @Test
    fun mediaEdge() = captureEdge("guide_media_menu_draft") { MediaMenu() }

    @Composable
    private fun MediaMenu() {
        var anchor by remember { mutableStateOf(IntOffset.Zero) }
        MenuOverScreen(open = true, anchor = anchor, screen = { Guide(menuTile = 0, onTile = { anchor = it }) }) {
            DropdownMenuGroup(index = 0, count = 1) {
                SelectableDropdownMenuItem(selected = true, onClick = {}, text = "Images and videos")
                SelectableDropdownMenuItem(selected = false, onClick = {}, text = "Posts only")
            }
        }
    }

    @Composable
    private fun Menu(tiles: List<SetupTile>, unticked: Set<String>) {
        var anchor by remember { mutableStateOf(IntOffset.Zero) }
        MenuOverScreen(open = true, anchor = anchor, screen = { Guide(tiles, onTile = { anchor = it }) }) {
            DropdownMenuGroup(index = 0, count = 1) {
                SOURCE_OPTIONS.forEach { option ->
                    CheckableDropdownMenuItem(checked = option.title !in unticked, onCheckedChange = {}, text = option.title)
                }
            }
        }
    }

    @Composable
    private fun Guide(guide: List<SetupTile> = this.guide, menuTile: Int = guide.lastIndex, onTile: (IntOffset) -> Unit = {}) {
        val density = LocalDensity.current
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(horizontal = 8.dp)
            ) {
                Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
                Spacer(Modifier.height(40.dp))
                LoadingMark(size = 160.dp, running = false, progress = 1f)
                Spacer(Modifier.height(20.dp))
                Text(
                    "X reader",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(Modifier.height(24.dp))
                // SetupTileGrid does not say where its tiles are: the tile that opens the menu ([menuTile], the Sources tile
                // by default, the last of the last row) has its place worked out from the grid's box by the grid's own
                // columns rule (cells at least 160 dp, 1 to 4 columns, 12 dp between them, a last row not full centred, the
                // rows of one height), the menu opening under it.
                Box(
                    Modifier.onGloballyPositioned {
                        val at = it.positionInRoot()
                        val inner = with(density) { it.size.width.toDp() } - 32.dp
                        val columns = (inner / 160.dp).toInt().coerceIn(1, 4)
                        val cell = (inner - 12.dp * (columns - 1)) / columns
                        val rows = (guide.size + columns - 1) / columns
                        val row = menuTile / columns
                        val inRow = minOf(columns, guide.size - row * columns)
                        val first = (inner - cell * inRow - 12.dp * (inRow - 1)) / 2
                        val centre = 16.dp + first + (cell + 12.dp) * (menuTile % columns) + cell / 2
                        val rowHeight = (with(density) { it.size.height.toDp() } - 12.dp * (rows - 1)) / rows
                        val under = (rowHeight + 12.dp) * row + rowHeight
                        onTile(IntOffset((at.x + with(density) { centre.toPx() }).toInt(), (at.y + with(density) { under.toPx() }).toInt()))
                    }
                ) { SetupTileGrid(guide) {} }
                Spacer(Modifier.height(32.dp))
                BoldButton(filled = true, onClick = {}) { Text("Start") }
                Spacer(Modifier.height(24.dp))
                Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
            }
        }
    }
}
