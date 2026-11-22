package com.mtga.app.screenshot

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mtga.app.navigation.LocalReadableInset
import com.mtga.app.ui.component.Avatar
import com.mtga.app.ui.component.BoldButton
import com.mtga.app.ui.component.FloatingTopBar
import com.mtga.app.ui.component.LoadingMark
import com.mtga.app.ui.icon.MtgaIcons
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * DRAFT for the user's yes (requests.md points 20 and 21), nothing of app/src/main changes: the top of a profile while it
 * loads. profile_top_now is today's: the floating bar with Back and the Refresh button, which shows LoadingMark 22 dp
 * while loading ("l'animation est dans un capsule en haut a droite plutot que dans le rond en haut", "dans la tab du
 * profil on a une capsule en haut : enleve les 2 boutons ils sont inutiles ici"). profile_top_draft has no bar: the
 * ProfileHeader is the top of the screen, and the load shows in the bare mark (72 dp, no disc, no surface: "les bulles
 * d'animations ne me plaisent pas") centred in the 80 dp room above the header, as a swipe down would show it, the content
 * pulled down uncovering the mark (PullRevealDraftScreenshotTest's pull_reveal_draft). LinkedOut's profile already draws no bar while
 * its header is on screen, and Tusky refreshes a profile by a swipe with the load in that round indicator:
 * https://github.com/213YaZ786/LinkedOut/blob/main/app/src/main/java/com/linkedout/app/feature/feed/FeedScreen.kt#FeedScreen
 * https://github.com/tuskyapp/Tusky/blob/develop/app/src/main/java/com/keylesspalace/tusky/components/account/AccountActivity.kt#AccountActivity
 * The screen is MTGA's FeedScreen, a faithful copy of its top with fixed data (its first load: the account known from the
 * followed list, no feed yet, so the header shows the name, the handle and Follow, then the big mark of an empty first load):
 * https://github.com/213YaZ786/MTGA/blob/main/app/src/main/java/com/mtga/app/feature/feed/FeedScreen.kt#FeedScreen
 * The mark's place is PullIndicator's, with no disc:
 * https://github.com/213YaZ786/MTGA/blob/main/app/src/main/java/com/mtga/app/ui/component/PullIndicator.kt#PullIndicator
 * The marks are frozen (running = false, progress = 1f), which draws on Robolectric.
 * not ported: FeedScreen's view model, the folding scroll behavior and underFloatingTopBar, the bar title once the header
 * has gone (BarTitle), the unfollow dialog, the viewer, the post list, the footer, the challenge pill, the pull gesture itself
 * (PullToRefreshBox: the mark stands for the state it leaves), PullIndicator's disc, glass, haptic threshold effect, fade-in alpha and translation, Tusky's toolbar
 * buttons (the user's words win: no bar), the avatar picture and the banner
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = android.app.Application::class, sdk = [36])
class ProfileTopDraftScreenshotTest : ScreenshotBase() {

    // The bar as FeedScreen builds it while loading: Back, no title (the header is on screen), the Refresh button with the mark.
    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun now() = capture("profile_top_now") {
        Column(Modifier.fillMaxSize()) {
            FloatingTopBar(
                title = {},
                navigationIcon = {
                    // Material's IconButton: a 48 dp target, the icon 24 dp in the bar's content colour.
                    Box(Modifier.size(48.dp).clickable(onClick = {}), contentAlignment = Alignment.Center) {
                        Icon(MtgaIcons.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                        LoadingMark(size = 22.dp, running = false, progress = 1f)
                    }
                }
            )
            Box(Modifier.padding(horizontal = LocalReadableInset.current)) { Header() }
        }
    }

    // No bar: the header under the status bar, the bare mark in the room above it (no disc, no surface), as iOS's refresh
    // control uncovers its indicator (PullRevealDraftScreenshotTest's pull_reveal_draft).
    @Test
    fun draft() = capture("profile_top_draft") { DraftProfile() }

    // Edge: the draft at 200 % text and on a 360 dp phone (font scale as Roborazzi's captureRoboImage, device by qualifiers as
    // Now in Android's captureMultiDevice)
    // https://github.com/takahirom/roborazzi/blob/main/roborazzi-compose/src/main/java/com/github/takahirom/roborazzi/RoborazziCompose.kt#captureRoboImage
    // https://github.com/android/nowinandroid/blob/main/core/screenshot-testing/src/main/kotlin/com/google/samples/apps/nowinandroid/core/testing/util/ScreenshotHelper.kt#captureMultiDevice
    @Test
    fun edge() = captureEdge("profile_top_draft") { DraftProfile() }

    @Composable
    private fun DraftProfile() {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
            // The header keeps the place it has under today's bar, so the mark is centred in the 80 dp the bar left,
            // and the mark alone shows the load (no second mark in the list).
            Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                LoadingMark(size = 72.dp, running = false, progress = 1f)
            }
            Box(Modifier.padding(horizontal = LocalReadableInset.current)) { Header(listMark = false) }
        }
    }

    // A faithful copy of ProfileHeader (FeedScreen.kt, private) for an account known by name and handle, no feed read yet,
    // then the list's loading row (a 96 dp mark in 48 dp of padding).
    @Composable
    private fun Header(listMark: Boolean = true) {
        Column {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Avatar(url = null, name = "The New York Times", size = 88.dp)
                Text(
                    "The New York Times",
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 6.dp)
                )
                Text(
                    "@nytimes",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                BoldButton(filled = true, onClick = {}, modifier = Modifier.padding(top = 8.dp)) { Text("Follow") }
                Spacer(Modifier.height(4.dp))
            }
            if (listMark) {
                Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                    LoadingMark(size = 96.dp, running = false, progress = 1f)
                }
            }
        }
    }
}
