package com.mtga.app.screenshot

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * DRAFT for the user's yes (requests.md point 31), nothing of app/src/main changes: the content scrolls on the whole
 * screen with no fade at all (no underFloatingTopBar), and the top capsule folds away while scrolling down and comes
 * back on the first upward scroll: Material's enterAlwaysScrollBehavior (EnterAlwaysScrollBehavior, its
 * nestedScrollConnection and heightOffset), which FloatingTopBar already supports through its scrollBehavior:
 * https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/AppBar.kt#enterAlwaysScrollBehavior
 * The Connection check list is scrolled four items down, the frame is [DiagnosticsDraftScreen]
 * (ConnectionCheckRingDraftScreenshotTest): scroll_full_screen_draft, the capsule fully folded (heightOffset =
 * heightOffsetLimit, rows up to the window's top, under the status bar); scroll_full_screen_draft_back, the capsule
 * coming back (half the offset, a scroll up).
 * Settings is not drawn: its header (FloatingFrame's top and large title) has no scrollBehavior, so it cannot fold the
 * same way without a new mechanism.
 * not ported: EnterAlwaysScrollBehavior's flingAnimationSpec and snapAnimationSpec (a still picture, no fling)
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = android.app.Application::class, sdk = [36])
class ScrollFullScreenDraftScreenshotTest : ScreenshotBase() {

    // The bar's height is measured after the first frame, and the offset follows it one frame later: frames are given.
    private fun settle() {
        repeat(3) { compose.mainClock.advanceTimeByFrame() }
        compose.waitForIdle()
    }

    @Test
    fun folded() = capture("scroll_full_screen_draft", interact = ::settle) {
        DiagnosticsDraftScreen(checking = false, firstItem = 4, fade = false, foldedBy = 1f)
    }

    @Test
    fun back() = capture("scroll_full_screen_draft_back", interact = ::settle) {
        DiagnosticsDraftScreen(checking = false, firstItem = 4, fade = false, foldedBy = 0.5f)
    }

    // Edge: the capsule fully folded, at 200 % text and on a 360 dp phone.
    @Test
    fun foldedEdge() = captureEdge("scroll_full_screen_draft", interact = ::settle) {
        DiagnosticsDraftScreen(checking = false, firstItem = 4, fade = false, foldedBy = 1f)
    }
}
