package com.mtga.app.feature.settings

import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.mtga.app.BuildConfig
import com.mtga.app.data.settings.ThemeMode
import org.koin.androidx.compose.koinViewModel

private enum class OpenDialog { NONE, THEME, FREQUENCY, CLEAR }

/**
 * Settings, grouped by what people come here to change. Every description
 * says what the option does for the reader, in plain words.
 */
@Composable
fun SettingsScreen(
    onOpenDiagnostics: () -> Unit,
    onOpenDebugLog: () -> Unit,
    viewModel: SettingsViewModel = koinViewModel()
) {
    val settings by viewModel.settings.collectAsState()
    val storageBytes by viewModel.storageBytes.collectAsState()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    var dialog by remember { mutableStateOf(OpenDialog.NONE) }

    LaunchedEffect(Unit) { viewModel.measureStorage() }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
    ) {
        Text(
            "Settings",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 4.dp)
        )

        Section("Appearance") {
            SettingRow(
                title = "Theme",
                summary = themeLabel(settings.themeMode),
                onClick = { dialog = OpenDialog.THEME }
            )
            SwitchRow(
                title = "Pure black",
                summary = "Deeper blacks in dark mode. Easier on the battery with OLED screens.",
                checked = settings.pureBlack,
                enabled = settings.themeMode != ThemeMode.LIGHT,
                onChange = viewModel::setPureBlack
            )
            SwitchRow(
                title = "Show counts",
                summary = "Replies, reposts, likes and views under each post.",
                checked = settings.showCounts,
                onChange = viewModel::setShowCounts
            )
        }

        Section("Reading") {
            SwitchRow(
                title = "Newest posts from X",
                summary = "Shows an account's latest posts faster and more reliably. " +
                    "X can see your IP address while this is on.",
                checked = settings.useXcomDirect,
                onChange = viewModel::setXcomDirect
            )
        }

        Section("Background updates") {
            SwitchRow(
                title = "Check for new posts",
                summary = "Keeps Home up to date and saves new posts, even when the app is closed.",
                checked = settings.backgroundSync,
                onChange = viewModel::setBackgroundSync
            )
            SettingRow(
                title = "Frequency",
                summary = intervalLabel(settings.syncIntervalMinutes),
                enabled = settings.backgroundSync,
                onClick = { dialog = OpenDialog.FREQUENCY }
            )
            SwitchRow(
                title = "Wi-Fi only",
                summary = "Uses no mobile data for background checks.",
                checked = settings.syncOnWifiOnly,
                enabled = settings.backgroundSync,
                onChange = viewModel::setWifiOnly
            )
        }

        Section("Storage") {
            SettingRow(
                title = "Saved posts",
                summary = storageBytes?.let {
                    "${Formatter.formatShortFileSize(context, it)} on this phone. Readable offline."
                } ?: "Measuring",
                onClick = null
            )
            SettingRow(
                title = "Clear saved posts",
                summary = "Frees space. The accounts you follow are kept.",
                onClick = { dialog = OpenDialog.CLEAR }
            )
        }

        Section("Help") {
            SettingRow(
                title = "Connection check",
                summary = "See which servers answer right now, and why something does not load.",
                onClick = onOpenDiagnostics
            )
            SettingRow(
                title = "Activity log",
                summary = "Technical details to share when you report a problem.",
                onClick = onOpenDebugLog
            )
        }

        Section("About") {
            SettingRow(
                title = "MTGA ${BuildConfig.VERSION_NAME}",
                summary = "Read public X posts with no account, no tracking and no ads.",
                onClick = null
            )
            SettingRow(
                title = "Source code",
                summary = "github.com/213YaZ786/MTGA",
                onClick = { uriHandler.openUri("https://github.com/213YaZ786/MTGA") }
            )
            SettingRow(
                title = "Thanks",
                summary = "Made possible by Nitter and the people who run its servers.",
                onClick = null
            )
        }

        Spacer(Modifier.height(24.dp))
    }

    when (dialog) {
        OpenDialog.THEME -> ChoiceDialog(
            title = "Theme",
            options = ThemeMode.entries.map { it to themeLabel(it) },
            selected = settings.themeMode,
            onSelect = viewModel::setTheme,
            onDismiss = { dialog = OpenDialog.NONE }
        )
        OpenDialog.FREQUENCY -> ChoiceDialog(
            title = "Check for new posts",
            options = INTERVALS.map { it to intervalLabel(it) },
            selected = settings.syncIntervalMinutes,
            onSelect = viewModel::setInterval,
            onDismiss = { dialog = OpenDialog.NONE }
        )
        OpenDialog.CLEAR -> AlertDialog(
            onDismissRequest = { dialog = OpenDialog.NONE },
            title = { Text("Clear saved posts?") },
            text = {
                Text("Posts saved on this phone will be deleted. They load again the next time you open an account.")
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearSavedPosts()
                    dialog = OpenDialog.NONE
                }) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { dialog = OpenDialog.NONE }) { Text("Cancel") }
            }
        )
        OpenDialog.NONE -> Unit
    }
}

/** A titled group of rows on one rounded card, the Obtainium way. */
@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 28.dp, end = 28.dp, top = 20.dp, bottom = 8.dp)
    )
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
    ) {
        Column(content = content)
    }
}

@Composable
private fun SettingRow(
    title: String,
    summary: String?,
    onClick: (() -> Unit)?,
    enabled: Boolean = true,
    trailing: (@Composable () -> Unit)? = null
) {
    val alpha = if (enabled) 1f else 0.38f
    ListItem(
        headlineContent = { Text(title, color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)) },
        supportingContent = summary?.let {
            { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)) }
        },
        trailingContent = trailing,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick) else Modifier
    )
}

@Composable
private fun SwitchRow(
    title: String,
    summary: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    SettingRow(
        title = title,
        summary = summary,
        enabled = enabled,
        onClick = { onChange(!checked) },
        trailing = { Switch(checked = checked, onCheckedChange = onChange, enabled = enabled) }
    )
}

@Composable
private fun <T> ChoiceDialog(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { (value, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                onSelect(value)
                                onDismiss()
                            }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = value == selected, onClick = null)
                        Text(label, modifier = Modifier.padding(start = 12.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private fun themeLabel(mode: ThemeMode): String = when (mode) {
    ThemeMode.SYSTEM -> "Same as the system"
    ThemeMode.LIGHT -> "Light"
    ThemeMode.DARK -> "Dark"
}

private val INTERVALS = listOf(15, 30, 60, 180, 360)

private fun intervalLabel(minutes: Int): String = when {
    minutes < 60 -> "Every $minutes minutes"
    minutes == 60 -> "Every hour"
    else -> "Every ${minutes / 60} hours"
}
