package com.mtga.app.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mtga.app.BuildConfig
import com.mtga.app.ui.icon.MtgaIcons
import org.koin.androidx.compose.koinViewModel

@Composable
fun SettingsScreen(
    onOpenDiagnostics: () -> Unit,
    onOpenDebugLog: () -> Unit,
    viewModel: SettingsViewModel = koinViewModel()
) {
    val settings by viewModel.settings.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.displaySmall,
            modifier = Modifier.padding(24.dp)
        )

        SectionHeader("Sources")

        ListItem(
            headlineContent = { Text("Read the newest posts from x.com") },
            supportingContent = {
                Text(
                    "The most accurate source for recent posts, straight from X, with no " +
                        "account. The trade is that X sees this device's address and which " +
                        "profiles it opens. Turn this off and MTGA uses only Nitter instances, " +
                        "which proxy that away."
                )
            },
            trailingContent = {
                Switch(
                    checked = settings.useXcomDirect,
                    onCheckedChange = viewModel::setXcomDirect
                )
            }
        )

        SectionHeader("History")

        ListItem(
            headlineContent = { Text("Collect in the background") },
            supportingContent = {
                Text(
                    "X only shows the newest few posts to logged out visitors, and there is no " +
                        "way to page further back. Checking regularly and keeping everything " +
                        "seen builds an archive going forward instead."
                )
            },
            trailingContent = {
                Switch(
                    checked = settings.backgroundSync,
                    onCheckedChange = viewModel::setBackgroundSync
                )
            }
        )

        if (settings.backgroundSync) {
            ListItem(
                headlineContent = { Text("How often") },
                supportingContent = { Text(intervalLabel(settings.syncIntervalMinutes)) },
                modifier = Modifier.clickable {
                    viewModel.setInterval(nextInterval(settings.syncIntervalMinutes))
                }
            )
            ListItem(
                headlineContent = { Text("Only on wifi") },
                supportingContent = { Text("Avoids spending mobile data on posts you may not read") },
                trailingContent = {
                    Switch(
                        checked = settings.syncOnWifiOnly,
                        onCheckedChange = viewModel::setWifiOnly
                    )
                }
            )
        }

        SectionHeader("Troubleshooting")

        ListItem(
            headlineContent = { Text("Diagnostics") },
            supportingContent = { Text("Instance health, last errors, connectivity report") },
            leadingContent = { Icon(MtgaIcons.Pulse, contentDescription = null) },
            modifier = Modifier.clickable(onClick = onOpenDiagnostics)
        )

        ListItem(
            headlineContent = { Text("Request log") },
            supportingContent = {
                Text("Every request and response, copyable and exportable as a text file")
            },
            leadingContent = { Icon(MtgaIcons.Download, contentDescription = null) },
            modifier = Modifier.clickable(onClick = onOpenDebugLog)
        )

        ListItem(
            headlineContent = { Text("Version") },
            supportingContent = { Text("${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})") },
            leadingContent = { Icon(MtgaIcons.Info, contentDescription = null) }
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHigh)
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp)
    )
}

private fun intervalLabel(minutes: Int): String = when {
    minutes < 60 -> "Every $minutes minutes"
    minutes == 60 -> "Every hour"
    else -> "Every ${minutes / 60} hours"
}

/** Tap cycles through sensible values rather than opening a picker. */
private fun nextInterval(current: Int): Int = when (current) {
    15 -> 30
    30 -> 60
    60 -> 180
    180 -> 360
    else -> 15
}
