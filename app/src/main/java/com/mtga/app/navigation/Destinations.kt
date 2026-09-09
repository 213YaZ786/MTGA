package com.mtga.app.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.graphics.vector.ImageVector

/** Top level destinations, the ones reachable from the bar or rail. */
enum class TopDestination(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    TIMELINE("timeline", "Timeline", Icons.Default.Home),
    ACCOUNTS("accounts", "Accounts", Icons.Default.Person),
    SEARCH("search", "Search", Icons.Default.Search),
    SETTINGS("settings", "Settings", Icons.Default.Settings)
}

/** Destinations pushed on top, not part of the bar. */
object Routes {
    const val DIAGNOSTICS = "diagnostics"
    val diagnosticsIcon = Icons.Default.Warning
}
