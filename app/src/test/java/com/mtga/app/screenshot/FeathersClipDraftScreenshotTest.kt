package com.mtga.app.screenshot

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mtga.app.navigation.LocalReadableInset
import com.mtga.app.ui.component.Avatar
import com.mtga.app.ui.component.BoldButton
import com.mtga.app.ui.component.FeatherArt
import com.mtga.app.ui.component.FeatherInk
import com.mtga.app.ui.component.drawFeather
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/**
 * DRAFT for the user's yes, nothing of app/src/main changes ("dans la page d'un profil l'animation est au milieu et
 * encadree dans un rectangle"): LoadingMark draws in Canvas(...clipToBounds()), and each feather falls from y = -25 to
 * 105 hundredths of the mark, so it is cut flat at the square's top and bottom edges. feathers_clip_now is today's;
 * feathers_clip_draft is the same frame without the clip, the feather whole as it fades in and out.
 * The screen is the profile as it loads (FeedScreen's loading row: a 96 dp mark in 48 dp of padding under ProfileHeader):
 * https://github.com/213YaZ786/MTGA/blob/main/app/src/main/java/com/mtga/app/feature/feed/FeedScreen.kt#FeedScreen
 * The mark is a copy of LoadingMark that takes the clock as a fixed value instead of the infinite transition, so both
 * pictures show the same instant (FEATHERS, the swing, the seen fade, the order of translate, rotate and scale as there):
 * https://github.com/213YaZ786/MTGA/blob/main/app/src/main/java/com/mtga/app/ui/component/LoadingMark.kt#LoadingMark
 * FeatherArt, FeatherInk and drawFeather are the app's own (internal, seen by the tests).
 * not ported: LoadingMark's infinite transition (rememberInfiniteTransition, FALL_MILLIS), the running and progress
 * parameters (a frozen clock stands for them), FeedScreen's view model, scroll behaviour, bar, post list and dialogs,
 * ProfileHeader's avatar picture and banner
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = android.app.Application::class, sdk = [36])
class FeathersClipDraftScreenshotTest : ScreenshotBase() {

    @Test
    fun now() = capture("feathers_clip_now") { Profile(clip = true) }

    @Test
    fun draft() = capture("feathers_clip_draft") { Profile(clip = false) }

    // Edge: the profile at 200 % text and on a 360 dp phone.
    @Test
    fun edge() = captureEdge("feathers_clip_draft") { Profile(clip = false) }

    // A checking line as DiagnosticsScreen draws it (a 22 dp mark in a 28 dp box beside its text, a 20 dp one in the summary
    // row), the unclipped mark at the instant a feather leaves its box: does it cross the text?
    // https://github.com/213YaZ786/MTGA/blob/main/app/src/main/java/com/mtga/app/ui/component/LoadingMark.kt#LoadingMark
    @Test
    fun small() = capture("feathers_clip_draft_small") {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = LocalReadableInset.current + 16.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            listOf(true, false).forEach { clip ->
                Text(if (clip) "Clipped, as today" else "Unclipped, the draft", style = MaterialTheme.typography.labelMedium)
                CheckingRow("Checking x.com", 22.dp, clip)
                CheckingRow("Checking the Internet Archive", 22.dp, clip)
                CheckingRow("Checking 5 sources", 20.dp, clip)
            }
        }
    }

    @Composable
    private fun CheckingRow(text: String, mark: Dp, clip: Boolean) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) { FrozenMark(size = mark, time = TIME, clip = clip) }
            Spacer(Modifier.width(12.dp))
            Text(text, style = MaterialTheme.typography.bodyLarge)
        }
    }

    @Composable
    private fun Profile(clip: Boolean) {
        Column(Modifier.fillMaxSize().padding(horizontal = LocalReadableInset.current)) {
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
            Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                FrozenMark(size = 96.dp, time = TIME, clip = clip)
            }
        }
    }

    // LoadingMark with the clock given. At TIME the first feather is at 0.9 of its fall (y = 92 hundredths, its tail past
    // the bottom edge, half faded), the second at 0.23 and the third at 0.57.
    @Composable
    private fun FrozenMark(size: Dp, time: Float, clip: Boolean) {
        val accent = MaterialTheme.colorScheme.primary
        val ink = remember(accent) {
            FeatherInk(
                base = lerp(accent, Color.White, 0.72f),
                middle = lerp(accent, Color.White, 0.38f),
                tip = accent,
                shaft = lerp(accent, Color.White, 0.3f)
            )
        }
        val art = remember { FeatherArt() }
        val centre = art.centre
        val box = Modifier.size(size)
        Canvas(if (clip) box.clipToBounds() else box) {
            val unit = this.size.minDimension / 100f
            FEATHERS.forEachIndexed { i, (lane, scale) ->
                val t = (time + i / FEATHERS.size.toFloat()) % 1f
                val swing = sin(t * PI.toFloat() * 2.2f + i)
                val x = 50f + lane + swing * 10f
                val y = -25f + t * 130f
                val seen = min(1f, min(t * 5f, (1f - t) * 5f))
                translate(x * unit - centre.x, y * unit - centre.y) {
                    rotate(swing * 28f - 35f, pivot = centre) {
                        scale(scale * unit, pivot = centre) {
                            drawFeather(art, ink, seen)
                        }
                    }
                }
            }
        }
    }

    private companion object {
        const val TIME = 0.9f

        /** Each feather's lane from the middle and its size, in hundredths of the mark. */
        val FEATHERS = listOf(0f to 0.42f, -18f to 0.3f, 16f to 0.35f)
    }
}
