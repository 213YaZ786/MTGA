package com.mtga.app.feature.diagnostics

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.mtga.app.core.common.AppError
import com.mtga.app.core.common.present
import com.mtga.app.data.instances.HealthStatus
import com.mtga.app.ui.icon.MtgaIcons
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(
    onBack: () -> Unit,
    viewModel: DiagnosticsViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()
    val clipboard = LocalClipboardManager.current
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Diagnostics") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(MtgaIcons.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(MtgaIcons.Add, contentDescription = "Add instance")
                    }
                    IconButton(onClick = viewModel::refresh, enabled = !state.probing) {
                        if (state.probing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(MtgaIcons.Refresh, contentDescription = "Re-check")
                        }
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { OverallBanner(state) }

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
                    TextButton(onClick = {
                        clipboard.setText(AnnotatedString(viewModel.report()))
                    }) { Text("Copy report") }
                    TextButton(onClick = viewModel::restoreDefaults) { Text("Restore defaults") }
                }
            }

            item {
                Text(
                    "MTGA reads public Nitter front ends. Those instances are under legal " +
                        "pressure from X Corp and can disappear without notice. When every " +
                        "instance here is red, that is the upstream situation, not a fault " +
                        "in the app.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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

@Composable
private fun OverallBanner(state: DiagnosticsUiState) {
    val (headline, detail) = when {
        state.probing && state.rows.none { it.health?.lastCheckedAt != null } ->
            "Checking instances" to "Timing a feed request against each one."
        state.anyHealthy ->
            "At least one instance is serving feeds" to "MTGA will use the fastest healthy one first."
        state.allChecked ->
            "No instance is reachable" to "Every enabled instance failed its last check. Details below."
        else ->
            "Not checked yet" to "Pull the refresh button to time each instance."
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(headline, style = MaterialTheme.typography.titleMedium)
            Text(
                detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
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

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
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
                Switch(checked = row.instance.enabled, onCheckedChange = onToggle)
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
                        "Page said: $snippet",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceContainerHigh,
                                RoundedCornerShape(8.dp)
                            )
                            .padding(8.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(onClick = onMoveUp) {
                    Icon(MtgaIcons.ArrowUp, contentDescription = "Try earlier")
                }
                IconButton(onClick = onMoveDown) {
                    Icon(MtgaIcons.ArrowDown, contentDescription = "Try later")
                }
                if (!row.instance.builtIn) {
                    IconButton(onClick = onRemove) {
                        Icon(MtgaIcons.Delete, contentDescription = "Remove")
                    }
                }
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

private fun statusLine(status: HealthStatus, row: InstanceRow): String {
    val latency = row.health?.latencyMillis?.let { " · ${it}ms" }.orEmpty()
    return when (status) {
        HealthStatus.HEALTHY -> "Serving feeds$latency"
        HealthStatus.SLOW -> "Slow but working$latency"
        HealthStatus.DEGRADED -> "Failing$latency"
        HealthStatus.DOWN -> "Down after ${row.health?.consecutiveFailures ?: 0} tries"
        HealthStatus.DISABLED -> "Disabled, not used"
        HealthStatus.UNKNOWN -> "Not checked yet"
    }
}

@Composable
private fun AddInstanceDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var url by remember { mutableStateOf("") }
    var rssUrl by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add an instance") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    singleLine = true,
                    label = { Text("Host") },
                    placeholder = { Text("nitter.example.com") }
                )
                OutlinedTextField(
                    value = rssUrl,
                    onValueChange = { rssUrl = it },
                    singleLine = true,
                    label = { Text("Feed host, if different") },
                    placeholder = { Text("optional") }
                )
                Text(
                    "MTGA only talks to instances over https.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(url, rssUrl) }) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** Status colours kept out of the composable so they can be tweaked in one place. */
object InstanceRowDefaults {
    private val Green = Color(0xFF4CAF50)
    private val Amber = Color(0xFFFFB300)
    private val Red = Color(0xFFE53935)
    private val Grey = Color(0xFF9E9E9E)

    fun colorFor(status: HealthStatus): Color = when (status) {
        HealthStatus.HEALTHY -> Green
        HealthStatus.SLOW -> Amber
        HealthStatus.DEGRADED -> Amber
        HealthStatus.DOWN -> Red
        HealthStatus.DISABLED -> Grey
        HealthStatus.UNKNOWN -> Grey
    }
}
