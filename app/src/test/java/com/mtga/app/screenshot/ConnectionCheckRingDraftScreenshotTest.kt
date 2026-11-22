package com.mtga.app.screenshot

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShapeDefaults
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mtga.app.data.instances.HealthStatus
import com.mtga.app.feature.diagnostics.InstanceRowDefaults
import com.mtga.app.navigation.LocalReadableInset
import com.mtga.app.ui.component.BarTitle
import com.mtga.app.ui.component.FeatherRing
import com.mtga.app.ui.component.FloatingTopBar
import com.mtga.app.ui.component.GlassSwitch
import com.mtga.app.ui.component.ZoneSurface
import com.mtga.app.ui.component.plus
import com.mtga.app.ui.component.scrolledFromTop
import com.mtga.app.ui.component.underFloatingTopBar
import com.mtga.app.ui.icon.MtgaIcons
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * DRAFT for the user's yes (requests.md point 30), nothing of app/src/main changes: the Connection check while the
 * servers are checked. The Re-check button of the top bar keeps its Refresh icon, disabled (no LoadingMark in the bar);
 * the banner shows the FeatherRing of the pull (point 25), 120 dp, "8 of 20" and "servers" in its centre, progress 0.4.
 * The screen is DiagnosticsScreen's frame, a faithful copy with fixed data (a dozen servers, some checked):
 * https://github.com/213YaZ786/MTGA/blob/main/app/src/main/java/com/mtga/app/feature/diagnostics/DiagnosticsScreen.kt#DiagnosticsScreen
 * and its OverallBanner, InstanceCard and StatusDot. [DiagnosticsDraftScreen] is shared with the point 31 draft
 * (ScrollFullScreenDraftScreenshotTest).
 * not ported: DiagnosticsScreen's view model and clipboard, the Add instance dialog, ListCard (the server list's card),
 * the Copy report and Reset buttons, InstanceCard's error text, move and remove buttons, healthyLine, statusText.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = android.app.Application::class, sdk = [36])
class ConnectionCheckRingDraftScreenshotTest : ScreenshotBase() {

    @Test
    fun draft() = capture("connection_check_ring_draft") { DiagnosticsDraftScreen(checking = true) }

    // Edge: at 200 % text and on a 360 dp phone.
    @Test
    fun edge() = captureEdge("connection_check_ring_draft") { DiagnosticsDraftScreen(checking = true) }
}

/** One server of the draft: its name, its status and the line under the name. */
internal data class DraftServer(val label: String, val status: HealthStatus, val line: String)

internal val draftServers = listOf(
    DraftServer("nitter.net", HealthStatus.HEALTHY, "Working, 412ms, gave posts"),
    DraftServer("nitter.poast.org", HealthStatus.HEALTHY, "Working, 640ms"),
    DraftServer("xcancel.com", HealthStatus.CHALLENGED, "Asks for a quick check"),
    DraftServer("nitter.privacyredirect.com", HealthStatus.SLOW, "Slow but working, 2310ms"),
    DraftServer("nitter.space", HealthStatus.DOWN, "Not responding after 3 tries"),
    DraftServer("nitter.tiekoetter.com", HealthStatus.HEALTHY, "Working, 520ms"),
    DraftServer("lightbrd.com", HealthStatus.DEGRADED, "Having trouble, 3100ms"),
    DraftServer("nitter.catsarch.com", HealthStatus.HEALTHY, "Working, 780ms"),
    DraftServer("nitter.kareem.one", HealthStatus.UNKNOWN, "Not checked yet"),
    DraftServer("nitter.dashy.a3x.dev", HealthStatus.UNKNOWN, "Not checked yet"),
    DraftServer("nitter.adminforge.de", HealthStatus.UNKNOWN, "Not checked yet"),
    DraftServer("nitter.projectsegfau.lt", HealthStatus.DISABLED, "Turned off")
)

/**
 * DiagnosticsScreen with fixed data. [checking] draws the banner as OverallBanner does while the servers are checked,
 * with the ring, and the Re-check button disabled; [firstItem] scrolls the list that many items down; [fade] keeps
 * the list's soft edge under the top bar (underFloatingTopBar), off for the full screen scroll of point 31.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun DiagnosticsDraftScreen(checking: Boolean, firstItem: Int = 0, fade: Boolean = true, foldedBy: Float? = null) {
    // [foldedBy] is how far the bar has folded away, as enterAlwaysScrollBehavior's heightOffset runs from 0 (in place) to
    // heightOffsetLimit (minus the bar's height, fully folded): 1 is fully folded, 0 in place. Material's own state did not
    // settle in Robolectric's frames (the bar stayed out of the picture at half), so the offset is set on the bar's layer.
    Scaffold(
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
        topBar = {
            FloatingTopBar(
                modifier = Modifier.graphicsLayer { translationY = -(foldedBy ?: 0f) * size.height },
                title = { BarTitle("Connection check") },
                navigationIcon = {
                    // Material's IconButton: a 48 dp target, the icon 24 dp in the bar's content colour.
                    Box(Modifier.size(48.dp).clickable(onClick = {}), contentAlignment = Alignment.Center) {
                        Icon(MtgaIcons.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Box(Modifier.size(48.dp).clickable(onClick = {}), contentAlignment = Alignment.Center) {
                        Icon(MtgaIcons.Add, contentDescription = "Add instance")
                    }
                    // Kept as it is, disabled while the servers are checked: the ring in the banner shows the work.
                    Box(
                        Modifier.size(48.dp).alpha(if (checking) 0.38f else 1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(MtgaIcons.Refresh, contentDescription = "Re-check")
                    }
                }
            )
        }
    ) { padding ->
        val listState = rememberLazyListState(initialFirstVisibleItemIndex = firstItem)
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().then(
                if (fade) Modifier.underFloatingTopBar(
                    barHeight = padding.calculateTopPadding(),
                    scrolled = listState::scrolledFromTop
                ) else Modifier
            ),
            contentPadding = PaddingValues(
                start = 16.dp + LocalReadableInset.current,
                end = 16.dp + LocalReadableInset.current,
                top = 16.dp,
                bottom = 16.dp
            ).plus(padding),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { OverallBanner(checking) }
            items(draftServers, key = { it.label }) { InstanceCard(it) }
        }
    }
}

@Composable
private fun OverallBanner(checking: Boolean) {
    ZoneSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = ShapeDefaults.Large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(Modifier.padding(16.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            if (checking) {
                FeatherRing(progress = { 0.4f }, size = 120.dp, modifier = Modifier.padding(bottom = 8.dp)) {
                    Text("8 of 20", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                    Text(
                        "servers",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
            Text(
                if (checking) "Checking servers" else "Reading works",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
            Text(
                if (checking) "Measuring how each server responds." else "5 of 11 servers responding, fastest 412 ms.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun InstanceCard(server: DraftServer) {
    ZoneSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = ShapeDefaults.Large,
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(server.status)
                Column(Modifier.padding(start = 12.dp).weight(1f)) {
                    Text(server.label, style = MaterialTheme.typography.titleMedium)
                    Text(
                        server.line,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                GlassSwitch(checked = server.status != HealthStatus.DISABLED, onCheckedChange = {})
            }
        }
    }
}

@Composable
private fun StatusDot(status: HealthStatus) {
    Box(
        modifier = Modifier
            .size(12.dp)
            .background(InstanceRowDefaults.colorFor(status), CircleShape)
    )
}
