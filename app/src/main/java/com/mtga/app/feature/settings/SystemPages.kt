package com.mtga.app.feature.settings

import android.content.Context
import android.content.Intent
import android.content.pm.verify.domain.DomainVerificationManager
import android.content.pm.verify.domain.DomainVerificationUserState
import android.net.Uri
import android.provider.Settings

/*
 * The Android settings pages MTGA sends the reader to, shared by Settings and the welcome guide (moved out of
 * SettingsScreen.kt so neither copies them).
 */

// not ported: openScreen (AntennaPod's in-app preference screens: only its notification page branch is used here, below)

/**
 * Whether Android sends x.com links to MTGA. They can only be enabled by the
 * reader, since only X could verify the domain, so this reads the reader's
 * choice rather than a verification.
 * Moved as it was from
 * https://github.com/213YaZ786/MTGA/blob/main/app/src/main/java/com/mtga/app/feature/settings/SettingsScreen.kt#xLinksEnabled
 */
internal fun xLinksEnabled(context: Context): Boolean {
    val manager = context.getSystemService(DomainVerificationManager::class.java) ?: return false
    val state = runCatching { manager.getDomainVerificationUserState(context.packageName) }
        .getOrNull() ?: return false
    if (!state.isLinkHandlingAllowed) return false
    val xState = state.hostToStateMap["x.com"]
    return xState == DomainVerificationUserState.DOMAIN_STATE_SELECTED ||
        xState == DomainVerificationUserState.DOMAIN_STATE_VERIFIED
}

/**
 * MTGA's own notification page in Android settings: ACTION_APP_NOTIFICATION_SETTINGS with EXTRA_APP_PACKAGE, as
 * https://github.com/AntennaPod/AntennaPod/blob/develop/app/src/main/java/de/danoeh/antennapod/ui/screen/preferences/PreferenceActivity.java#openScreen
 * opens it for its notifications screen. Moved from
 * https://github.com/213YaZ786/MTGA/blob/main/app/src/main/java/com/mtga/app/feature/settings/SettingsScreen.kt#openNotificationSettings
 * with the new task flag kept, since a non-activity context may call it.
 */
internal fun openNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

/**
 * The system screen where supported links are switched on for this app.
 * Moved as it was from
 * https://github.com/213YaZ786/MTGA/blob/main/app/src/main/java/com/mtga/app/feature/settings/SettingsScreen.kt#openLinkSettings
 */
internal fun openLinkSettings(context: Context) {
    val intent = Intent(
        Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS,
        Uri.parse("package:${context.packageName}")
    )
    runCatching { context.startActivity(intent) }
}
