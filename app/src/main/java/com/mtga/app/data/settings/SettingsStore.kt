package com.mtga.app.data.settings

import com.mtga.app.core.update.UpdateMode
import android.content.Context
import com.mtga.app.core.common.writeTextAtomically
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** The tab the app opens on. LAST reopens whichever tab was shown last. */
@Serializable
enum class StartTab { HOME, ACCOUNTS, LAST }

/**
 * When media of newly arrived posts is saved to Downloads without being asked.
 *
 * UNMETERED rather than "Wi-Fi", because that is what the reader means: a
 * phone hotspot is Wi-Fi and costs data, an unlimited Ethernet dongle is not
 * Wi-Fi and costs nothing. Android knows which is which.
 */
@Serializable
enum class AutoDownload { OFF, UNMETERED, ANY }

@Serializable
data class Settings(
    /** What happens when a newer version is out, checked once when the app opens. */
    val updates: UpdateMode = UpdateMode.NOTIFY,
    /*
     * The sources, each the reader's to switch off, all on by default: the
     * app's whole point is every public post without an account, whatever
     * the way. See data/repository/Sources for how they follow each other.
     */
    /** Read the newest posts straight from x.com. Accurate, but X sees you. */
    val useXcomDirect: Boolean = true,
    /** Whole timelines and conversations through FxTwitter, see FxTwitterSource. */
    val useFxTwitter: Boolean = true,
    /** The volunteer Nitter servers: pages, feeds, search, conversations. */
    val useNitter: Boolean = true,
    /** Post ids from the Internet Archive, see ArchiveSource. The name predates DuckDuckGo's own switch. */
    val olderFromArchives: Boolean = true,
    /** Post ids from DuckDuckGo's results, see ArchiveSource. */
    val useDuckDuckGo: Boolean = true,
    /** Poll followed accounts in the background so history accumulates. */
    val backgroundSync: Boolean = false,
    val syncIntervalMinutes: Int = 60,
    val syncOnWifiOnly: Boolean = true,
    /** A notification when background checks find new posts. Needs backgroundSync. */
    val notifyNewPosts: Boolean = false,
    /**
     * When notifications were turned on. Nothing older is ever announced, so
     * switching them on does not flood the shade with the backlog.
     */
    val notifySinceMillis: Long = 0,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** True black instead of dark grey in dark mode. */
    val pureBlack: Boolean = false,
    /** Zones and floating controls in liquid glass, over a soft light in the wallpaper's colours. */
    val glass: Boolean = true,
    /** Reply, repost, like and view counts under posts. */
    val showCounts: Boolean = true,
    /** Multiplier on every text style, one of the steps in ui.theme.TEXT_SCALES. */
    val textScale: Float = 1f,
    val compactPosts: Boolean = false,
    val squareAvatars: Boolean = false,
    /** Videos open silent. GIFs are always silent, they have no sound. */
    val startMuted: Boolean = false,
    /** Videos start by themselves when opened. GIFs always loop. */
    val autoplayVideos: Boolean = true,
    /**
     * On a metered network, pictures and videos wait for a tap. Off by
     * default: it is a data saver the reader chooses to impose.
     */
    val mediaOnWifiOnly: Boolean = false,
    /**
     * Save the pictures and videos of posts that arrive from now on, without
     * being asked. Off by default: it writes to the reader's own Downloads
     * folder and spends their data.
     */
    val autoDownloadMedia: AutoDownload = AutoDownload.OFF,
    /**
     * Posts published up to this instant have already been through automatic
     * download. Set to the moment the option is switched on, so turning it on
     * saves what arrives next and never the whole stored backlog.
     */
    val autoDownloadedUntilMillis: Long = 0,
    /** Saved posts older than this many days are dropped. 0 keeps everything. */
    val keepPostsDays: Int = 0,
    val startTab: StartTab = StartTab.HOME,
    /** Index of the tab shown last, for StartTab.LAST. Recorded on every switch. */
    val lastTab: Int = 0,
    /**
     * Share and copy post links on the first enabled Nitter server instead of
     * x.com, so the person receiving it can read without X. "Open on X" still
     * opens X.
     */
    val shareAsNitter: Boolean = false,
    /**
     * The first launch guide was closed. It is only offered when nothing is
     * followed yet, so an update never shows it to someone already set up.
     */
    val welcomeSeen: Boolean = false,
    /** The folder Home shows, or null for every account. */
    val homeFolder: String? = null,
    /**
     * Where Home's folder button was put, as fractions of the room it can
     * move in, or -1 until it is moved (then it sits above the dock).
     */
    val folderButtonX: Float = -1f,
    val folderButtonY: Float = -1f
)

/**
 * Small preference file, same plain JSON approach as the rest of MTGA's
 * storage. Nothing here is a secret, and none of it leaves the device.
 */
class SettingsStore(context: Context) {

    private val file = File(context.filesDir, "settings.json")
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    val current: Settings get() = _settings.value

    private fun load(): Settings {
        if (!file.exists()) return Settings()
        return runCatching { json.decodeFromString<Settings>(file.readText()) }
            .getOrDefault(Settings())
    }

    fun update(transform: (Settings) -> Settings) {
        val updated = transform(_settings.value)
        _settings.value = updated
        runCatching { file.writeTextAtomically(json.encodeToString(updated)) }
    }
}
