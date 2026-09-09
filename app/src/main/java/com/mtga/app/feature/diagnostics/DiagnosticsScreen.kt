package com.mtga.app.feature.diagnostics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mtga.app.core.common.Blame

/**
 * Step 1 renders the shell and the legend. Step 2 fills it with live probe
 * results from the instance pool.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Diagnostics") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
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
            item {
                Text(
                    "When MTGA cannot show content it names the layer that failed. " +
                        "Live instance probes arrive in step 2.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            items(Blame.entries) { blame ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(blame.title(), style = MaterialTheme.typography.titleMedium)
                        Text(
                            blame.description(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

private fun Blame.title(): String = when (this) {
    Blame.DEVICE -> "Your device"
    Blame.NETWORK -> "Your network"
    Blame.INSTANCE -> "The Nitter instance"
    Blame.UPSTREAM -> "X itself"
    Blame.APP -> "MTGA"
}

private fun Blame.description(): String = when (this) {
    Blame.DEVICE -> "No connectivity. Cached content stays readable."
    Blame.NETWORK -> "DNS or TLS failed. Something between you and the instance is blocking or intercepting."
    Blame.INSTANCE -> "The instance refused, rate limited, timed out or errored. MTGA fails over to the next one in the pool."
    Blame.UPSTREAM -> "The account is suspended, protected, renamed or deleted. No front end can show it."
    Blame.APP -> "MTGA parsed a page it no longer recognises, or local storage failed. The fallback viewer still works."
}
