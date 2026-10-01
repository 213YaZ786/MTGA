package com.mtga.app.data.repository

import com.mtga.app.core.common.AppError
import com.mtga.app.core.common.Outcome
import com.mtga.app.core.model.Conversation
import com.mtga.app.core.model.Feed
import com.mtga.app.core.debug.RequestLog
import com.mtga.app.core.model.ArchiveCursor
import com.mtga.app.core.model.FxCursor
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
import com.mtga.app.data.fxtwitter.FxTwitterSource

/**
 * The only place that decides which source to use, following the reader's
 * choice of sources, see [Sources].
 *
 * A first page comes from FxTwitter, then Nitter (HTML, RSS when the page
 * itself was the problem) when FxTwitter fails or is off. x.com's few newest
 * posts come beside, see [loadHead]. Further down, each cursor goes back to
 * the source that wrote it while that source is on; past a source's end,
 * after a failure mid scroll, or once its source is switched off, paging
 * goes on below the oldest post through post ids.
 */
class FeedRepository(
    private val pool: InstancePool,
    private val html: HtmlSource,
    private val rss: RssSource,
    private val xcom: XComSource,
    private val fx: FxTwitterSource,
    private val settings: SettingsStore,
    private val archive: ArchiveSource,
    private val cache: FeedCache,
    private val log: RequestLog,
    private val marks: ReadMarks,
    private val syndication: SyndicationSource,
    private val notice: LoadingNotice
) {

    /** Read at each call: a switch flipped in Settings takes effect at once. */
    private val sources: Sources get() = Sources.of(settings.current)

    /** Posts already checked for a cut text this session. */
    private val checkedForCut = java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap<String, Boolean>())

    /**
     * The newest posts straight from x.com, when enabled. Head only, no
     * cursor, so it is fetched alongside [loadFeed] rather than instead of it.
     * Returns null when the setting is off.
     */
    suspend fun loadHead(handle: String): Outcome<Feed>? {
        if (!sources.xcom) return null
        return xcom.fetchLatest(handle)
    }

    /**
     * After a refresh whose first page brought nothing (every page source
     * failed or is off), x.com's few posts may be all there is: an account
     * stored without a way further down gets one through post ids. Before
     * 2.15.0 the head itself carried that cursor, and a first page arriving
     * just after kept it as the deeper one, so a new account paged through
     * the archives' gaps rather than its own timeline.
     */
    suspend fun keepPaging(handle: String) {
        val stored = cache.read(handle) ?: return
        if (stored.nextCursor != null || stored.posts.isEmpty()) return
        val next = sources.below(stored.posts) ?: return
        cache.continueAt(handle, next)
        log.record(RequestLog.Kind.PAGE, "page/$handle", "no page source answered, older posts through ${sources.via(next)}")
    }

    /**
     * Fills the account's gaps from the archives after a refresh: the posts
     * of the last weeks the phone does not hold, read from X by their ids,
     * see ArchiveSource.fill. No duplicate: only ids not stored are read,
     * and the cache adds only ids it does not hold. What was published
     * before the reader's previous visit is not new. Returns the count added.
     */
    suspend fun fillGaps(handle: String): Int {
        val sources = sources
        var count = if (sources.fxtwitter) fillFromFx(handle) else 0
        if (sources.idLists) count += fillFromArchives(handle)
        if (sources.fxtwitter) completeCutPosts(handle)
        return count
    }

    /**
     * The archives' posts that fall between the oldest and the newest post
     * stored: holes only. Before 2.15.0 any post of the last month was
     * added, and the archive lagging a day or two behind, its newest posts
     * landed under the first page with a hole of a day between, which
     * paging filled only once the reader had scrolled past the whole store.
     * Older posts are paging's own business.
     */
    private suspend fun fillFromArchives(handle: String): Int {
        val stored = cache.read(handle) ?: return 0
        val own = stored.posts.filterNot { it.isPinned }
        val newest = own.maxOfOrNull { it.publishedAtMillis } ?: return 0
        val oldest = own.minOf { it.publishedAtMillis }
        val days = settings.current.keepPostsDays.takeIf { it in 1..FILL_DAYS } ?: FILL_DAYS
        val since = maxOf(System.currentTimeMillis() - days * DAY_MS, oldest)
        if (since >= newest) return 0
        val found = archive.fill(handle, stored.posts.mapTo(HashSet()) { it.id }, since, newest, FILL_MAX)
        val added = cache.addPosts(handle, found)
        marks.markReadIfOld(added.map { it.id to it.publishedAtMillis })
        return added.size
    }

    /**
     * The hole a refresh left (see Feed.gapCursor), read from FxTwitter page
     * after page down to the posts stored before it, a few pages a pass.
     */
    private suspend fun fillFromFx(handle: String): Int {
        val stored = cache.read(handle) ?: return 0
        var cursor = FxCursor.parse(stored.gapCursor) ?: return 0
        val held = stored.posts.mapTo(HashSet()) { it.id }
        var added = 0
        repeat(FX_GAP_PAGES) {
            val page = (fx.timeline(handle, cursor) as? Outcome.Success)?.value ?: return added.also {
                log.record(RequestLog.Kind.PAGE, "fx/$handle", "gap: FxTwitter failed, tried again at the next refresh", detail = "added: $added")
            }
            val fresh = cache.addPosts(handle, page.posts.filter { it.id !in held })
            marks.markReadIfOld(fresh.map { it.id to it.publishedAtMillis })
            added += fresh.size
            val next = FxCursor.parse(page.nextCursor)
            if (page.posts.isEmpty() || page.posts.any { !it.isPinned && it.id in held } || next == null) {
                cache.setGap(handle, null)
                log.record(RequestLog.Kind.PAGE, "fx/$handle", "gap filled from FxTwitter", detail = "added: $added")
                return added
            }
            cursor = next
        }
        cache.setGap(handle, FxCursor.of(cursor))
        log.record(RequestLog.Kind.PAGE, "fx/$handle", "gap: $FX_GAP_PAGES pages read, the rest at the next refresh", detail = "added: $added")
        return added
    }

    /**
     * Long posts saved cut at 280 characters (read from X's embed before
     * 2.14.1): a few per pass get their whole text, see SyndicationSource.longText.
     * A post of that length that was not cut simply comes back the same.
     * FxTwitter gives the text, so only while it is on.
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
        if (sources.idLists) archive.warm(handle)
    }

    /**
     * A post's conversation, never cached: FxTwitter first, which needs only
     * the post's number, then whichever Nitter server is healthy, which also
     * needs the author ([handle], null when nothing named one).
     */
    suspend fun loadConversation(handle: String?, id: String): Outcome<Conversation> {
        val sources = sources
        var failed: Outcome<Conversation>? = null
        if (sources.fxtwitter) {
            val viaFx = fx.conversation(id)
            if (viaFx is Outcome.Success) return viaFx
            failed = viaFx
        }
        if (sources.nitter) {
            if (handle == null) return failed ?: Outcome.Failure(AppError.AuthorUnknown(id, askedX = sources.xcom))
            return pool.withInstance { instance -> html.fetchConversation(instance, handle, id) }
        }
        return failed ?: Outcome.Failure(AppError.SourcesOff)
    }

    suspend fun loadFeed(handle: String, cursor: String? = null): Outcome<Feed> {
        // Cursors are not interchangeable between sources, and handing one to
        // the wrong source silently restarts the feed.
        if (LegacyCursor.fromDroppedSource(cursor)) {
            // A cursor from a source MTGA no longer has, still sitting in a
            // cache written by an older version. Nothing can continue it, so
            // the honest answer is that it has nothing more. The empty page
            // makes the cache drop the cursor, and paging stops instead of
            // failing on every scroll.
            return Outcome.Success(empty(handle))
        }
        val sources = sources
        if (cursor != null) return loadOlder(handle, cursor, sources)

        if (sources.none) return Outcome.Failure(AppError.SourcesOff)

        var fxFailure: Outcome<Feed>? = null
        if (sources.fxtwitter) {
            val viaFx = fx.timeline(handle, null)
            // An empty timeline (a protected account, an answer without posts)
            // is not trusted over Nitter's own reading.
            if (viaFx is Outcome.Success && viaFx.value.posts.isNotEmpty()) {
                return Outcome.Success(withBeyond(viaFx.value, firstPage = true))
            }
            fxFailure = viaFx
        }

        // No page source: x.com's head and the id lists do the work, see
        // keepPaging. An empty page, so no account is reported as failing.
        if (!sources.nitter) return fxFailure?.takeIf { it is Outcome.Failure } ?: Outcome.Success(empty(handle))

        val viaHtml = pool.withInstance { instance -> html.fetchProfile(instance, handle, null) }
        if (viaHtml is Outcome.Success) return Outcome.Success(withBeyond(viaHtml.value, firstPage = true))

        val htmlError = (viaHtml as Outcome.Failure).error

        // RSS only helps when the page itself was the problem, and it has no
        // concept of paging, so it can only ever stand in for the first page.
        if (!worthTryingRss(htmlError)) return viaHtml

        val viaRss = rss.fetchFeed(handle)
        if (viaRss is Outcome.Success) return Outcome.Success(withBeyond(viaRss.value, firstPage = true))

        return viaHtml
    }

    /** A page further down, from the source that wrote [cursor] while it is on. */
    private suspend fun loadOlder(handle: String, cursor: String, sources: Sources): Outcome<Feed> {
        // Which source the next page is asked of, so a log sent in shows why
        // a scroll to the end brought nothing.
        log.record(RequestLog.Kind.PAGE, "page/$handle", "older posts asked of ${sources.via(cursor)}")

        if (!sources.follows(cursor)) {
            val next = sources.translate(cursor, cache.read(handle)?.posts.orEmpty())
            if (next == null) {
                log.record(RequestLog.Kind.PAGE, "page/$handle", "every source that goes further is off, the end")
                return Outcome.Success(empty(handle))
            }
            return loadOlder(handle, next, sources)
        }

        FxCursor.parse(cursor)?.let { return fxOlder(handle, it, sources) }

        ArchiveCursor.parse(cursor)?.let { below -> return archive.page(handle, below) }

        SearchCursor.parse(cursor)?.let { position ->
            return pool.withInstance { instance -> html.fetchSearch(instance, handle, position) }
        }

        // Paging cannot cross sources, so a mid-scroll failure stays failed:
        // the cursor is kept and the next scroll tries again.
        return when (val viaHtml = pool.withInstance { instance -> html.fetchProfile(instance, handle, cursor) }) {
            is Outcome.Success -> Outcome.Success(withBeyond(viaHtml.value, firstPage = false))
            is Outcome.Failure -> viaHtml
        }
    }

    /**
     * FxTwitter's next page. A page holding only posts already stored is
     * passed over, a few at most: after a refresh met a hole, the timeline
     * is read again from the top through what the phone holds. Past its
     * end, after a failure, or past too many known pages, paging goes on
     * below the oldest stored post through post ids.
     */
    private suspend fun fxOlder(handle: String, start: String, sources: Sources): Outcome<Feed> {
        val held = cache.read(handle)?.posts?.mapTo(HashSet()) { it.id }.orEmpty()
        var next = start
        var passed = 0
        repeat(FX_KNOWN_PAGES) {
            when (val page = fx.timeline(handle, next)) {
                is Outcome.Failure -> return beyond(handle, "FxTwitter failed", sources, page)
                is Outcome.Success -> {
                    val feed = page.value
                    if (feed.posts.isEmpty()) return beyond(handle, "FxTwitter has nothing older", sources, Outcome.Success(feed))
                    val following = FxCursor.parse(feed.nextCursor)
                    if (feed.posts.any { it.id !in held } || following == null) {
                        if (passed > 0) log.record(RequestLog.Kind.PAGE, "fx/$handle", "passed over $passed posts already stored")
                        return Outcome.Success(withBeyond(feed, firstPage = false))
                    }
                    passed += feed.posts.size
                    next = following
                }
            }
        }
        return beyond(handle, "$passed posts in a row already stored", sources, Outcome.Success(empty(handle)))
    }

    /** Below the oldest stored post through post ids, else [otherwise]. */
    private suspend fun beyond(handle: String, why: String, sources: Sources, otherwise: Outcome<Feed>): Outcome<Feed> {
        val next = sources.below(cache.read(handle)?.posts.orEmpty())
        log.record(RequestLog.Kind.PAGE, "fx/$handle", "$why, " + (next?.let { "older posts through ${sources.via(it)}" } ?: "no other source goes further"))
        return if (next == null) otherwise else loadOlder(handle, next, sources)
    }

    /**
     * A page with no further page, the end of what a source shows, goes on
     * below its oldest post through post ids (the archives' lists, else
     * Nitter search). When those have nothing older either, their empty page
     * ends paging as any source's does.
     */
    private suspend fun withBeyond(feed: Feed, firstPage: Boolean): Feed = when {
        feed.nextCursor != null || feed.posts.isEmpty() -> feed
        firstPage -> feed.copy(nextCursor = belowStored(feed.handle, feed.posts))
        else -> feed.copy(nextCursor = sources.below(feed.posts))
    }

    /**
     * Below the oldest post the phone already has for the account, not only
     * below this first page: a page of posts all stored already reads to the
     * cache as the end, and paging would stop there.
     */
    private suspend fun belowStored(handle: String, posts: List<Post>): String? {
        val cursor = sources.below(posts + cache.read(handle)?.posts.orEmpty())
        log.record(
            RequestLog.Kind.PAGE, "page/$handle",
            cursor?.let { "next page through ${sources.via(it)}" } ?: "no source goes further than this page",
            detail = (ArchiveCursor.parse(cursor) ?: SearchCursor.parse(cursor)?.maxId)
                ?.let { "below ${java.time.Instant.ofEpochMilli((it shr 22) + 1_288_834_974_657L)}" }
        )
        return cursor
    }

    private fun empty(handle: String) =
        Feed(handle = handle, displayName = "", posts = emptyList(), fetchedFromHost = "", fetchedAtMillis = System.currentTimeMillis())

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
        /** FxTwitter pages of known posts passed over before going on through ids. */
        const val FX_KNOWN_PAGES = 6
        /** FxTwitter pages read into a hole per pass. */
        const val FX_GAP_PAGES = 10
    }
}
