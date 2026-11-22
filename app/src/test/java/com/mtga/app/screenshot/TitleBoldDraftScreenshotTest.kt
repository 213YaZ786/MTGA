package com.mtga.app.screenshot

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mtga.app.ui.component.BarTitle
import com.mtga.app.ui.component.FloatingTop
import com.mtga.app.ui.component.FloatingTopBar
import com.mtga.app.ui.component.TopActionSize
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * DRAFT for the user's yes (requests.md point 33), nothing of app/src/main changes: the screen titles in bold.
 * Two rows, today over proposed: Folders' FloatingTopBar (BarTitle, titleLarge) and Settings' FloatingTop
 * (headlineSmall in the accent). The proposed ones are local copies of BarTitle and of FloatingTop's title with
 * fontWeight = Bold, everything else unchanged.
 * Compared: Bluesky's TitleText (font_bold in its wide panel, semi_bold on phones), Material 3 Expressive's
 * TitleLargeEmphasized (Medium), the profile name in ProfileHeader (bold).
 * https://github.com/bluesky-social/social-app/blob/main/src/components/Layout/Header/index.tsx#TitleText
 * https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/tokens/TypographyTokens.kt#TitleLargeEmphasized
 * not ported: TitleText's breakpoint and split view branches, the platform alignment (this app centres always),
 * emoji and maxFontSizeMultiplier (autoSize bounds the size here); TitleMediumEmphasized and the other Emphasized styles
 * of the file (not a title of this app)
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = android.app.Application::class, sdk = [36])
class TitleBoldDraftScreenshotTest : ScreenshotBase() {

    @Test
    fun draft() = capture("title_bold_draft") { Titles() }

    @Test
    fun edge() = captureEdge("title_bold_draft") { Titles() }

    @Composable
    private fun Titles() {
        Column(Modifier.fillMaxWidth()) {
            Caption("Folders, today")
            FloatingTopBar(title = { BarTitle("Folders") })
            Caption("Folders, proposed")
            FloatingTopBar(title = { BoldBarTitle("Folders") })
            Spacer(Modifier.height(16.dp))
            Caption("Settings, today")
            FloatingTop("Settings")
            Caption("Settings, proposed")
            BoldFloatingTop("Settings")
        }
    }

    @Composable
    private fun Caption(text: String) {
        Text(text, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 16.dp))
    }

    // BarTitle (FloatingTopBar.kt:61-72) with the weight set.
    @Composable
    private fun BoldBarTitle(text: String) {
        val style = MaterialTheme.typography.titleLarge
        BasicText(
            text,
            modifier = Modifier.semantics { heading() },
            style = style.copy(color = LocalContentColor.current, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            autoSize = TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = style.fontSize, stepSize = 0.5.sp)
        )
    }

    // FloatingTop's title row (FloatingPane.kt:128-173) with the weight set, no action, no collapse.
    @Composable
    private fun BoldFloatingTop(title: String) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = TopActionSize + 16.dp).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(TopActionSize))
            Box(Modifier.weight(1f).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                BasicText(
                    title,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    autoSize = TextAutoSize.StepBased(
                        minFontSize = 12.sp,
                        maxFontSize = MaterialTheme.typography.headlineSmall.fontSize,
                        stepSize = 0.5.sp
                    ),
                    modifier = Modifier.semantics { heading() }
                )
            }
            Box(Modifier.size(TopActionSize))
        }
    }
}
