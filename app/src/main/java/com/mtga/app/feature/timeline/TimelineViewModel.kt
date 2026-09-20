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

    init {
        viewModelScope.launch {
            // Paint from disk first so the timeline is readable before any
            // request goes out, then refresh over the top.
            work.withLock {
                known = followedKeys()
                val cached = repository.cached()
                _state.value = _state.value.copy(
                    posts = cached.posts,
                    followedCount = known.size,
                    lastUpdatedMillis = cached.oldestFetchedAtMillis,
                    canLoadMore = cached.canLoadMore
                )
            }
            refresh()
        }

        // Home follows the list live. Before 1.3.1 it read the list once at
        // launch, so an account followed later stayed off Home until a restart.
        // The flow is conflated, so a burst of changes becomes one pass.
        viewModelScope.launch {
            accounts.accounts.collect { reconcile() }
        }
    }

    fun refresh() {
        if (_state.value.loading || fullRefreshQueued) return
        fullRefreshQueued = true
        viewModelScope.launch {
            work.withLock {
                fullRefreshQueued = false
                known = followedKeys()
                fetch(only = null)
            }
        }
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
        if (added.isEmpty() && removed.isEmpty()) return@withLock
        known = current

        val cached = repository.cached()
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
    private suspend fun fetch(only: Set<String>?) {
        val before = _state.value
        _state.value = before.copy(loading = true, followedCount = known.size)

        val merged = repository.refresh(only)

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
        // happens with Home on screen.
        autoDownloader.consider(merged.posts)
    }

    private fun followedKeys(): Set<String> =
        accounts.accounts.value.map { it.handle.lowercase() }.toSet()

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
                val merged = repository.loadMore()
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
