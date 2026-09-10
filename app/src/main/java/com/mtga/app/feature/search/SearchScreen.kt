package com.mtga.app.feature.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mtga.app.core.model.FollowedAccount
import com.mtga.app.ui.icon.MtgaIcons

/**
 * Opens any public account by handle, without following it. The feed screen
 * then offers Follow. Searching post text over the saved history comes later
 * (roadmap step 6), which is why this screen only takes a handle for now.
 */
@Composable
fun SearchScreen(onOpenFeed: (String) -> Unit) {
    var input by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    fun submit() {
        if (input.isBlank()) return
        val handle = FollowedAccount.normalise(input)
        if (handle == null) {
            error = "Not a valid handle. Letters, digits and underscores, 15 at most."
        } else {
            error = null
            onOpenFeed(handle)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            MtgaIcons.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
        Text("Find an account", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Read any public account before deciding to follow it.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        OutlinedTextField(
            value = input,
            onValueChange = { input = it; error = null },
            singleLine = true,
            modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth(),
            label = { Text("Handle") },
            placeholder = { Text("@nytimes") },
            isError = error != null,
            supportingText = { error?.let { Text(it) } },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { submit() })
        )
        Button(onClick = ::submit, enabled = input.isNotBlank()) {
            Text("Open profile")
        }
    }
}
