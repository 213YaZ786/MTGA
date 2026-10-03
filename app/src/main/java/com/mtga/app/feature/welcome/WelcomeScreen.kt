package com.mtga.app.feature.welcome

import androidx.compose.foundation.layout.systemBarsPadding
import com.mtga.app.core.update.UpdateMode
import com.mtga.app.core.update.Updates
import com.mtga.app.feature.settings.SettingsViewModel
import com.mtga.app.feature.settings.SOURCE_OPTIONS
import androidx.compose.runtime.collectAsState
import org.koin.androidx.compose.koinViewModel
import com.mtga.app.core.system.BatteryExemption
import com.mtga.app.ui.component.LoadingMark
import com.mtga.app.ui.component.QuietButton
import com.mtga.app.ui.glass.glassZone
import com.mtga.app.ui.glass.LocalGlass
import com.mtga.app.ui.component.BoldButton
import com.mtga.app.ui.component.ZoneSurface
import com.mtga.app.ui.component.rememberHaptics
import org.koin.compose.koinInject
import com.mtga.app.data.settings.SettingsStore
import com.mtga.app.data.settings.AutoDownload
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import android.os.Build
import android.content.pm.PackageManager
import android.Manifest
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.mtga.app.ui.icon.MtgaIcons
import kotlinx.coroutines.launch

private data class WelcomePage(
    val icon: ImageVector,
    val title: String,
    val intro: String,
    val points: List<String>,
    /** Shows "x.com/nytimes" with the handle picked out. */
    val showLinkExample: Boolean = false,
    /** The last page asks for a decision instead of explaining anything. */
    val showMediaChoice: Boolean = false,
    /** The app's own animation, large, in place of the icon. */
    val showMark: Boolean = false,
    val showUpdateChoice: Boolean = false,
    val showNotifyChoice: Boolean = false,
    val showSourceChoice: Boolean = false
)

private val PAGES = listOf(
    WelcomePage(
        showMark = true,
        icon = MtgaIcons.Home,
        title = "Welcome to MTGA",
        intro = "Read public posts from X with no account, no tracking and no ads.",
        points = listOf(
            "You choose the accounts to follow. The list stays on this device.",
            "Home gathers their newest posts in one timeline."
        )
    ),
    WelcomePage(
        icon = MtgaIcons.Person,
        title = "Find a handle",
        intro = "Every X account has a handle, the name written after the @. For example @nytimes.",
        points = listOf(
            "On a profile, it sits right under the display name.",
            "In a profile link, it comes right after x.com/ as below.",
            "It is made of letters, digits and underscores, 15 at most."
        ),
        showLinkExample = true
    ),
    WelcomePage(
        icon = MtgaIcons.Search,
        title = "Follow an account",
        intro = "Three ways to add someone.",
        points = listOf(
            "In Accounts, type the handle or paste the profile link in the search bar, then tap Follow.",
            "In the X app or a browser, share a profile to MTGA. It opens here, then tap Follow.",
            "Coming from Fritter or Squawker? Import your list in Settings, under Data. Their groups become folders."
        )
    ),
    WelcomePage(
        icon = MtgaIcons.Folder,
        title = "Sort into folders",
        intro = "Folders give one stream each, news in one, friends in another.",
        points = listOf(
            "Create them in Accounts with the folder button, then file each account with its chip.",
            "Home switches between them from its title, and shows every account by default.",
            "Everything loads when the app opens, so switching folders is instant."
        )
    ),
    WelcomePage(
        icon = MtgaIcons.Search,
        title = "Sources",
        intro = "Where posts are read. All of them together find the most.",
        points = listOf("Tap one to leave it out. Changeable in Settings."),
        showSourceChoice = true
    ),
    WelcomePage(
        icon = MtgaIcons.Download,
        title = "Save media automatically",
        intro = "Posts saved on the phone open again offline.",
        points = listOf("Changeable in Settings."),
        showMediaChoice = true
    ),
    WelcomePage(
        icon = MtgaIcons.Refresh,
        title = "Updates",
        intro = "When a new version is out.",
        points = listOf("Changeable in Settings."),
        showUpdateChoice = true
    ),
    WelcomePage(
        icon = MtgaIcons.Bell,
        title = "New posts",
        intro = "A notification when followed accounts post.",
        points = listOf("Changeable in Settings."),
        showNotifyChoice = true
    )
)

/**
 * A short guide shown on first launch, and again from Settings. Five pages:
 * what MTGA is, what a handle is and where to find one, how to follow, how to
 * sort into folders, and the one decision that spends the reader's data.
 *
 * [onFinish] receives true when the reader asks to go to Accounts.
 */
@Composable
fun WelcomeScreen(onFinish: (openAccounts: Boolean) -> Unit) {
    val pager = rememberPagerState(pageCount = { PAGES.size })
    val scope = rememberCoroutineScope()
    val last = pager.currentPage == PAGES.lastIndex
    val settings: SettingsStore = koinInject()
    val context = LocalContext.current
    // On first launch nothing is picked: this is the only setting that spends
    // the reader's data without asking again, so it is not left as a default
    // they never saw. Reopened from Settings, the current choice is shown.
    // LinkedOut starts blank every time, which locked the last button for a
    // reader who had already chosen.
    var chosen by remember {
        mutableStateOf(settings.current.takeIf { it.welcomeSeen }?.autoDownloadMedia)
    }
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
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

    // Updates and new post notifications start from the current settings;
    // each asks for the permission it needs when turned on, not before.
    var updates by remember { mutableStateOf(settings.current.updates) }
    val chooseUpdates: (UpdateMode) -> Unit = { mode ->
        updates = mode
        settings.update { it.copy(updates = mode) }
        if (mode == UpdateMode.INSTALL && !Updates.canInstall(context)) Updates.allowInstalls(context)
    }
    val sync: SettingsViewModel = koinViewModel()
    var notify by remember { mutableStateOf(settings.current.backgroundSync && settings.current.notifyNewPosts) }
    val turnOnNotify = {
        notify = true
        sync.setBackgroundSync(true)
        sync.setNotifyNewPosts(true)
        // Checks run with the app closed: see BatteryExemption.
        BatteryExemption.request(context)
    }
    val askNotify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) turnOnNotify() else notify = false
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

    // Drawn over the whole window, so it keeps clear of the system bars itself.
    Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 24.dp, vertical = 16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            QuietButton(onClick = { onFinish(false) }) { Text(if (last) "Close" else "Skip") }
        }

        HorizontalPager(
            state = pager,
            modifier = Modifier.weight(1f).fillMaxWidth()
        ) { index ->
            val page = PAGES[index]
            PageContent(page) {
                when {
                    page.showMediaChoice -> MediaChoice(chosen = chosen, onChoose = choose)
                    page.showUpdateChoice -> Choices(
                        listOf(UpdateMode.OFF to "Off", UpdateMode.NOTIFY to "Notify me", UpdateMode.INSTALL to "Install"),
                        updates,
                        chooseUpdates
                    )
                    page.showNotifyChoice -> Choices(listOf(false to "Off", true to "Notify me"), notify, chooseNotify)
                    page.showSourceChoice -> SourceChoices(settings)
                }
            }
        }

        Dots(count = PAGES.size, current = pager.currentPage)

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (pager.currentPage > 0) {
                QuietButton(onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } }) {
                    Text("Back")
                }
            }
            Spacer(Modifier.weight(1f))
            BoldButton(
                filled = true,
                // The media page has no way forward until the choice is made.
                // Skip, top right, still leaves at any time.
                enabled = !PAGES[pager.currentPage].showMediaChoice || chosen != null,
                onClick = {
                    // Install is chosen from the start, and Android lets an app
                    // install only once allowed: moving on from that page asks.
                    if (PAGES[pager.currentPage].showUpdateChoice && updates == UpdateMode.INSTALL && !Updates.canInstall(context)) {
                        Updates.allowInstalls(context)
                    }
                    if (last) onFinish(true) else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                }
            ) { Text(if (last) "Go to Accounts" else "Next") }
        }
    }
}

@Composable
private fun PageContent(page: WelcomePage, choice: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
    ) {
        if (page.showMark) {
            LoadingMark(size = 160.dp)
        } else {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .accentDisc(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    page.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(40.dp)
                )
            }
        }
        Text(
            page.title,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() }
        )
        Text(
            page.intro,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
        if (page.showLinkExample) LinkExample()
        choice()
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            page.points.forEach { Point(it) }
        }
    }
}

/** Three rows, one of which has to be tapped before the guide can be left. */
@Composable
private fun MediaChoice(chosen: AutoDownload?, onChoose: (AutoDownload) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Option("Never", AutoDownload.OFF, chosen, onChoose)
        Option("On Wi-Fi only", AutoDownload.UNMETERED, chosen, onChoose)
        Option("On Wi-Fi or mobile data", AutoDownload.ANY, chosen, onChoose)
    }
}

/** Rows to pick one from. */
@Composable
private fun <T> Choices(options: List<Pair<T, String>>, chosen: T?, onChoose: (T) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) -> Option(label, value, chosen, onChoose) }
    }
}

/**
 * Each source on or off, any number of them: a picked row is on. The
 * summary says what each one sees.
 */
@Composable
private fun SourceChoices(settings: SettingsStore) {
    val current by settings.settings.collectAsState()
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SOURCE_OPTIONS.forEach { option ->
            val on = option.isOn(current)
            Option(
                label = option.title,
                value = true,
                chosen = on,
                onChoose = { settings.update { option.set(it, !on) } },
                detail = option.summary
            )
        }
    }
}

/** A filled primary colour when picked, so the answer is unmistakable. */
@Composable
private fun <T> Option(label: String, value: T, chosen: T?, onChoose: (T) -> Unit, detail: String? = null) {
    val picked = chosen == value
    val haptics = rememberHaptics()
    ZoneSurface(
        onClick = {
            haptics.tick()
            onChoose(value)
        },
        shape = RoundedCornerShape(16.dp),
        color = if (picked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
        accent = picked,
        modifier = Modifier.fillMaxWidth()
    ) {
        // The text takes the zone's own colour, which knows whether it is picked.
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Rows that switch on and off carry a check: in glass, a
                // picked zone and a plain one differ only by a light tint.
                if (detail != null && picked) Icon(MtgaIcons.Check, contentDescription = "On", modifier = Modifier.size(20.dp))
                Text(label, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            }
            if (detail != null) {
                Text(detail, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun LinkExample() {
    val handleStyle = SpanStyle(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    ZoneSurface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Text(
            buildAnnotatedString {
                append("https://x.com/")
                withStyle(handleStyle) { append("nytimes") }
            },
            style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
        )
    }
}

@Composable
private fun Point(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            Modifier
                .padding(top = 8.dp)
                .size(6.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape)
        )
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun Dots(count: Int, current: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(count) { index ->
            Box(
                Modifier
                    .size(if (index == current) 10.dp else 8.dp)
                    .background(
                        if (index == current) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        },
                        CircleShape
                    )
            )
        }
    }
}

/** The page's icon on a disc of the accent: a drop of glass in the accent when glass is on. */
@Composable
private fun Modifier.accentDisc(): Modifier {
    val glass = LocalGlass.current
    return if (glass == null) {
        background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
    } else {
        glassZone(CircleShape, glass, lens = 1f).background(glass.accentTint, CircleShape)
    }
}
