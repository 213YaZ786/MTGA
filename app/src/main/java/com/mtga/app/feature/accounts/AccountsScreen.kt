package com.mtga.app.feature.accounts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.mtga.app.ui.icon.MtgaIcons
import org.koin.androidx.compose.koinViewModel

@Composable
fun AccountsScreen(
    onOpenFeed: (String) -> Unit,
    viewModel: AccountsViewModel = koinViewModel()
) {
    val accounts by viewModel.accounts.collectAsState()
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    fun submit() {
        if (input.isBlank()) return
        error = viewModel.add(input)
        if (error == null) input = ""
    }

    Column(Modifier.fillMaxSize()) {
        Text(
            "Accounts",
            style = MaterialTheme.typography.displaySmall,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 12.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it; error = null },
                singleLine = true,
                modifier = Modifier.weight(1f),
                label = { Text("Handle") },
                placeholder = { Text("@nytimes") },
                isError = error != null,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    imeAction = ImeAction.Done
                ),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                    onDone = { submit() }
                )
            )
            IconButton(onClick = ::submit) {
                Icon(MtgaIcons.Add, contentDescription = "Follow")
            }
        }

        error?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        if (accounts.isEmpty()) {
            Text(
                "Follow an account to read it. Handles are stored on this device only, " +
                    "and nothing is sent anywhere except the Nitter instance serving the feed.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(24.dp)
            )
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(accounts, key = { it.handle }) { account ->
                    ListItem(
                        headlineContent = { Text("@${account.handle}") },
                        supportingContent = account.displayName?.let { { Text(it) } },
                        trailingContent = {
                            IconButton(onClick = { viewModel.remove(account.handle) }) {
                                Icon(MtgaIcons.Delete, contentDescription = "Unfollow")
                            }
                        },
                        modifier = Modifier.clickable { onOpenFeed(account.handle) }
                    )
                }
            }
        }
    }
}
