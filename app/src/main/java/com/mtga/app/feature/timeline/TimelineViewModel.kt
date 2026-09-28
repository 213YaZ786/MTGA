package com.mtga.app.feature.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mtga.app.core.common.AppError
import com.mtga.app.core.media.AutoMediaDownloader
import com.mtga.app.core.web.ChallengeSolver
import com.mtga.app.core.model.Post
import com.mtga.app.data.accounts.AccountStore
import com.mtga.app.data.repository.TimelineRepository
import com.mtga.app.data.settings.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class TimelineUiState(
    val posts: List<Post> = emptyList(),
    val loading: Boolean = false,
    val errors: Map<String, AppError> = emptyMap(),
    val followedCount: Int = 0,
    val lastUpdatedMillis: Long? = null,
    val loadingMore: Boolean = false,
    val canLoadMore: Boolean = false,
    /**
     * Set when a page request fails. Automatic prefetch stops, and the reader
     * gets a button instead. Retrying a rate limited host on a scroll gesture
     * is how a 429 turns into a fifteen minute ban.
     */
    val pagingFailed: Boolean = false,
    /** The folder Home shows, null for every account. */
    val folder: String? = null,
    /** Every folder, Main first. One entry means the reader has made none. */
    val folders: List<String> = emptyList(),
) {
    val isEmpty: Boolean get() = posts.isEmpty() && !loading
}

class TimelineViewModel(
    private val repository: TimelineRepository,
    private val accounts: AccountStore,
    private val settings: SettingsStore,
    private val solver: ChallengeSolver,
    private val autoDownloader: AutoMediaDownloader
) : ViewModel() {

    private val _state = MutableStateFlow(TimelineUiState())
    val state: StateFlow<TimelineUiState> = _state.asStateFlow()


    /**
     * One timeline operation at a time: launch, refresh, follow, unfollow,
     * paging. Each reads the cache and replaces the post list, so two running
     * together would overwrite each other, and two fetches together would
     * double the requests against a fragile host.
     */
    private val work = Mutex()

    /** Followed handles, lowercased, that the state reflects. Touched only under [work]. */
    private var known: Set<String> = emptySet()

    /** Set from a pull until it runs, so a second pull cannot queue a second pass. */
    private var fullRefreshQueued = false

    /** The folder Home shows, null for every account. Kept across launches. */
    @Volatile
    private var folder: String? = settings.current.homeFolder

    /**
     * Where each followed handle is filed, as the state reflects it. Moving
     * an account between folders leaves [known] unchanged, so without this a
     * folder being read would not notice an account arriving in it. Touched
     * only under [work].
     */
    private var filed: Map<String, String> = emptyMap()

    /**
     * True once a pass over every followed account, whatever its folder, has
     * finished in this run. From then on switching folders is a repaint from
     * disk. Touched only under [work].
     */
    private var everythingFetched = false

    init {
        viewModelScope.launch {
            // Paint from disk first so the timeline is readable before any
            // request goes out, then refresh over the top.
            work.withLock {
                known = followedKeys()
                filed = filedIn()
                val cached = repository.cached(folder)
                _state.value = _state.value.copy(
                    posts = cached.posts,
                    followedCount = known.size,
                    lastUpdatedMillis = cached.oldestFetchedAtMillis,
                    canLoadMore = cached.canLoadMore,
                    folder = folder,
                    folders = accounts.folders.value
                )
            }
            refresh(everything = true)
        }

        // Home follows the list live. Before 1.3.1 it read the list once at
        // launch, so an account followed later stayed off Home until a restart.
        // The flow is conflated, so a burst of changes becomes one pass.
        viewModelScope.launch {
            accounts.accounts.collect { reconcile() }
        }

        // The folder list is its own flow: creating or renaming a folder
        // moves no handle, so reconcile would not see it.
        viewModelScope.launch {
            accounts.folders.collect { onFolders(it) }
        }
    }

    /**
     * [everything] reads every followed account, whichever folder is on
     * screen. The launch does that, once, so each folder is fresh when the
     * reader moves to it instead of every switch starting its own round of
     * requests. A pull reads the folder on screen, which is what was asked
     * for, at a lower cost to hosts that are already rate limiting.
     */
    fun refresh(everything: Boolean = false) {
        if (_state.value.loading || fullRefreshQueued) return
        fullRefreshQueued = true
        viewModelScope.launch {
            work.withLock {
                fullRefreshQueued = false
                known = followedKeys()
                filed = filedIn()
                fetch(only = null, everything = everything)
            }
        }
    }

    /**
     * Switches Home to another folder, from disk and at once. Only when the
     * pass over everything never finished, the launch having been offline
     * for instance, does the switch fetch the folder's own accounts.
     *
     * The repaint does not wait for [work], so a switch made while the launch
     * is still reading shows the new folder immediately rather than when the
     * last request returns. The fetch in flight paints the folder shown at
     * the moment it finishes, see [fetch].
     */
    fun showFolder(name: String?) {
        if (folder == name) return
        folder = name
        settings.update { it.copy(homeFolder = name) }
        viewModelScope.launch {
            val cached = repository.cached(name)
            if (folder != name) return@launch
            _state.value = _state.value.copy(
                folder = name,
                posts = cached.posts,
                lastUpdatedMillis = cached.oldestFetchedAtMillis,
                canLoadMore = cached.canLoadMore,
                errors = errorsIn(_state.value.errors, name),
                pagingFailed = false
            )
            if (_state.value.loading) return@launch
            work.withLock {
                if (everythingFetched || folder != name) return@withLock
                known = followedKeys()
                filed = filedIn()
                fetch(only = null)
            }
        }
    }

    /**
     * Takes the folder list as it is now. Costs no request. The one case that
     * touches the stream is the folder being read having gone. A rename is
     * followed, since the screen that renames also moves the Home setting to
     * the new name, and a delete falls back to every account rather than to
     * an empty Home.
     */
    private fun onFolders(names: List<String>) {
        _state.value = _state.value.copy(folders = names)
        val shown = folder ?: return
        if (shown in names) return
        showFolder(settings.current.homeFolder?.takeIf { it in names })
    }

    /**
     * Brings the state in line with the followed list. An unfollow costs no
     * request, its posts simply leave. A follow fetches that account only,
     * after showing whatever its cache already holds, for example from having
     * just opened its feed.
     */
    private suspend fun reconcile() = work.withLock {
        val current = followedKeys()
        val added = current - known
        val removed = known - current
        val placed = filedIn()
        if (added.isEmpty() && removed.isEmpty() && placed == filed) return@withLock
        known = current
        filed = placed

        val cached = repository.cached(folder)
        _state.value = _state.value.copy(
            posts = cached.posts,
            followedCount = current.size,
            canLoadMore = cached.canLoadMore,
            errors = _state.value.errors.filterKeys { it.lowercase() in current },
            lastUpdatedMillis = if (current.isEmpty()) null else _state.value.lastUpdatedMillis
        )

        if (added.isNotEmpty()) fetch(only = added)
    }

    /**
     * Runs under [work]. [only] limits the network to those handles, and the
     * errors of every other account are kept, since they were not retried.
     */
    private suspend fun fetch(only: Set<String>?, everything: Boolean = false) {
        val before = _state.value
        _state.value = before.copy(loading = true, followedCount = known.size)
        val asked = folder

        val network = repository.refresh(only, if (everything) null else asked)
        if (everything && only == null) everythingFetched = true
        // What goes on screen is the folder shown now. It differs from what
        // was fetched after a pass over everything, and after a switch made
        // while the requests were out.
        val merged = if (everything || folder != asked) {
            repository.cached(folder).copy(errors = errorsIn(network.errors, folder))
        } else {
            network
        }

        val errors = if (only == null) {
            merged.errors
        } else {
            _state.value.errors.filterKeys { it.lowercase() !in only } + merged.errors
        }
        val lastUpdated = if (only == null) {
            merged.oldestFetchedAtMillis ?: _state.value.lastUpdatedMillis
        } else {
            // One account fetched does not make the whole timeline fresh.
            _state.value.lastUpdatedMillis ?: merged.oldestFetchedAtMillis
        }
        _state.value = _state.value.copy(
            posts = merged.posts,
            loading = false,
            errors = errors,
            followedCount = known.size,
            lastUpdatedMillis = lastUpdated,
            canLoadMore = merged.canLoadMore,
            loadingMore = false,
            pagingFailed = false
        )
        // Automatic media saving runs here and nowhere else, so it only ever
        // happens with Home on screen. It always sees every account: it keeps
        // a watermark on the newest post it handled, and given one folder
        // only, it would move that mark past posts of other folders it never
        // saw, which would then never be saved.
        autoDownloader.consider(if (everything || asked == null) network.posts else repository.cached(null).posts)
    }

    /** Creates the folder if it is new and returns the name to show, see AccountStore.createFolder. */
    fun createFolder(name: String): String? = accounts.createFolder(name)

    private fun followedKeys(): Set<String> =
        accounts.accounts.value.map { it.handle.lowercase() }.toSet()

    private fun filedIn(): Map<String, String> =
        accounts.accounts.value.associate { it.handle.lowercase() to it.folder }

    /** The errors of the accounts in [folder], all of them for null. */
    private fun errorsIn(errors: Map<String, AppError>, folder: String?): Map<String, AppError> {
        if (folder == null) return errors
        val inFolder = accounts.accounts.value.filter { it.folder == folder }.map { it.handle.lowercase() }.toSet()
        return errors.filterKeys { it.lowercase() in inFolder }
    }

    /**
     * The user tapped the check pill. On success the host is cleared for the
     * session and the whole timeline is read again, since one bot check
     * usually blocked every account served by that host.
     */
    fun verify(error: AppError.ChallengeRequired) {
        viewModelScope.launch {
            val result = solver.solve(error.url, error.host, interactive = true)
            if (result is ChallengeSolver.Result.Cleared) refresh()
        }
    }

    /**
     * Called when the reader nears the bottom, and by the retry button.
     * [manual] bypasses the failure latch, so a person can insist, but a scroll
     * gesture cannot. Skipped while another operation holds the timeline, the
     * next scroll asks again.
     */
    /**
     * When the last paging attempt gave up. The whole pool is swept on every
     * attempt, so a failed attempt has just asked seven servers and re-armed
     * two bot checks. The log showed three sweeps in eight seconds, which
     * deepens the very rate limits that caused the failure. Automatic attempts
     * wait this out, a deliberate one from the reader does not.
     */
    private var pagingFailedAtMillis = 0L

    fun loadMore(manual: Boolean = false) {
        val current = _state.value
        if (current.loadingMore || current.loading || !current.canLoadMore) return
        if (current.pagingFailed && !manual) return
        if (!manual &&
            System.currentTimeMillis() - pagingFailedAtMillis < PAGING_RETRY_PAUSE_MS
        ) {
            return
        }
        if (!work.tryLock()) return

        _state.value = current.copy(loadingMore = true, pagingFailed = false)
        viewModelScope.launch {
            try {
                val before = current.posts.size
                val merged = repository.loadMore(folder)
                val failed = merged.errors.isNotEmpty() || merged.posts.size <= before
                if (failed) pagingFailedAtMillis = System.currentTimeMillis()
                _state.value = _state.value.copy(
                    posts = merged.posts,
                    loadingMore = false,
                    canLoadMore = merged.canLoadMore,
                    errors = merged.errors.ifEmpty { _state.value.errors },
                    pagingFailed = failed
                )
            } finally {
                work.unlock()
            }
        }
    }

}

/** A failed sweep of the whole pool is not worth repeating sooner. */
private const val PAGING_RETRY_PAUSE_MS = 30_000L
