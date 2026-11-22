package com.mtga.app.ui.component

import androidx.activity.ComponentActivity
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.math.abs

// Test protocol: Compose's test clock (https://developer.android.com/develop/ui/compose/testing/time-manipulation):
// mainClock.autoAdvance = false, then advanceTimeByFrame, the values read after each frame.
/**
 * The ring's coming and going over a refresh (animateIn, MBProgressHUD): the bezelView's alpha, which also sets the
 * blur's radius, and its scale go 0 to 1 and back to 0 through intermediate values, never in one frame.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = android.app.Application::class, sdk = [36])
class RingMotionTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val bezelView = MBBackgroundView()
    private var refreshing by mutableStateOf(false)

    private fun show() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            LaunchedEffect(refreshing) { animateIn(bezelView, animatingIn = refreshing) }
        }
        compose.mainClock.advanceTimeByFrame()
    }

    private fun refresh(on: Boolean) {
        compose.runOnUiThread {
            refreshing = on
            Snapshot.sendApplyNotifications()
        }
    }

    // The values after each of [frames] frames.
    private fun frames(frames: Int): Pair<List<Float>, List<Float>> {
        val alphas = mutableListOf<Float>()
        val scales = mutableListOf<Float>()
        repeat(frames) {
            compose.mainClock.advanceTimeByFrame()
            alphas += bezelView.alpha.value
            scales += bezelView.transform.value
        }
        return alphas to scales
    }

    @Test
    fun `alpha and scale go 0 to 1 and back through intermediate values`() {
        show()
        assertEquals(0f, bezelView.alpha.value, 0f)
        assertEquals(0.5f, bezelView.transform.value, 0f)

        // The refresh starts: the ring comes in.
        refresh(true)
        val (alphasIn, scalesIn) = frames(40)
        assertEquals(1f, bezelView.alpha.value, 0.01f)
        assertEquals(1f, bezelView.transform.value, 0.01f)
        assertSmooth(alphasIn, rising = true)
        assertSmooth(scalesIn, rising = true)

        // The refresh ends: the ring leaves from where it stands.
        refresh(false)
        val (alphasOut, scalesOut) = frames(40)
        assertEquals(0f, bezelView.alpha.value, 0.01f)
        assertEquals(0.5f, bezelView.transform.value, 0.01f)
        assertSmooth(alphasOut, rising = false)
        assertSmooth(scalesOut, rising = false)
    }

    @Test
    fun `a refresh that ends mid zoom leaves from where the ring stands`() {
        show()
        refresh(true)
        frames(6)
        val at = bezelView.alpha.value
        assertTrue("the ring is part way in: $at", at > 0.05f && at < 0.95f)
        refresh(false)
        val (alphas, _) = frames(40)
        // No jump up to full, nor down to 0, at the turn: the first frame out is near where it stood.
        assertTrue("the first frame out is near $at: ${alphas.first()}", abs(alphas.first() - at) < 0.35f)
        assertEquals(0f, bezelView.alpha.value, 0.01f)
    }

    // No single frame moves more than a third of the way, in one direction, and the values between the ends are seen.
    private fun assertSmooth(values: List<Float>, rising: Boolean) {
        val inBetween = values.count { it > 0.55f && it < 0.95f || it in 0.05f..0.45f }
        assertTrue("intermediate values seen: $inBetween of ${values.size}", inBetween >= 3)
        values.zipWithNext().forEach { (a, b) ->
            assertTrue("a frame moved $a to $b", abs(b - a) < 0.35f)
            assertTrue("it goes one way: $a to $b", if (rising) b >= a - 0.001f else b <= a + 0.001f)
        }
    }
}
