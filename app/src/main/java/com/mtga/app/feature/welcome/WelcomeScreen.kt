/*
 * The welcome guide as tiles, after FlorisBoard's SetupScreen: every step is read from the system (what is enabled,
 * selected, allowed) and shown done or not, the tiles are Modules' SetupTiles. MTGA's own settings logic of the former
 * pager (media choice, updates, new post alerts, the sources) is kept as it was.
 *
 * Copyright (C) 2021-2025 The FlorisBoard Contributors, Apache License 2.0
 * (http://www.apache.org/licenses/LICENSE-2.0), translated from
 * https://github.com/florisboard/florisboard/blob/main/app/src/main/kotlin/dev/patrickgold/florisboard/app/setup/SetupScreen.kt
 */
package com.mtga.app.feature.welcome

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.mtga.app.core.system.BatteryExemption
import com.mtga.app.data.settings.AutoDownload
import com.mtga.app.data.settings.SettingsStore
import com.mtga.app.feature.settings.SOURCE_OPTIONS
import com.mtga.app.feature.settings.SettingsIcons
import com.mtga.app.feature.settings.RefreshDialog
import com.mtga.app.feature.settings.SettingsViewModel
import com.mtga.app.core.update.StoreLink
import com.mtga.app.core.update.Updates
import com.mtga.app.feature.settings.openLinkSettings
import com.mtga.app.feature.settings.openNotificationSettings
import com.mtga.app.feature.settings.xLinksEnabled
import com.mtga.app.sync.NewPostNotifier
import com.mtga.app.ui.component.CheckableDropdownMenuItem
import com.mtga.app.ui.component.DropdownMenuGroup
import com.mtga.app.ui.component.GlassMenu
import com.mtga.app.ui.component.LoadingMark
import com.mtga.app.ui.component.SelectableDropdownMenuItem
import com.mtga.app.ui.component.SetupTile
import com.mtga.app.ui.component.SetupTileDialog
import com.mtga.app.ui.component.SetupTileGrid
import com.mtga.app.ui.component.rememberHaptics
import com.mtga.app.ui.icon.MtgaIcons
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

// not ported: WelcomePage and PAGES (the pager's pages: their texts moved into the tiles' summaries)
// not ported: PageContent, Dots, Point, LinkExample, accentDisc (the pages' own drawing: the pager is gone)
// not ported: MediaChoice, Choices, Option (rows of the pager's pages: the tiles open the app's ChoiceDialog instead)
// not ported: awaitTransition (FlorisBoard's wait on a Flow of the system's change: each state is read again at every resume)
// not ported: flow (FlorisBoard's Flow of step changes: the tiles are read again at every resume instead)
// not ported: Steps (FlorisBoard's sealed step ids: a tile carries its own string id)
// not ported: SetupLinks (FlorisBoard's links of its footer: MTGA's guide has none)
// not ported: SetupScreen (the name only: its header, steps and footer are WelcomeContent, its state logic rememberSetupTiles)

/**
 * The few choices worth making, the best one already set, as tiles: each is read again each time the screen comes
 * back (SetupScreen reads isFlorisBoardEnabled, isFlorisBoardSelected and hasNotificationPermission, and sets the
 * current step from them), and turns done by itself. [onFinish] receives true when the reader asks for Accounts.
 * Ported from rememberSetupTiles of Language
 * https://github.com/213YaZ786/Language/blob/main/app/src/main/java/com/yaz/language/feature/welcome/WelcomeScreen.kt#rememberSetupTiles
 * with the settings logic of MTGA's former WelcomeScreen
 * https://github.com/213YaZ786/MTGA/blob/main/app/src/main/java/com/mtga/app/feature/welcome/WelcomeScreen.kt#WelcomeScreen
 * Only the choices that weigh on the reader (the user, 2026-11-06: a tile only for an important call, the best preset
 * for the rest): Save media (preset to images and videos on Wi-Fi, menu: posts only), Sources (a menu of checks, X sees
 * the reader's address), New posts (a permission and checks with the app closed). None is required. Updates stay on
 * Install through Store, following is done in Accounts.
 */
@Composable
fun rememberSetupTiles(onFinish: (openAccounts: Boolean) -> Unit): List<SetupTile> {
    val settings: SettingsStore = koinInject()
    val context = LocalContext.current
    var checks by remember { mutableIntStateOf(0) }

    // Save media is not required any more (the user, 2026-11-08): a first launch starts on "Images and videos" (saved on
    // Wi-Fi, AutoDownload.UNMETERED), the tile's menu offers "Posts only", and Start never waits. The network choice stays in
    // Settings. Reopened from Settings, or by a reader who chose before, the current choice is kept: the preset is written
    // only while the guide has not been seen and nothing was chosen (the stored default is OFF).
    var chosen by rememberSaveable {
        mutableStateOf(
            settings.current.let { start ->
                if (!start.welcomeSeen && start.autoDownloadMedia == AutoDownload.OFF) AutoDownload.UNMETERED else start.autoDownloadMedia
            }
        )
    }
    LaunchedEffect(Unit) {
        if (!settings.current.welcomeSeen && settings.current.autoDownloadMedia == AutoDownload.OFF && chosen != AutoDownload.OFF) {
            settings.update { it.copy(autoDownloadMedia = chosen, autoDownloadedUntilMillis = 0) }
        }
    }
    // New post notifications start from the current settings and ask for
    // the permission they need when turned on, not before.
    val sync: SettingsViewModel = koinViewModel()
    var notify by remember { mutableStateOf(settings.current.backgroundSync && settings.current.notifyNewPosts) }
    // Refused when the reader turned New posts on: it stays so, shown on its tile, until Android allows it.
    var notifyRefused by rememberSaveable { mutableStateOf(false) }
    // Read again at each resume, since each of these is changed on an Android page: the sources of the tiles below.
    var xLinksOn by remember { mutableStateOf(xLinksEnabled(context)) }
    var exempt by remember { mutableStateOf(BatteryExemption.isExempt(context)) }
    var storePresent by remember { mutableStateOf(StoreLink.present(context)) }
    var installsAllowed by remember { mutableStateOf(Updates.canInstall(context)) }
    // The notification state is NewPostNotifier's own check, so the guide agrees with what a check would do.
    val notifier = remember { NewPostNotifier(context) }
    var blocked by remember { mutableStateOf(!notifier.canNotify()) }
    // Save media's own refusal, as notifyRefused is New posts': set when the permission came back refused.
    var mediaRefused by rememberSaveable { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        checks++
        notify = settings.current.backgroundSync && settings.current.notifyNewPosts
        xLinksOn = xLinksEnabled(context)
        exempt = BatteryExemption.isExempt(context)
        storePresent = StoreLink.present(context)
        installsAllowed = Updates.canInstall(context)
        blocked = !notifier.canNotify()
        if (!blocked) mediaRefused = false
        onPauseOrDispose { }
    }
    // The tile whose menu is open ("media", "sources"), "" when none.
    var choosing by rememberSaveable { mutableStateOf("") }

    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        checks++
        blocked = !notifier.canNotify()
        // The error of Save media shows only once the reader was asked and said no, never before.
        if (!granted && blocked) mediaRefused = true
    }
    val choose: (AutoDownload) -> Unit = { choice ->
        chosen = choice
        // Same as the Settings dialog: the watermark restarts, so what is on
        // screen now is saved on the next refresh.
        settings.update { it.copy(autoDownloadMedia = choice, autoDownloadedUntilMillis = 0) }
        // The progress line of a batch is a notification, and Android 13 and
        // later want the permission for it. Asked here, where saving was asked for.
        if (choice != AutoDownload.OFF &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    val turnOnNotify = {
        notify = true
        notifyRefused = false
        sync.setBackgroundSync(true)
        sync.setNotifyNewPosts(true)
        // Checks run with the app closed: see BatteryExemption.
        BatteryExemption.request(context)
    }
    // Allowed on Android's notification page after a refusal: the reader asked for New posts, so it turns on.
    LifecycleResumeEffect(Unit) {
        if (notifyRefused && !blocked) {
            notifyRefused = false
            turnOnNotify()
        }
        onPauseOrDispose { }
    }
    val askNotify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        blocked = !notifier.canNotify()
        if (granted) turnOnNotify() else {
            // Shown on the tile instead of silently turned off: its tap opens Android's notification page.
            notify = false
            notifyRefused = true
        }
    }
    val chooseNotify: (Boolean) -> Unit = { on ->
        when {
            !on -> {
                notify = false
                sync.setNotifyNewPosts(false)
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED -> askNotify.launch(Manifest.permission.POST_NOTIFICATIONS)
            else -> turnOnNotify()
        }
    }

    // The Save media tile and its menu, ported from the feed pill's menu of TimelineScreen (a GlassMenu under its anchor, one
    // DropdownMenuGroup, SelectableDropdownMenuItem per choice, the one chosen checked)
    // https://github.com/213YaZ786/MTGA/blob/main/app/src/main/java/com/mtga/app/feature/timeline/TimelineScreen.kt#TimelineScreen
    val sources = SourceChoices(settings, open = choosing == "sources", onOpen = { choosing = "sources" }, onClose = { choosing = "" }).single()
    // Refresh at launch: the same dialog and setter as Settings > Reading, so the guide and Settings share one value.
    val refresh by settings.settings.collectAsState()
    return listOf(
        SetupTile(
            id = "media", name = "Save media", title = "Save media automatically", icon = MtgaIcons.Download,
            state = when {
                chosen == AutoDownload.OFF -> "Posts only"
                blocked && mediaRefused -> "Saved, no progress bar"
                else -> "Images and videos"
            },
            summary = "Images and videos you save go to the Pictures and Movies folders. Tap to choose.",
            done = true, required = false,
            // Saving shows its progress as a notification: refused, the tap opens Android's notification page.
            error = chosen != AutoDownload.OFF && blocked && mediaRefused,
            onClick = {
                if (chosen != AutoDownload.OFF && blocked && mediaRefused) openNotificationSettings(context) else choosing = "media"
            },
            // The menu opens under the tile, as the feed pill's menu under its pill: two choices, the one chosen checked.
            // A choice of Wi-Fi or mobile data made in Settings stays: "Images and videos" does not rewrite it.
            menu = {
                GlassMenu(expanded = choosing == "media", onDismissRequest = { choosing = "" }) {
                    DropdownMenuGroup(index = 0, count = 1) {
                        SelectableDropdownMenuItem(
                            selected = chosen != AutoDownload.OFF,
                            onClick = {
                                choosing = ""
                                if (chosen == AutoDownload.OFF) choose(AutoDownload.UNMETERED)
                                // Already selected, never asked yet: this is where the reader can still be asked.
                                else if (blocked && !mediaRefused && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            },
                            text = "Images and videos"
                        )
                        SelectableDropdownMenuItem(
                            selected = chosen == AutoDownload.OFF,
                            onClick = {
                                choosing = ""
                                if (chosen != AutoDownload.OFF) choose(AutoDownload.OFF)
                            },
                            text = "Posts only"
                        )
                    }
                }
            }
        ),
        sources,
        SetupTile(
            id = "refresh", name = "Refresh at launch", title = "Refresh at launch", icon = SettingsIcons.Timer,
            state = refresh.launchRefresh.tile,
            summary = "When the app opens, a feed is read again once its last read is older than this. Tap to choose.",
            done = true, required = false,
            onClick = { choosing = "refresh" },
            menu = {
                if (choosing == "refresh") RefreshDialog(
                    selected = refresh.launchRefresh,
                    onPick = sync::setSyncFrequency,
                    onDismiss = { choosing = "" }
                )
            }
        ),
        // New posts, X links and Updates follow the rows of Settings, which read the same Android states and open the same
        // pages: the notification permission and page, the x.com link state, the Store check and the install page
        // https://github.com/213YaZ786/MTGA/blob/main/app/src/main/java/com/mtga/app/feature/settings/SettingsScreen.kt#SettingsScreen
        SetupTile(
            id = "notify", name = "New posts", title = "New posts", icon = MtgaIcons.Bell,
            state = when {
                notifyRefused && blocked -> "Notifications blocked"
                notify && !exempt -> "Checks may be late"
                notify -> "On"
                else -> "Off"
            },
            summary = when {
                notifyRefused && blocked -> "Android blocks this app's notifications. Tap to open its notification page."
                notify && !exempt -> "Android may hold the checks back while the app is closed. Tap to allow it."
                notify -> "New posts of the accounts you follow. Tap to turn off."
                else -> "New posts of the accounts you follow. Tap to turn on."
            },
            done = notify && exempt, required = false,
            error = notifyRefused && blocked,
            onClick = {
                when {
                    notifyRefused && blocked -> openNotificationSettings(context)
                    notify && !exempt -> BatteryExemption.request(context)
                    else -> chooseNotify(!notify)
                }
            }
        ),
        SetupTile(
            id = "links", name = "X links", title = "X links", icon = SettingsIcons.Link,
            state = if (xLinksOn) "On" else "Off",
            summary = "x.com and twitter.com links tapped anywhere open in MTGA once Android's \"Open supported links\" " +
                "is on for it. Tap to open that page.",
            done = xLinksOn, required = false,
            onClick = { openLinkSettings(context) }
        )
    ) + if (storePresent) emptyList() else listOf(
        // Without Store, the app installs its own updates and Android must allow it.
        SetupTile(
            id = "update", name = "Updates", title = "Updates", icon = SettingsIcons.Update,
            state = if (installsAllowed) "Allowed" else "Installs not allowed",
            summary = if (installsAllowed) "Android lets MTGA install its updates. Tap to open its install page."
            else "Android does not let MTGA install updates. Tap to open its install page.",
            done = installsAllowed, required = false, error = !installsAllowed,
            onClick = { Updates.allowInstalls(context) }
        )
    )
}

/**
 * The sources as one tile, "Sources" ("N of 5", done while one is on): a tap opens a GlassMenu under it with a
 * CheckableDropdownMenuItem per source of SOURCE_OPTIONS, any number of them on; the last one left on cannot be turned off.
 * Ported from
 * https://github.com/213YaZ786/MTGA/blob/main/app/src/main/java/com/mtga/app/feature/welcome/WelcomeScreen.kt#SourceChoices
 * where it gave a tile per source (then one, "Newest from X"); the menu is the feed pill's menu of
 * https://github.com/213YaZ786/MTGA/blob/main/app/src/main/java/com/mtga/app/feature/timeline/TimelineScreen.kt#TimelineScreen
 * with the checks of GlassMenu's CheckableDropdownMenuItem. The summary says what each source sees.
 * not ported: the per-source name and icon table (one tile has one icon, Settings' Dns of its Sources section)
 */
@Composable
private fun SourceChoices(settings: SettingsStore, open: Boolean, onOpen: () -> Unit, onClose: () -> Unit): List<SetupTile> {
    val current by settings.settings.collectAsState()
    val haptics = rememberHaptics()
    val on = SOURCE_OPTIONS.count { it.isOn(current) }
    return listOf(
        SetupTile(
            id = "sources", name = "Sources", title = "Sources", icon = SettingsIcons.Dns,
            state = "$on of ${SOURCE_OPTIONS.size}",
            summary = "Where posts are read from. All of them together find the most posts; each one sees your address, " +
                "and X sees it when its source is on. Tap to tick or untick them. At least one stays on. " +
                "Changeable in Settings.\n\n" + SOURCE_OPTIONS.joinToString("\n") { it.title + ": " + it.summary },
            done = on > 0, required = false,
            onClick = onOpen,
            menu = {
                GlassMenu(expanded = open, onDismissRequest = onClose) {
                    DropdownMenuGroup(index = 0, count = 1) {
                        SOURCE_OPTIONS.forEach { option ->
                            val ticked = option.isOn(current)
                            CheckableDropdownMenuItem(
                                checked = ticked,
                                onCheckedChange = { want ->
                                    // The last source on stays on: a reader with none would find nothing.
                                    if (!want && on == 1) haptics.reject() else settings.update { option.set(it, want) }
                                },
                                text = option.title
                            )
                        }
                    }
                }
            }
        )
    )
}

/**
 * The guide shown on first launch, and again from Settings: the mark, one sentence saying what the app is, the tiles,
 * then Start (Done when reopened), which waits for the required ones if any. [onFinish] receives true when the reader asks
 * to go to Accounts. Ported from SetupScreen#SetupScreen of
 * https://github.com/florisboard/florisboard/blob/main/app/src/main/kotlin/dev/patrickgold/florisboard/app/setup/SetupScreen.kt
 * through Language's
 * https://github.com/213YaZ786/Language/blob/main/app/src/main/java/com/yaz/language/feature/welcome/WelcomeScreen.kt#WelcomeScreen
 * and MTGA's former
 * https://github.com/213YaZ786/MTGA/blob/main/app/src/main/java/com/mtga/app/feature/welcome/WelcomeScreen.kt#WelcomeScreen
 */
@Composable
fun WelcomeScreen(
    onFinish: (openAccounts: Boolean) -> Unit,
    // Start (Done) with the button's centre in root coordinates, for the reveal. The tiles keep onFinish.
    onStart: (origin: Offset) -> Unit = { onFinish(false) }
) {
    val settings: SettingsStore = koinInject()
    val reopened = remember { settings.current.welcomeSeen }
    val steps = rememberSetupTiles(onFinish)
    // The tile whose long press is open (its id), "" when none.
    var explained by rememberSaveable { mutableStateOf("") }

    WelcomeContent(steps, reopened, onStart) { explained = it.id }

    steps.firstOrNull { it.id == explained }?.let { tile ->
        SetupTileDialog(tile, onDismiss = { explained = "" })
    }
}

/**
 * What the guide draws from the tiles it is given: the layout of SetupScreen (header, steps, footer with the one
 * button), split from [WelcomeScreen] as Language's. Ported from
 * https://github.com/213YaZ786/Language/blob/main/app/src/main/java/com/yaz/language/feature/welcome/WelcomeScreen.kt#WelcomeContent
 * and
 * https://github.com/florisboard/florisboard/blob/main/app/src/main/kotlin/dev/patrickgold/florisboard/app/setup/SetupScreen.kt#SetupScreen
 * with the centred content block of
 * https://github.com/SvenTiigi/WhatsNewKit/blob/main/Sources/View/WhatsNewView.swift#body
 * not ported: WhatsNewView's footer band, secondary action sheet, version store, title and feature helper views (the
 * app's title and SetupTileGrid take their place), the pinned footer (Start ends the block, as the approved picture).
 */
@Composable
internal fun WelcomeContent(tiles: List<SetupTile>, reopened: Boolean, onStart: (origin: Offset) -> Unit, onExplain: (SetupTile) -> Unit = {}) {
    val steps = tiles
    // WhatsNewView's body: the scrolling content is a block centred in the window when shorter than it (the viewport is
    // the minimum height of the block). Drawn over the whole window, so it keeps clear of the system bars itself.
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val screen = maxHeight
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .widthIn(max = 720.dp).fillMaxWidth().heightIn(min = screen)
                    .windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 8.dp)
            ) {
                // The old guide's mark, turning at its size (https://github.com/213YaZ786/MTGA/blob/main/app/src/main/java/com/mtga/app/feature/welcome/WelcomeScreen.kt#PageContent).
                LoadingMark(size = 160.dp)
                Spacer(Modifier.height(16.dp))
                // The model of every app's guide (the user, 2026-11-08): the mark, one sentence saying what the app is, the
                // tiles, Start. No title, no "Welcome", no "N left": each tile shows its own check, none is required.
                Text(
                    "X reader",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(Modifier.height(24.dp))
                SetupTileGrid(steps, onExplain)
                Spacer(Modifier.height(40.dp))
                StartCookie(description = if (reopened) "Done" else "Start", onClick = onStart)
                Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
            }
        }
    }
}
