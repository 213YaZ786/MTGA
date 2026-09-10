package com.mtga.app.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mtga.app.BuildConfig
import com.mtga.app.ui.icon.MtgaIcons

@Composable
fun SettingsScreen(
    onOpenDiagnostics: () -> Unit,
    onOpenDebugLog: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.displaySmall,
            modifier = Modifier.padding(24.dp)
        )

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
