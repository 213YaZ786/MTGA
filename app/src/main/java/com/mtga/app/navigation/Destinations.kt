package com.mtga.app.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector

/** Top level destinations, the ones reachable from the bar or rail. */
enum class TopDestination(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    TIMELINE("timeline", "Timeline", Icons.Outlined.Home),
    ACCOUNTS("accounts", "Accounts", Icons.Outlined.Groups),
    SEARCH("search", "Search", Icons.Outlined.Search),
    SETTINGS("settings", "Settings", Icons.Outlined.Settings)
}

/** Destinations pushed on top, not part of the bar. */
object Routes {
    const val DIAGNOSTICS = "diagnostics"
    val diagnosticsIcon = Icons.Outlined.MonitorHeart
}
