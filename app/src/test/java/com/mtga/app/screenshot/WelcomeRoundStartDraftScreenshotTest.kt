package com.mtga.app.screenshot

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.foundation.Image
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mtga.app.feature.settings.SettingsIcons
import com.mtga.app.ui.component.LoadingMark
import com.mtga.app.ui.component.SetupTile
import com.mtga.app.ui.component.SetupTileGrid
import com.mtga.app.ui.glass.LocalGlass
import com.mtga.app.ui.glass.glassZone
import com.mtga.app.ui.icon.MtgaIcons
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * DRAFT for the user's yes (requests.md point 26), nothing of app/src/main changes: WelcomeStartDraftScreenshotTest's
 * layout with Start as a round 80 dp button under the tiles, part of the centred content block, not pinned.
 * Layout ported from https://github.com/SvenTiigi/WhatsNewKit/blob/main/Sources/View/WhatsNewView.swift#body
 * (carried from WelcomeStartDraftScreenshotTest.kt#WelcomeStartDraft).
 * not ported: WhatsNewView's footer band, secondary action sheet, version store, title and feature helper views
 * (the app's title and SetupTileGrid take their place).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = android.app.Application::class, sdk = [36])
class WelcomeRoundStartDraftScreenshotTest : ScreenshotBase() {

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
        tile("notify", "New posts", MtgaIcons.Bell, "Off", "New posts of the accounts you follow. Tap to turn on.", done = false),
        tile("links", "X links", SettingsIcons.Link, "Off",
            "x.com and twitter.com links tapped anywhere open in MTGA once Android's \"Open supported links\" is on for it. " +
                "Tap to open that page.", done = false)
    )

    @Test
    fun draft() = capture("welcome_round_start_draft") { WelcomeRoundStartDraft(tiles, onStart = {}) }

    @Test
    fun edge() = captureEdge("welcome_round_start_draft") { WelcomeRoundStartDraft(tiles, onStart = {}) }
}

/**
 * FabMediumTokens: ContainerHeight 80 dp, IconSize 28 dp (the container is as wide as it is high).
 * https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/tokens/FabMediumTokens.kt#FabMediumTokens
 * not ported: ContainerShape (CircleShape here, as every button of the apps), ContainerWidth, container colours
 * (FilledButtonTokens below give them), focus, hover, pressed and icon colours, elevations (a draft, nothing is pressed).
 */
private val FabMediumContainerSize: Dp = 80.dp
private val FabMediumIconSize: Dp = 28.dp

/**
 * arrow_forward, Material Symbols Rounded 24px: viewport 960, built as MtgaIcons' build does, mirrored in
 * right-to-left as MtgaIcons does for ArrowBack (autoMirror).
 * https://github.com/google/material-design-icons/blob/master/symbols/android/arrow_forward/materialsymbolsrounded/arrow_forward_24px.xml#arrow_forward
 * not ported: the tint attribute (the icon takes the colour of its use).
 */
private val ArrowForward: ImageVector by lazy {
    ImageVector.Builder(
        name = "ArrowForward",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 960f,
        viewportHeight = 960f,
        autoMirror = true
    ).apply {
        addPath(
            pathData = PathParser().parsePathString(
                "M647,520L200,520Q183,520 171.5,508.5Q160,497 160,480Q160,463 171.5,451.5Q183,440 200,440L647,440" +
                    "L451,244Q439,232 439.5,216Q440,200 452,188Q464,177 480,176.5Q496,176 508,188L772,452" +
                    "Q778,458 780.5,465Q783,472 783,480Q783,488 780.5,495Q778,502 772,508L508,772" +
                    "Q497,783 480.5,783Q464,783 452,772Q440,760 440,743.5Q440,727 452,715L647,520Z"
            ).toNodes(),
            fill = SolidColor(Color.Black)
        )
    }.build()
}

/**
 * MediumFloatingActionButton: a container of at least 80 x 80 dp holding one centred icon of 28 dp. In glass the
 * primary (FilledButtonTokens: ContainerColor Primary, icon OnPrimary) is kept at 0.85 alpha over the pane so the rim
 * shows; the pane is BoldButton's glass branch (glassZone, lens = 1f, CircleShape). A clickable Box, not a Material control.
 * https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/FloatingActionButton.kt#MediumFloatingActionButton
 * https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/tokens/FilledButtonTokens.kt#FilledButtonTokens
 * not ported: FloatingActionButton, SmallFloatingActionButton, LargeFloatingActionButton, ExtendedFloatingActionButton,
 * FloatingActionButtonElevation, interactionSource, shadow (one size, no extended form, glass draws its own shadow).
 */
@Composable
private fun RoundStartDraftButton(onClick: () -> Unit, modifier: Modifier = Modifier, description: String) {
    val glass = LocalGlass.current
    val shape = CircleShape
    val colors = MaterialTheme.colorScheme
    val look = if (glass != null) {
        Modifier.glassZone(shape, glass, lens = 1f).background(colors.primary.copy(alpha = 0.85f), shape)
    } else {
        Modifier.background(colors.primary, shape)
    }
    Box(
        modifier = modifier.defaultMinSize(minWidth = FabMediumContainerSize, minHeight = FabMediumContainerSize)
            .then(look)
            .clickable(role = Role.Button, onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = rememberVectorPainter(ArrowForward),
            contentDescription = description,
            colorFilter = ColorFilter.tint(colors.onPrimary),
            modifier = Modifier.size(FabMediumIconSize)
        )
    }
}

/**
 * WhatsNewView's body without the pinned footer: the button ends the content block, which is centred when shorter
 * than the screen, with no bottom reserve.
 * https://github.com/SvenTiigi/WhatsNewKit/blob/main/Sources/View/WhatsNewView.swift#body
 */
@Composable
private fun WelcomeRoundStartDraft(tiles: List<SetupTile>, onStart: () -> Unit) {
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
                Spacer(Modifier.height(40.dp))
                RoundStartDraftButton(onClick = onStart, description = "Start")
                Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
            }
        }
    }
}
