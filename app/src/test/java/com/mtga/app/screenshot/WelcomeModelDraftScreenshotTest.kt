package com.mtga.app.screenshot

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mtga.app.feature.welcome.WelcomeContent
import com.mtga.app.ui.component.BoldButton
import com.mtga.app.ui.component.LoadingMark
import com.mtga.app.ui.component.SetupTile
import com.mtga.app.ui.component.SetupTileGrid
import com.mtga.app.ui.component.requiredDone
import com.mtga.app.ui.component.requiredLeft
import com.mtga.app.ui.icon.MtgaIcons
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * DRAFT for the user's yes, nothing of app/src/main changes: the welcome guide in the order the user asked for
 * ("l'animation en haut et la petite description de l'app juste en dessous ensuite les tiles de settings importants
 * et le reste", the model for every app): the mark, one sentence, the tiles, Start. No app name, no "Welcome", no
 * "N left". welcome_model_now is today's WelcomeContent for comparison (MTGA has no welcome screenshot test yet).
 *
 * [WelcomeContentDraft] is a private copy of WelcomeContent, ported from
 * https://github.com/213YaZ786/Language/blob/main/app/src/main/java/com/yaz/language/feature/welcome/WelcomeScreen.kt#WelcomeContent
 * and
 * https://github.com/florisboard/florisboard/blob/main/app/src/main/kotlin/dev/patrickgold/florisboard/app/setup/SetupScreen.kt#SetupScreen
 * through MTGA's own
 * https://github.com/213YaZ786/MTGA/blob/main/app/src/main/java/com/mtga/app/feature/welcome/WelcomeScreen.kt#WelcomeContent
 * only the top differs. The tiles are those rememberSetupTiles of that file gives on a first launch (its names,
 * icons and states; the choice of media not made yet, so it is required), in its order. The tiles' layout and the
 * capture follow
 * https://github.com/213YaZ786/LinkedOut/blob/main/app/src/test/java/com/linkedout/app/screenshot/WelcomeScreenshotTest.kt#firstRun
 *
 * The mark is LoadingMark frozen on its written frame (running = false, progress = 1f): it stands in for the
 * animation, to show where the mark sits.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = android.app.Application::class, sdk = [36])
class WelcomeModelDraftScreenshotTest : ScreenshotBase() {

    private fun tile(
        id: String, name: String, icon: androidx.compose.ui.graphics.vector.ImageVector, state: String,
        done: Boolean, required: Boolean
    ) = SetupTile(
        id = id, name = name, title = name, icon = icon, state = state, summary = "", done = done,
        required = required, onClick = {}
    )

    // A first launch: nothing chosen for the media (required), new posts off, the source that shows X the reader's
    // address on, the preset.
    private val firstRun = listOf(
        tile("media", "Save media", MtgaIcons.Download, "Not chosen", done = false, required = true),
        tile("notify", "New posts", MtgaIcons.Bell, "Off", done = false, required = false),
        tile("source:Newest posts from X", "Newest from X", MtgaIcons.Pulse, "On", done = true, required = false)
    )

    // The draft: Save media preset to "Images and videos" (saved on Wi-Fi, AutoDownload.UNMETERED; the other choice is "Posts
    // only", AutoDownload.OFF) and no longer required, as LinkedOut's guide (2026-11-06), so Start never waits and no "Still
    // needed" line is left.
    private val draftRun = listOf(
        tile("media", "Save media", MtgaIcons.Download, "Images and videos", done = true, required = false),
        tile("notify", "New posts", MtgaIcons.Bell, "Off", done = false, required = false),
        tile("source:Newest posts from X", "Newest from X", MtgaIcons.Pulse, "On", done = true, required = false)
    )

    @Test
    fun now() = capture("welcome_model_now") { WelcomeContent(firstRun, reopened = false, onStart = {}) }

    @Test
    fun draft() = capture("welcome_model_draft") { WelcomeContentDraft(draftRun, reopened = false, onStart = {}) }

    // Edge: the draft at 200 % text and on a 360 dp phone (font scale as Roborazzi's captureRoboImage, device by qualifiers as
    // Now in Android's captureMultiDevice)
    // https://github.com/takahirom/roborazzi/blob/main/roborazzi-compose/src/main/java/com/github/takahirom/roborazzi/RoborazziCompose.kt#captureRoboImage
    // https://github.com/android/nowinandroid/blob/main/core/screenshot-testing/src/main/kotlin/com/google/samples/apps/nowinandroid/core/testing/util/ScreenshotHelper.kt#captureMultiDevice
    @Test
    fun edge() = captureEdge("welcome_model_draft") { WelcomeContentDraft(draftRun, reopened = false, onStart = {}) }
}

@Composable
private fun WelcomeContentDraft(tiles: List<SetupTile>, reopened: Boolean, onStart: () -> Unit, onExplain: (SetupTile) -> Unit = {}) {
    val steps = tiles
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
            SetupTileGrid(steps, onExplain)

            Spacer(Modifier.height(32.dp))
            val ready = steps.requiredDone()
            BoldButton(filled = true, enabled = ready, onClick = onStart) { Text(if (reopened) "Done" else "Start") }
            if (!ready) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "Still needed: " + steps.requiredLeft().joinToString(", ") { it.name },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
            Spacer(Modifier.height(24.dp))
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
}
