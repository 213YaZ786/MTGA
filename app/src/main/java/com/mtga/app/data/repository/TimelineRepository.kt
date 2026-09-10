package com.mtga.app.data.repository

import com.mtga.app.core.common.AppError
import com.mtga.app.core.common.Outcome
import com.mtga.app.core.model.Post
import com.mtga.app.data.accounts.AccountStore
import com.mtga.app.data.cache.FeedCache
import com.mtga.app.data.settings.SettingsStore
import com.mtga.app.data.xcom.XComSource
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * Everything you follow, merged into one stream.
 *
 * Cache first, then network. The reader sees content immediately and watches it
 * update, rather than staring at a spinner while several instances are polled.
 * When a fetch fails the cached posts stay on screen and the failure is
 * reported per account, because losing one account is not losing the timeline.
 */
class TimelineRepository(
    private val accounts: AccountStore,
    private val feeds: FeedRepository,
    private val cache: FeedCache,
    private val xcom: XComSource,
    private val settings: SettingsStore
) {

    data class Merged(
        val posts: List<Post> = emptyList(),
        val errors: Map<String, AppError> = emptyMap(),
        val fromCache: Boolean = false,
        val oldestFetchedAtMillis: Long? = null,
        val canLoadMore: Boolean = false
    )

    /** Instant, offline, no network touched. */
    suspend fun cached(): Merged {
        val handles = accounts.accounts.value.map { it.handle }
        if (handles.isEmpty()) return Merged()

        val loaded = handles.mapNotNull { cache.read(it) }
        return Merged(
            posts = merge(loaded.flatMap { it.posts }),
            fromCache = true,
            oldestFetchedAtMillis = loaded.minOfOrNull { it.fetchedAtMillis },
            canLoadMore = loaded.any { it.nextCursor != null }
        )
    }

    /**
     * Fetches every followed account, a few at a time.
     *
     * The concurrency limit is the point. Firing twenty simultaneous requests at
     * a single surviving instance is the fastest way to get rate limited, and
     * the pool's backoff would then punish every later read.
     */
    suspend fun refresh(): Merged = coroutineScope {
        val handles = accounts.accounts.value.map { it.handle }
        if (handles.isEmpty()) return@coroutineScope Merged()

        val gate = Semaphore(MAX_PARALLEL_FETCHES)

        val results = handles.map { handle ->
            async {
                gate.withPermit { handle to feeds.loadFeed(handle) }
            }
        }.map { it.await() }

        val errors = mutableMapOf<String, AppError>()
        var oldest: Long? = null
        var more = false

        // The head of the feed comes from X itself when that is switched on.
        // It is the only source that cannot be a stale re-serving of someone
        // else's parse, and the cache merges it with whatever the instances
        // provide for depth.
        if (settings.current.useXcomDirect) {
            for (handle in handles) {
                when (val head = xcom.fetchLatest(handle)) {
                    is Outcome.Success -> {
                        cache.append(head.value)
                        errors.remove(handle)
                    }
                    is Outcome.Failure -> Unit // instances still had their turn
                }
            }
        }

        for ((handle, outcome) in results) {
            when (outcome) {
                is Outcome.Success -> {
                    // Merge rather than overwrite, so a refresh does not throw
                    // away every page the reader already scrolled through.
                    val merged = cache.append(outcome.value)
                    accounts.updateDisplayName(handle, outcome.value.displayName)
                    if (merged.nextCursor != null) more = true
                    oldest = minOf(oldest ?: outcome.value.fetchedAtMillis, outcome.value.fetchedAtMillis)
                }
                is Outcome.Failure -> {
                    errors[handle] = outcome.error
                    cache.read(handle)?.let { if (it.nextCursor != null) more = true }
                }
            }
        }

        // Read back from the cache rather than from this run's results, so the
        // merged view includes everything ever collected, not only what today's
        // fetch happened to return. This is what makes background polling
        // accumulate history instead of replacing it.
        val stored = handles.mapNotNull { cache.read(it) }

        Merged(
            posts = merge(stored.flatMap { it.posts }),
            errors = errors,
            fromCache = false,
            oldestFetchedAtMillis = oldest,
            canLoadMore = more || stored.any { it.nextCursor != null }
        )
    }

    /**
     * Extends the merged timeline further back.
     *
     * The trick is choosing whom to ask. An account whose oldest loaded post is
     * recent is the one capping how far back the merged view can honestly go,
     * so those get paged first. Asking every account for another page instead
     * would waste requests on accounts that already reach back weeks, and with
     * one fragile instance in the pool, wasted requests are the scarce resource.
     */
    suspend fun loadMore(): Merged = coroutineScope {
        val handles = accounts.accounts.value.map { it.handle }
        if (handles.isEmpty()) return@coroutineScope Merged()

        val cached = handles.mapNotNull { cache.read(it) }
        val blocking = cached
            .filter { it.nextCursor != null && it.posts.isNotEmpty() }
            .sortedByDescending { feed -> feed.posts.minOf { it.publishedAtMillis } }
            .take(MAX_PARALLEL_FETCHES)

        if (blocking.isEmpty()) {
            return@coroutineScope Merged(
                posts = merge(cached.flatMap { it.posts }),
                canLoadMore = false
            )
        }

        val gate = Semaphore(MAX_PARALLEL_FETCHES)
        val errors = mutableMapOf<String, AppError>()

        blocking.map { feed ->
            async {
                gate.withPermit { feed.handle to feeds.loadFeed(feed.handle, feed.nextCursor) }
            }
        }.map { it.await() }.forEach { (handle, outcome) ->
            when (outcome) {
                is Outcome.Success -> cache.append(outcome.value, isPagedFetch = true)
                is Outcome.Failure -> errors[handle] = outcome.error
            }
        }

        val refreshed = handles.mapNotNull { cache.read(it) }
        Merged(
            posts = merge(refreshed.flatMap { it.posts }),
            errors = errors,
            fromCache = false,
            oldestFetchedAtMillis = refreshed.minOfOrNull { it.fetchedAtMillis },
            canLoadMore = refreshed.any { it.nextCursor != null }
        )
    }

    /**
     * Newest first, deduplicated. Pinned posts lose their pin in the merged
     * view: a pin is a statement about one profile, and honouring it here would
     * park an old post at the top of everything.
     */
    private fun merge(posts: List<Post>): List<Post> =
        posts.distinctBy { it.id }
            .sortedByDescending { it.publishedAtMillis }
            .map { if (it.isPinned) it.copy(isPinned = false) else it }
            .take(MAX_TIMELINE_POSTS)

    private companion object {
        /**
         * One at a time. With a single healthy instance in the pool, "parallel"
         * just means several simultaneous requests to the same small server,
         * which is precisely what earns a 429. The throttle paces them anyway,
         * so concurrency here would buy nothing.
         */
        const val MAX_PARALLEL_FETCHES = 1
        const val MAX_TIMELINE_POSTS = 2_000
    }
}
