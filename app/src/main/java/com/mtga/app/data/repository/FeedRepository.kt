package com.mtga.app.data.repository

import com.mtga.app.core.common.AppError
import com.mtga.app.core.common.Outcome
import com.mtga.app.core.model.Conversation
import com.mtga.app.core.model.Feed
import com.mtga.app.core.debug.RequestLog
import com.mtga.app.core.model.ArchiveCursor
import com.mtga.app.core.model.LegacyCursor
import com.mtga.app.core.model.Post
import com.mtga.app.core.model.SearchCursor
import com.mtga.app.data.archive.ArchiveSource
import com.mtga.app.data.cache.FeedCache
import com.mtga.app.data.html.HtmlSource
import com.mtga.app.data.read.ReadMarks
import com.mtga.app.core.system.LoadingNotice
import com.mtga.app.data.xcom.SyndicationSource
import com.mtga.app.core.model.PostKind
import com.mtga.app.data.instances.InstancePool
import com.mtga.app.data.rss.RssSource
import com.mtga.app.data.settings.SettingsStore
import com.mtga.app.data.xcom.XComSource

/**
 * The only place that decides which source to use.
 *
 * HTML first. That reverses the original plan, and the reason is concrete:
 * the one surviving instance restricts RSS to clients it has approved, while
 * its web pages stay open. RSS remains wired up because it is cheaper and
 * because a different instance, or a whitelisted one, makes it the better path
 * again without any change above this class.
 */
class FeedRepository(
    private val pool: InstancePool,
    private val html: HtmlSource,
    private val rss: RssSource,
    private val xcom: XComSource,
    private val settings: SettingsStore,
    private val archive: ArchiveSource,
    private val cache: FeedCache,
    private val log: RequestLog,
    private val marks: ReadMarks,
    private val syndication: SyndicationSource,
    private val notice: LoadingNotice
) {

    /** Posts already checked for a cut text this session. */
    private val checkedForCut = java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap<String, Boolean>())

    /**
     * The newest posts straight from x.com, when enabled. Head only, no
     * cursor, so it is fetched alongside [loadFeed] rather than instead of it.
     * Returns null when the setting is off.
     */
    suspend fun loadHead(handle: String): Outcome<Feed>? {
        if (!settings.current.useXcomDirect) return null
        val head = xcom.fetchLatest(handle)
        // When every Nitter server fails, these few posts are all there is:
        // the archives still reach further back from them.
        return if (head is Outcome.Success && settings.current.olderFromArchives && head.value.nextCursor == null) {
            Outcome.Success(head.value.copy(nextCursor = archiveBelow(handle, head.value.posts)))
        } else {
            head
        }
    }

    /**
     * Fills the account's gaps from the archives after a refresh: the posts
     * of the last weeks the phone does not hold, read from X by their ids,
     * see ArchiveSource.fill. No duplicate: only ids not stored are read,
     * and the cache adds only ids it does not hold. What was published
     * before the reader's previous visit is not new. Returns the count added.
     */
    suspend fun fillGaps(handle: String): Int {
        if (!settings.current.olderFromArchives) return 0
        val stored = cache.read(handle) ?: return 0
        val days = settings.current.keepPostsDays.takeIf { it in 1..FILL_DAYS } ?: FILL_DAYS
        val since = System.currentTimeMillis() - days * DAY_MS
        val found = archive.fill(handle, stored.posts.mapTo(HashSet()) { it.id }, since, FILL_MAX)
        val added = cache.addPosts(handle, found)
        marks.markReadIfOld(added.map { it.id to it.publishedAtMillis })
        completeCutPosts(handle)
        return added.size
    }

    /**
     * Long posts saved cut at 280 characters (read from X's embed before
     * 2.14.1): a few per pass get their whole text, see SyndicationSource.longText.
     * A post of that length that was not cut simply comes back the same.
     */
    private suspend fun completeCutPosts(handle: String) {
        val stored = cache.read(handle) ?: return
        val suspects = stored.posts
            .filter { it.kind != PostKind.REPOST && it.text.length in CUT_LENGTHS && it.id.all(Char::isDigit) && it.id !in checkedForCut }
            .take(CUT_PER_PASS)
        if (suspects.isEmpty()) return
        val whole = suspects.mapNotNull { post ->
            checkedForCut += post.id
            syndication.longText(post.authorHandle, post.id)?.takeIf { it.length > post.text.length }?.let { post.id to it }
        }.toMap()
        cache.replaceTexts(handle, whole)
    }

    /** [fillGaps] with the loading notification, for a profile the reader opened. */
    suspend fun fillGapsShown(handle: String): Int = notice.during("Looking up older posts") { fillGaps(handle) }

    /** Starts the archives' lookup for an account opened by the reader, see ArchiveSource.warm. */
    fun warmArchive(handle: String) {
        if (settings.current.olderFromArchives) archive.warm(handle)
    }

    /** A post's conversation, from whichever Nitter server is healthy. Never cached. */
    suspend fun loadConversation(handle: String, id: String): Outcome<Conversation> =
        pool.withInstance { instance -> html.fetchConversation(instance, handle, id) }

    suspend fun loadFeed(handle: String, cursor: String? = null): Outcome<Feed> {
        // Cursors are not interchangeable between sources, and handing one to
        // the wrong source silently restarts the feed.
        if (LegacyCursor.fromDroppedSource(cursor)) {
            // A cursor from a source MTGA no longer has, still sitting in a
            // cache written by an older version. Nothing can continue it, so
            // the honest answer is that it has nothing more. The empty page
            // makes the cache drop the cursor, and paging stops instead of
            // failing on every scroll.
            return Outcome.Success(
                Feed(
                    handle = handle,
                    displayName = "",
                    posts = emptyList(),
                    fetchedFromHost = "",
                    fetchedAtMillis = System.currentTimeMillis()
                )
            )
        }

        // Which source the next page is asked of, so a log sent in shows why
        // a scroll to the end brought nothing.
        if (cursor != null) {
            val via = when {
                ArchiveCursor.parse(cursor) != null -> if (settings.current.olderFromArchives) "the archives" else "the archives, switched off"
                SearchCursor.parse(cursor) != null -> "Nitter search"
                else -> "a Nitter page"
            }
            log.record(RequestLog.Kind.PAGE, "page/$handle", "older posts asked of $via")
        }

        ArchiveCursor.parse(cursor)?.let { below ->
            if (settings.current.olderFromArchives) return archive.page(handle, below)
            return Outcome.Success(Feed(handle = handle, displayName = "", posts = emptyList(), fetchedFromHost = "", fetchedAtMillis = System.currentTimeMillis()))
        }

        SearchCursor.parse(cursor)?.let { position ->
            return pool.withInstance { instance -> html.fetchSearch(instance, handle, position) }
        }

        val viaHtml = pool.withInstance { instance -> html.fetchProfile(instance, handle, cursor) }
        if (viaHtml is Outcome.Success) return Outcome.Success(withSearchBeyond(viaHtml.value, cursor == null))

        val htmlError = (viaHtml as Outcome.Failure).error

        // RSS only helps when the page itself was the problem, and it has no
        // concept of paging, so it can only ever stand in for the first page.
        if (cursor != null || !worthTryingRss(htmlError)) {
            // Paging cannot cross sources, so a mid-scroll failure stays failed.
            return viaHtml
        }

        val viaRss = rss.fetchFeed(handle)
        if (viaRss is Outcome.Success) return Outcome.Success(withSearchBeyond(viaRss.value, firstPage = true))

        return viaHtml
    }

    /**
     * A page with no further page, the end of what a profile page or the RSS
     * feed shows, goes on below its oldest post: through the web archives
     * when the reader allows them, else through Nitter search. When
     * search has nothing older either, its empty page ends paging as any
     * source's does.
     */
    private suspend fun withSearchBeyond(feed: Feed, firstPage: Boolean): Feed = when {
        feed.nextCursor != null || feed.posts.isEmpty() -> feed
        // The archives reach further and do not depend on a Nitter server.
        settings.current.olderFromArchives ->
            feed.copy(nextCursor = if (firstPage) archiveBelow(feed.handle, feed.posts) else ArchiveCursor.below(feed.posts))
        else -> feed.copy(nextCursor = SearchCursor.below(feed.posts))
    }

    /**
     * Below the oldest post the phone already has for the account, not only
     * below this first page: a page of posts all stored already reads to the
     * cache as the end, and paging would stop there.
     */
    private suspend fun archiveBelow(handle: String, posts: List<Post>): String? {
        val cursor = ArchiveCursor.below(posts + cache.read(handle)?.posts.orEmpty())
        log.record(
            RequestLog.Kind.PAGE, "archive/$handle",
            if (cursor == null) "no own post to page below" else "next page from the archives",
            detail = ArchiveCursor.parse(cursor)?.let { "below ${java.time.Instant.ofEpochMilli((it shr 22) + 1_288_834_974_657L)}" }
        )
        return cursor
    }

    private fun worthTryingRss(error: AppError): Boolean = when (error) {
        // A check is deliberately absent. The feed host of a checked instance
        // is either behind the same check or gated, as rss.xcancel.com is, so
        // trying it only spends a request against a fragile host.
        is AppError.ParseFailure,
        is AppError.ClientRefused,
        is AppError.InstanceError,
        is AppError.Timeout -> true
        else -> false
    }

    private companion object {
        /** How far back a refresh fills, and how many posts a pass adds per account. */
        const val FILL_DAYS = 30
        const val FILL_MAX = 100
        val CUT_LENGTHS = 240..290
        const val CUT_PER_PASS = 15
        const val DAY_MS = 86_400_000L
    }
}
