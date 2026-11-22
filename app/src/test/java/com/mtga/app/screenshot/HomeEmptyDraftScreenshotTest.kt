package com.mtga.app.screenshot

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mtga.app.navigation.LocalReadableInset
import com.mtga.app.ui.component.EmptyZone
import com.mtga.app.ui.component.FeedBanner
import com.mtga.app.ui.component.MenuPill
import com.mtga.app.ui.icon.MtgaIcons
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * DRAFT for the user's yes (requests.md point 18), nothing of app/src/main changes: Home with nobody followed.
 * home_empty_now is today's top: the centre of the banner is the word "Home" ("la tab home y'a juste ecrit home en haut
 * nul"), nothing on either side (TimelineScreen's banner gives Search only when someone is followed, and the alert only
 * when something failed). home_empty_draft puts the same feed MenuPill as with accounts in the centre ("All", the
 * folder icon, the chevron), no subtitle, the sides as they are today, then the same empty zone.
 * The screen is TimelineScreen's `followedCount == 0` branch (banner, then EmptyState weighted over what is left), a
 * faithful copy with fixed data:
 * https://github.com/213YaZ786/MTGA/blob/main/app/src/main/java/com/mtga/app/feature/timeline/TimelineScreen.kt#TimelineScreen
 * not ported: TimelineScreen's view model, the feed menu (GlassMenu, drawn open in HomeTopDraftScreenshotTest), the
 * failure dialog, the pull to refresh, the posts list, the footer, the challenge pill, the back to top button
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = android.app.Application::class, sdk = [36])
class HomeEmptyDraftScreenshotTest : ScreenshotBase() {

    @Test
    fun now() = capture("home_empty_now") {
        Screen {
            Text(
                "Home",
                style = MaterialTheme.typography.headlineSmall.copy(color = MaterialTheme.colorScheme.primary),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() }
            )
        }
    }

    @Test
    fun draft() = capture("home_empty_draft") { DraftScreen() }

    // Edge: the draft at 200 % text and on a 360 dp phone (font scale as Roborazzi's captureRoboImage, device by qualifiers as
    // Now in Android's captureMultiDevice)
    // https://github.com/takahirom/roborazzi/blob/main/roborazzi-compose/src/main/java/com/github/takahirom/roborazzi/RoborazziCompose.kt#captureRoboImage
    // https://github.com/android/nowinandroid/blob/main/core/screenshot-testing/src/main/kotlin/com/google/samples/apps/nowinandroid/core/testing/util/ScreenshotHelper.kt#captureMultiDevice
    @Test
    fun edge() = captureEdge("home_empty_draft") { DraftScreen() }

    @Composable
    private fun DraftScreen() {
        Screen {
            MenuPill(
                icon = MtgaIcons.Folder,
                label = "All",
                expanded = false,
                trailingIcon = ExpandMore,
                onClick = {}
            )
        }
    }

    @Composable
    private fun Screen(centre: @Composable () -> Unit) {
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = LocalReadableInset.current)) {
            FeedBanner(centre = centre, subtitle = null)
            // The weight gives it the rest of the screen, so it sits in the middle of what is left under the banner.
            EmptyZone(
                title = "Nothing followed yet",
                message = "Find a few accounts and they will all appear here in one stream.",
                actionLabel = "Open Accounts",
                onAction = {},
                modifier = Modifier.weight(1f).fillMaxSize()
            )
        }
    }
}
