package com.mtga.app.navigation

import androidx.compose.ui.graphics.vector.ImageVector
import com.mtga.app.ui.icon.MtgaIcons

/** Top level destinations, the ones reachable from the bar or rail. */
enum class TopDestination(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    TIMELINE("timeline", "Timeline", MtgaIcons.Home),
    ACCOUNTS("accounts", "Accounts", MtgaIcons.Person),
    SEARCH("search", "Search", MtgaIcons.Search),
    SETTINGS("settings", "Settings", MtgaIcons.Settings)
}

/** Destinations pushed on top, not part of the bar. */
object Routes {
    const val DIAGNOSTICS = "diagnostics"
    val diagnosticsIcon: ImageVector = MtgaIcons.Pulse
}
