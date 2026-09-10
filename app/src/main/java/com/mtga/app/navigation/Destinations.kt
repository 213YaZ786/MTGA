package com.mtga.app.navigation

import androidx.compose.ui.graphics.vector.ImageVector
import com.mtga.app.ui.icon.MtgaIcons

/** Top level destinations, the ones reachable from the bar or rail. */
enum class TopDestination(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    TIMELINE("timeline", "Home", MtgaIcons.Home),
    ACCOUNTS("accounts", "Accounts", MtgaIcons.Person),
    SETTINGS("settings", "Settings", MtgaIcons.Settings)
}

/** Destinations pushed on top, not part of the bar. */
object Routes {
    const val DIAGNOSTICS = "diagnostics"
    const val FEED_PATTERN = "feed/{handle}"
    const val DEBUG_LOG = "debuglog"

    const val POST_PATTERN = "post/{id}?from={from}"

    fun feed(handle: String): String = "feed/$handle"

    /** [from] is the account whose cache holds the post, a lookup hint. */
    fun post(id: String, from: String): String = "post/$id?from=$from"

    val diagnosticsIcon: ImageVector = MtgaIcons.Pulse
}
