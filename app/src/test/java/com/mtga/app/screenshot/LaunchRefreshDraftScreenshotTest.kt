package com.mtga.app.screenshot

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mtga.app.feature.settings.SettingsIcons
import com.mtga.app.ui.component.GlassSlider
import com.mtga.app.ui.component.QuietButton
import com.mtga.app.ui.component.SettingsActionTile
import com.mtga.app.ui.component.SettingsSectionTitle
import com.mtga.app.ui.component.SettingsSwitchTile
import com.mtga.app.ui.component.SettingsTileGrid
import com.mtga.app.ui.component.ZoneAlertDialog
import com.mtga.app.ui.icon.MtgaIcons
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * DRAFT for the user's yes (requests.md point 44), nothing of app/src/main changes: the setting "Refresh at launch" as a
 * tile of Settings' Reading section, the same TileFace and size as Start on, X links and Share as Nitter, its value the
 * tile's state ("1 h", "Every launch", "Never"). A tap opens a dialog as SettingsScreen's other choices do (ZoneAlertDialog,
 * ChoiceDialog's frame: title, the value as text, Cancel replaced by Done) holding the app's GlassSlider with nine stops.
 * Pictures: launch_refresh_draft_tile (the section at the default 1 h), launch_refresh_draft_dialog_1h and
 * launch_refresh_draft_dialog_never (the dialog over the section), and the tile at 200 % text and on a 360 dp phone.
 * The stops are Feeder's SyncFrequency (15 min, 30 min, 1 h, 3 h, 6 h, 12 h, 1 day, manual) with Every launch first:
 * https://github.com/spacecowboy/Feeder/blob/master/app/src/main/java/com/nononsenseapps/feeder/archmodel/SettingsStore.kt#SyncFrequency
 * https://github.com/213YaZ786/MTGA/blob/main/app/src/main/java/com/mtga/app/ui/component/ChoiceDialog.kt#ChoiceDialog
 * https://github.com/213YaZ786/MTGA/blob/main/app/src/main/java/com/mtga/app/ui/component/GlassSlider.kt#GlassSlider
 * not ported: SyncFrequency's stringId (plain text here), MANUAL's minutes -1 (Never takes the last stop),
 * setSyncFrequency's storage and schedulePeriodicRssSync (a draft keeps nothing, nothing runs in the background);
 * ChoiceDialog's option capsules, Tick, danger and icon (a slider takes their place)
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = android.app.Application::class, sdk = [36])
class LaunchRefreshDraftScreenshotTest : ScreenshotBase() {

    // The stops in order: the age in minutes a feed may have at launch before it is read again, 0 every launch, -1 never.
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

    // The Reading section's tiles, as SettingsScreen builds them (fixed values), the new one among them.
    private fun tiles(stop: Stop) = listOf(
        SettingsActionTile(
            id = "startTab", name = "Start on", title = "Start on", icon = MtgaIcons.Home,
            value = "Home", summary = "Home"
        ),
        SettingsActionTile(
            id = "refreshLaunch", name = "Refresh at launch", title = "Refresh at launch", icon = SettingsIcons.Timer,
            value = stop.tile, summary = stop.value
        ),
        SettingsActionTile(
            id = "xLinks", name = "X links", title = "Open X links in MTGA", icon = SettingsIcons.Link,
            value = "Off", summary = "Off"
        ),
        SettingsSwitchTile(
            id = "nitter", name = "Share as Nitter", title = "Share links as Nitter", icon = MtgaIcons.Share,
            summary = "Share and Copy link use your first enabled server instead of x.com.",
            checked = false, onChange = {}
        )
    )

    @Test
    fun tile() = capture("launch_refresh_draft_tile") { Reading(Stop.EVERY_1_HOUR, open = false) }

    @Test
    fun dialogOneHour() = capture("launch_refresh_draft_dialog_1h", window = true) { Reading(Stop.EVERY_1_HOUR, open = true) }

    @Test
    fun dialogNever() = capture("launch_refresh_draft_dialog_never", window = true) { Reading(Stop.NEVER, open = true) }

    @Test
    fun edge() = captureEdge("launch_refresh_draft_tile") { Reading(Stop.EVERY_1_HOUR, open = false) }

    // The Reading section, and over it the dialog a tap on the tile opens.
    @Composable
    private fun Reading(stop: Stop, open: Boolean) {
        Column(Modifier.fillMaxWidth().statusBarsPadding()) {
            SettingsSectionTitle("Reading")
            SettingsTileGrid(tiles(stop)) {}
        }
        if (open) RefreshDialog(stop)
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
