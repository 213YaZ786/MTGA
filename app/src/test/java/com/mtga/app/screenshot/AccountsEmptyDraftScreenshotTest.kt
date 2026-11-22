package com.mtga.app.screenshot

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mtga.app.navigation.LocalReadableInset
import com.mtga.app.ui.component.EmptyZone
import com.mtga.app.ui.component.FeedBanner
import com.mtga.app.ui.component.GlassSearchField
import com.mtga.app.ui.icon.MtgaIcons
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * DRAFT for the user's yes (requests.md point 17), nothing of app/src/main changes: Accounts with nobody followed.
 * accounts_empty_now is today's top: FeedBanner kept as an empty row (a 1 dp box for TalkBack's heading) above the
 * search field, "la tab account a plus aucune icon en haut et pourtant la search bar est pas en haut".
 * accounts_empty_draft has no banner row when nobody is followed: the search field is the first row under the status bar,
 * 8 dp below it, the top gap the banner row's slots (44 dp in a 60 dp row) leave on Home.
 * The screen is AccountsScreen's own LazyColumn (banner, search, the empty zone at 70 % of the list), a faithful copy
 * with fixed data, as AccountsTopDraftScreenshotTest does for the top:
 * https://github.com/213YaZ786/MTGA/blob/main/app/src/main/java/com/mtga/app/feature/accounts/AccountsScreen.kt#AccountsScreen
 * not ported: AccountsScreen's view model, paste() and the clipboard, the clear button, CleanLinkEffect, the candidate
 * card, the hint, the folder pill and menu and its filing dialog, the account rows (nobody is followed), the dock padding
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = android.app.Application::class, sdk = [36])
class AccountsEmptyDraftScreenshotTest : ScreenshotBase() {

    @Test
    fun now() = capture("accounts_empty_now") { Screen(banner = true) }

    @Test
    fun draft() = capture("accounts_empty_draft") { Screen(banner = false) }

    // Edge: the draft at 200 % text and on a 360 dp phone (font scale as Roborazzi's captureRoboImage, device by qualifiers as
    // Now in Android's captureMultiDevice)
    // https://github.com/takahirom/roborazzi/blob/main/roborazzi-compose/src/main/java/com/github/takahirom/roborazzi/RoborazziCompose.kt#captureRoboImage
    // https://github.com/android/nowinandroid/blob/main/core/screenshot-testing/src/main/kotlin/com/google/samples/apps/nowinandroid/core/testing/util/ScreenshotHelper.kt#captureMultiDevice
    @Test
    fun edge() = captureEdge("accounts_empty_draft") { Screen(banner = false) }

    @Composable
    private fun Screen(banner: Boolean) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp + LocalReadableInset.current,
                top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding(),
                end = 16.dp + LocalReadableInset.current,
                bottom = 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (banner) {
                item(key = "banner") {
                    Box(Modifier.fullBleed(16.dp)) {
                        // Nobody followed: no pill, no count, the row stays empty above the field.
                        FeedBanner(
                            centre = { Box(Modifier.size(1.dp).semantics { heading(); contentDescription = "Accounts" }) },
                            subtitle = null
                        )
                    }
                }
            }
            item(key = "search") {
                // The draft: the field first, 8 dp under the status bar as Home's slots are under it.
                Field(Modifier.padding(top = if (banner) 0.dp else 8.dp))
            }
            item(key = "empty") {
                EmptyZone(
                    title = "No accounts yet",
                    message = "Type a handle above to read a profile, then follow it to build your timeline. " +
                        "Handles stay on this device and are never sent anywhere except to the server " +
                        "that serves the feed.",
                    icon = MtgaIcons.Person,
                    modifier = Modifier.fillParentMaxHeight(0.7f)
                )
            }
        }
    }

    // The search field as AccountsScreen builds it: search icon, the paste button at its end (Material's IconButton drawn as
    // a 40 dp circle, the icon in onSurfaceVariant, as AccountsTopDraftScreenshotTest does).
    @Composable
    private fun Field(modifier: Modifier) {
        GlassSearchField(
            value = "",
            onValueChange = {},
            placeholder = "Search or open a handle",
            leadingIcon = { Icon(MtgaIcons.Search, contentDescription = null) },
            trailingIcon = {
                Box(
                    Modifier.size(40.dp).clip(CircleShape).clickable(onClick = {}),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        MtgaIcons.Paste,
                        contentDescription = "Paste a link or a handle",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            modifier = modifier.fillMaxWidth().padding(bottom = 8.dp)
        )
    }
}

// AccountsScreen's fullBleed (private there): undoes the list's side padding for a row that brings its own.
private fun Modifier.fullBleed(side: Dp) = layout { measurable, constraints ->
    val extra = side.roundToPx()
    val placeable = measurable.measure(
        constraints.copy(minWidth = constraints.minWidth + 2 * extra, maxWidth = constraints.maxWidth + 2 * extra)
    )
    layout(constraints.maxWidth, placeable.height) { placeable.place(-extra, 0) }
}
