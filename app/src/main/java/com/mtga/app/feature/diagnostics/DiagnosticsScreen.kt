package com.mtga.app.feature.diagnostics

import com.mtga.app.ui.component.FloatingTopBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.lazy.rememberLazyListState
import com.mtga.app.ui.component.ZoneAlertDialog
import com.mtga.app.ui.component.ZoneSurface
import com.mtga.app.ui.component.FeatherRing
import com.mtga.app.ui.component.LoadingMark
import com.mtga.app.ui.component.MBBackgroundView
import com.mtga.app.ui.component.animateRing
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.graphicsLayer
import com.mtga.app.ui.component.plus
import com.mtga.app.ui.component.BoldButton
import com.mtga.app.navigation.LocalReadableInset
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import com.mtga.app.ui.component.BoldIconButton
import com.mtga.app.ui.component.GlassSwitch
import com.mtga.app.ui.component.QuietButton
import androidx.compose.material3.ShapeDefaults
import com.mtga.app.ui.component.BarTitle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.materialkolor.blend.Blend
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mtga.app.core.common.AppError
import com.mtga.app.core.common.present
import com.mtga.app.data.instances.HealthStatus
import com.mtga.app.data.instances.InstancePool
import com.mtga.app.ui.component.relativeTime
import com.mtga.app.ui.icon.MtgaIcons
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(
    onBack: () -> Unit,
    viewModel: DiagnosticsViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()
    val listStatus by viewModel.listStatus.collectAsState()
    val clipboard = LocalClipboardManager.current
    var showAddDialog by remember { mutableStateOf(false) }

    // The bar folds away going down and returns going up, the list full screen.
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        // The page's ground is painted under the whole app, see MainActivity.
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
        topBar = {
            // The floating bar, as FeedScreen and FoldersScreen use it: no band behind it, the list fades under it.
            FloatingTopBar(
                scrollBehavior = scrollBehavior,
                title = { BarTitle("Connection check") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(MtgaIcons.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(MtgaIcons.Add, contentDescription = "Add instance")
                    }
                    // Disabled while the servers are checked: the ring in the banner shows the work.
                    IconButton(onClick = viewModel::refresh, enabled = !state.probing) {
                        Icon(MtgaIcons.Refresh, contentDescription = "Re-check")
                    }
                }
            )
        }
    ) { padding ->
        val listState = rememberLazyListState()
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp + LocalReadableInset.current,
                end = 16.dp + LocalReadableInset.current,
                top = 16.dp,
                bottom = 16.dp
            ).plus(padding),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { OverallBanner(state) }

            item(key = "list") {
                ListCard(
                    status = listStatus,
                    count = state.rows.count { it.instance.builtIn },
                    onUpdate = viewModel::updateList
                )
            }

            items(state.rows, key = { it.instance.id }) { row ->
                InstanceCard(
                    row = row,
                    onToggle = { viewModel.setEnabled(row.instance.id, it) },
                    onMoveUp = { viewModel.move(row.instance.id, -1) },
                    onMoveDown = { viewModel.move(row.instance.id, 1) },
                    onRemove = { viewModel.remove(row.instance.id) }
                )
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BoldButton(onClick = {
                        clipboard.setText(AnnotatedString(viewModel.report()))
                    }) { Text("Copy report") }
                    BoldButton(
                        onClick = viewModel::resetFromList,
                        enabled = !listStatus.updating
                    ) { Text("Reset from list") }
                }
            }

            item {
                Text(
                    "MTGA reads public Nitter front ends. Those instances are under legal " +
                        "pressure from X Corp and can disappear without notice. When every " +
                        "instance here is red, that is the upstream situation, not a fault " +
                        "in the app.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    if (showAddDialog) {
        AddInstanceDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { url, rss ->
                viewModel.addCustom(url, rss.ifBlank { null })
                showAddDialog = false
            }
        )
    }
}

/**
 * The count, not a sentence about how MTGA picks a server. This banner is
 * read when something is wrong, so it carries numbers and nothing else.
 */
private fun healthyLine(state: DiagnosticsUiState): String {
    val enabled = state.rows.count { it.instance.enabled }
    val working = state.rows.count {
        it.instance.enabled && it.health?.lastError == null && it.health?.lastCheckedAt != null
    }
    val best = state.rows
        .filter { it.instance.enabled && it.health?.lastError == null }
        .mapNotNull { it.health?.latencyMillis }
        .minOrNull()
    val latency = best?.let { ", fastest $it ms" }.orEmpty()
    return "$working of $enabled servers responding$latency."
}

@Composable
private fun OverallBanner(state: DiagnosticsUiState) {
    val (headline, detail) = when {
        state.rows.isEmpty() ->
            "No servers yet" to "The server list has not arrived. Update it below."
        state.probing && state.rows.none { it.health?.lastCheckedAt != null } ->
            "Checking servers" to "Measuring how each server responds."
        state.anyHealthy ->
            "Reading works" to healthyLine(state)
        state.allChecked ->
            "No server is reachable" to "Every server failed its last check. Details below."
        else ->
            "Not checked yet" to "Tap refresh to check each server."
    }

    // The ring over the check comes in and leaves as the pull's does (animateRing in PullIndicator.kt, from MBProgressHUD's
    // animateIn); the last count is kept while it leaves. Its state, as PullToRefresh's:
    // https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/pulltorefresh/PullToRefresh.kt#PullToRefreshState
    // not ported: animateToHidden (the pull's own settling)
    // not ported: animateToThreshold (the pull's own settling)
    // not ported: snapTo (the state's own)
    val bezelView = remember { MBBackgroundView() }
    val fill = remember { Animatable(0f) }
    val shownState by rememberUpdatedState(state)
    LaunchedEffect(state.probing) {
        animateRing(bezelView, fill, state.probing) {
            if (shownState.enabledCount > 0) shownState.checkedCount.toFloat() / shownState.enabledCount else 0f
        }
    }
    var kept by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    LaunchedEffect(state.probing) { if (state.probing) kept = null }
    LaunchedEffect(state.checkedCount, state.enabledCount, state.probing) {
        if (state.probing) kept = state.checkedCount to state.enabledCount
    }
    val ringOn by remember { derivedStateOf { shownState.probing || bezelView.alpha.value > 0f } }

    ZoneSurface(
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        shape = ShapeDefaults.Large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(Modifier.padding(16.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            // The ring, a determinate CircularProgressIndicator of feathers, zoomed by the bezelView (see above):
            // https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/pulltorefresh/PullToRefresh.kt#PullToRefreshState
            // not ported: animateToHidden (the pull's own settling)
            // not ported: animateToThreshold (the pull's own settling)
            // not ported: snapTo (the state's own)
            if (ringOn) {
                FeatherRing(
                    progress = { fill.value },
                    size = 120.dp,
                    modifier = Modifier.padding(bottom = 8.dp).graphicsLayer {
                        alpha = bezelView.alpha.value
                        scaleX = bezelView.transform.value
                        scaleY = bezelView.transform.value
                    }
                ) {
                    kept?.let { (done, total) ->
                        Text("$done of $total", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                        Text(
                            "servers",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
            Text(headline, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            Text(
                detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun InstanceCard(
    row: InstanceRow,
    onToggle: (Boolean) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit
) {
    val status = row.health?.status(row.instance.enabled) ?: HealthStatus.UNKNOWN
    val presentation = row.health?.lastError?.present()

    ZoneSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = ShapeDefaults.Large,
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(status)
                Column(Modifier.padding(start = 12.dp).weight(1f)) {
                    Text(row.instance.label, style = MaterialTheme.typography.titleMedium)
                    Text(
                        statusLine(status, row),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                GlassSwitch(checked = row.instance.enabled, onCheckedChange = onToggle)
            }

            if (presentation != null && row.instance.enabled) {
                Text(
                    presentation.headline,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Text(
                    presentation.explanation,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                (row.health?.lastError as? AppError.ParseFailure)?.snippet?.let { snippet ->
                    Text(
                        "The server said: $snippet",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceContainerHigh,
                                ShapeDefaults.Small
                            )
                            .padding(8.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                // The round buttons keep a gap so they never touch: spacedBy(TileGap) across the full width, as SMS's PersonActions
                // (AGPL-3.0, same author, https://github.com/213YaZ786/SMS/blob/main/app/src/main/java/com/yaz/sms/ui/component/PersonActions.kt#TileGap).
                horizontalArrangement = Arrangement.spacedBy(TileGap)
            ) {
                BoldIconButton(onClick = onMoveUp) {
                    Icon(MtgaIcons.ArrowUp, contentDescription = "Try earlier")
                }
                BoldIconButton(onClick = onMoveDown) {
                    Icon(MtgaIcons.ArrowDown, contentDescription = "Try later")
                }
                if (!row.instance.builtIn) {
                    BoldIconButton(onClick = onRemove) {
                        Icon(MtgaIcons.Delete, contentDescription = "Remove")
                    }
                }
            }
        }
    }
}

// The gap between the round buttons, SMS's TileGap (AGPL-3.0, same author,
// https://github.com/213YaZ786/SMS/blob/main/app/src/main/java/com/yaz/sms/ui/component/PersonActions.kt#TileGap).
private val TileGap = 10.dp

@Composable
private fun StatusDot(status: HealthStatus) {
    Box(
        modifier = Modifier
            .size(12.dp)
            .background(InstanceRowDefaults.colorFor(status), CircleShape)
    )
}

private fun statusLine(status: HealthStatus, row: InstanceRow): String {
    // Parts joined with a comma, as AOSP Dialer's CallLogEntryText.java (https://android.googlesource.com/platform/packages/apps/Dialer/+/refs/heads/main/java/com/android/dialer/calllogutils/CallLogEntryText.java).
    val latency = row.health?.latencyMillis?.let { ", ${it}ms" }.orEmpty()
    val origin = when {
        !row.instance.builtIn -> ", added by you"
        row.instance.listedWorking == false -> ", marked down on the wiki"
        else -> ""
    }
    return statusText(status, row, latency) + origin
}

private fun statusText(status: HealthStatus, row: InstanceRow, latency: String): String {
    // Said plainly, because it explains both the extra second and why a
    // plain browser check elsewhere would call this server blocked.
    val path = if (row.health?.viaBrowser == true) ", through its browser check" else ""
    val delivered = if (row.health?.deliveredAt != null) ", gave posts" else ""
    return when (status) {
        HealthStatus.HEALTHY -> "Working$latency$path$delivered"
        HealthStatus.SLOW -> "Slow but working$latency$path$delivered"
        HealthStatus.DEGRADED -> "Having trouble$latency"
        HealthStatus.DOWN -> "Not responding after ${row.health?.consecutiveFailures ?: 0} tries"
        HealthStatus.DISABLED -> "Turned off"
        HealthStatus.UNKNOWN -> "Not checked yet"
        HealthStatus.CHALLENGED -> "Asks for a quick check"
    }
}

@Composable
private fun AddInstanceDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var url by remember { mutableStateOf("") }
    var rssUrl by remember { mutableStateOf("") }

    ZoneAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add a server") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    singleLine = true,
                    label = { Text("Address") },
                    placeholder = { Text("nitter.example.com") }
                )
                OutlinedTextField(
                    value = rssUrl,
                    onValueChange = { rssUrl = it },
                    singleLine = true,
                    label = { Text("Feed address, if different") },
                    placeholder = { Text("optional") }
                )
                Text(
                    "MTGA only talks to instances over https.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = { BoldButton(onClick = { onConfirm(url, rssUrl) }, filled = true) { Text("Add") } },
        dismissButton = { QuietButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** Status colours kept out of the composable so they can be tweaked in one place. */
object InstanceRowDefaults {
    private val Green = Color(0xFF4CAF50)
    private val Amber = Color(0xFFFFB300)
    private val Red = Color(0xFFE53935)

    // The design colour's hue moved toward the source colour's, as MaterialColors.harmonizeWithPrimary does with
    // the primary, through the materialkolor port of
    // https://github.com/material-foundation/material-color-utilities/blob/main/java/blend/Blend.java#harmonize
    private fun harmonize(designColor: Color, sourceColor: Color): Color =
        Color(Blend.harmonize(designColor.toArgb(), sourceColor.toArgb()))

    @Composable
    fun colorFor(status: HealthStatus): Color {
        val primary = MaterialTheme.colorScheme.primary
        return when (status) {
            HealthStatus.HEALTHY -> harmonize(Green, primary)
            HealthStatus.SLOW -> harmonize(Amber, primary)
            HealthStatus.DEGRADED -> harmonize(Amber, primary)
            HealthStatus.DOWN -> harmonize(Red, primary)
            HealthStatus.DISABLED -> MaterialTheme.colorScheme.outline
            HealthStatus.UNKNOWN -> MaterialTheme.colorScheme.outline
            HealthStatus.CHALLENGED -> harmonize(Amber, primary)
        }
    }
}

/**
 * Where the server list comes from and how old it is. MTGA ships no list, it
 * reads the Nitter wiki at every launch, so this is the only place that says so.
 */
@Composable
private fun ListCard(status: InstancePool.ListStatus, count: Int, onUpdate: () -> Unit) {
    ZoneSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = ShapeDefaults.Large,
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(Modifier.padding(16.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Server list", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            val age = status.updatedAtMillis?.let { relativeTime(it) }
            Text(
                when {
                    status.updating -> "Updating from the Nitter wiki"
                    age == null -> "Not fetched yet. It comes from the Nitter wiki."
                    age.isEmpty() || age == "now" ->
                        "From the Nitter wiki, updated just now, $count servers"
                    else -> "From the Nitter wiki, updated $age ago, $count servers"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            status.lastError?.let { error ->
                Text(
                    "Last update failed: " + error.present().headline,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 4.dp)
            ) {
                BoldButton(onClick = onUpdate, enabled = !status.updating) { Text("Update now") }
                if (status.updating) {
                    LoadingMark(size = 20.dp)
                }
            }
        }
    }
}
