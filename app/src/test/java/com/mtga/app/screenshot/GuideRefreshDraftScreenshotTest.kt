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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mtga.app.feature.settings.SettingsIcons
import com.mtga.app.ui.component.BoldButton
import com.mtga.app.ui.component.GlassSlider
import com.mtga.app.ui.component.LoadingMark
import com.mtga.app.ui.component.QuietButton
import com.mtga.app.ui.component.SetupTile
import com.mtga.app.ui.component.SetupTileGrid
import com.mtga.app.ui.component.ZoneAlertDialog
import com.mtga.app.ui.icon.MtgaIcons
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * DRAFT for the user's yes (requests.md point 46), nothing of app/src/main changes: the welcome guide as the app draws
 * it today (WelcomeGuideDraftScreenshotTest's layout and tiles) with one more tile after Sources, "Refresh at launch",
 * the Settings tile's icon (SettingsIcons.Timer), its state the stop's tile label ("1 h"), a preset so done, not
 * required. guide_refresh_draft_dialog shows the dialog the tile opens, the Settings one (LaunchRefreshDraftScreenshotTest's
 * RefreshDialog) over the guide, at 1 h.
 * The stops are Feeder's SyncFrequency (the app holds it as ui/component/LaunchRefresh.kt SyncFrequency):
 * https://github.com/spacecowboy/Feeder/blob/master/app/src/main/java/com/nononsenseapps/feeder/archmodel/SettingsStore.kt#SyncFrequency
 * not ported: SyncFrequency's stringId (plain text here), setSyncFrequency's storage and schedulePeriodicRssSync
 * (a draft keeps nothing, nothing runs in the background)
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = android.app.Application::class, sdk = [36])
class GuideRefreshDraftScreenshotTest : ScreenshotBase() {

    // The stops in order: 0 every launch, -1 never (SyncFrequency's order, Every launch first).
    private enum class Stop(val minutes: Int, val tile: String, val value: String) {
        EVERY_LAUNCH(0, "Every launch", "Every launch"),
        EVERY_15_MIN(15, "15 min", "When older than 15 min"),
        EVERY_30_MIN(30, "30 min", "When older than 30 min"),
        EVERY_1_HOUR(60, "1 h", "When older than 1 h"),
        EVERY_3_HOURS(180, "3 h", "When older than 3 h"),
        EVERY_6_HOURS(360, "6 h", "When older than 6 h"),
        EVERY_12_HOURS(720, "12 h", "When older than 12 h"),
        EVERY_DAY(1440, "1 day", "When older than 1 day"),
        NEVER(-1, "Never", "Never, pull to refresh")
    }

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
        tile("refresh", "Refresh at launch", SettingsIcons.Timer, Stop.EVERY_1_HOUR.tile,
            "When the app opens, a feed is read again once its last read is older than this. Tap to choose.", done = true),
        tile("notify", "New posts", MtgaIcons.Bell, "Off", "New posts of the accounts you follow. Tap to turn on.", done = false),
        tile("links", "X links", SettingsIcons.Link, "Off",
            "x.com and twitter.com links tapped anywhere open in MTGA once Android's \"Open supported links\" is on for it. " +
                "Tap to open that page.", done = false)
    )

    @Test
    fun draft() = capture("guide_refresh_draft") { Guide(open = false) }

    @Test
    fun dialog() = capture("guide_refresh_draft_dialog", window = true) { Guide(open = true) }

    @Test
    fun edge() = captureEdge("guide_refresh_draft") { Guide(open = false) }

    @Composable
    private fun Guide(open: Boolean) {
        GuideContent(tiles)
        if (open) RefreshDialog(Stop.EVERY_1_HOUR)
    }

    // ChoiceDialog's frame: ZoneAlertDialog with the title, a centred text and its one button; the slider is the choice.
    @Composable
    private fun RefreshDialog(start: Stop) {
        var index by remember { mutableFloatStateOf(start.ordinal.toFloat()) }
        val current = Stop.entries[index.toInt().coerceIn(0, Stop.entries.lastIndex)]
        ZoneAlertDialog(
            onDismissRequest = {},
            title = { Text("Refresh at launch") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        current.value,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    GlassSlider(
                        value = index,
                        onValueChange = { index = it },
                        valueRange = 0f..(Stop.entries.size - 1).toFloat(),
                        steps = Stop.entries.size - 2,
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
                    )
                }
            },
            confirmButton = { QuietButton(onClick = {}) { Text("Done") } }
        )
    }
}

/** The guide's layout as WelcomeGuideDraftScreenshotTest draws it (WhatsNewView's body). */
@Composable
private fun GuideContent(tiles: List<SetupTile>) {
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
                SetupTileGrid(tiles) {}
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
