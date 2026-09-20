package com.mtga.app.data.cache

import android.content.Context
import com.mtga.app.core.debug.RequestLog
import com.mtga.app.core.model.Feed
import com.mtga.app.core.model.LegacyCursor
import com.mtga.app.core.model.Post
import com.mtga.app.core.model.PostId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * On disk cache of the last fetched feed per account.
 *
 * One JSON file per handle rather than a database. For a few hundred posts this
 * is faster to read than opening a database, adds no code generation to the
 * build, and keeps everything the app stores inspectable. Room earns its place
 * when full text search across the archive arrives, not before.
 *
 * Lives in filesDir, not cacheDir, because the system is free to delete cacheDir
 * under storage pressure and an offline reader that loses its content when the
 * phone gets full is not much of an offline reader.
 */
class FeedCache(
    context: Context,
    private val log: RequestLog,
    /** Days to keep saved posts, 0 for no limit. Read at each write. */
    private val retentionDays: () -> Int = { 0 }
) {

    private val directory = File(context.filesDir, "feeds").apply { mkdirs() }
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Posts the reader scrolled back to in this session that the disk does
     * not keep: older than "Keep posts", or past the per account cap. Memory
     * only, per lowercased handle, gone when the app closes.
     *
     * Before 2.4.2 they were dropped at the write that fetched them, so a page
     * of older posts arrived and vanished at once. Home and profiles saw no
     * new posts, took it as a failure and stopped loading older ones. Kept
     * here, they show until the app closes, which is what the setting
     * promises: older posts can be read, they are simply not kept.
     */
    private val scrolledBack = ConcurrentHashMap<String, List<Post>>()

    suspend fun read(handle: String): Feed? = withContext(Dispatchers.IO) {
        val file = fileFor(handle)
        if (!file.exists()) return@withContext null
        runCatching { json.decodeFromString<Feed>(file.readText()) }.getOrNull()
            ?.let(::canonical)
            ?.let(::withScrolledBack)
    }

    suspend fun write(feed: Feed) = withContext(Dispatchers.IO) {
        val kept = trim(feed)
        if (kept !== feed) {
            val keptIds = kept.posts.mapTo(HashSet()) { it.id }
            remember(feed.handle, feed.posts.filterNot { it.id in keptIds })
        }
        runCatching {
            fileFor(feed.handle).writeText(json.encodeToString(kept))
        }
        Unit
    }

    /** Adds what the disk dropped to this session's memory, newest version first. */
    private fun remember(handle: String, dropped: List<Post>) {
        if (dropped.isEmpty()) return
        scrolledBack.merge(handle.lowercase(), dropped) { old, new ->
            (new + old).distinctBy { it.id }
                .sortedByDescending { it.publishedAtMillis }
                .take(MAX_SCROLLED_BACK_PER_ACCOUNT)
        }
    }

    /** The stored feed plus the posts of this session the disk does not keep. */
    private fun withScrolledBack(feed: Feed): Feed {
        val extra = scrolledBack[feed.handle.lowercase()].orEmpty()
        if (extra.isEmpty()) return feed
        val known = feed.posts.mapTo(HashSet()) { it.id }
        val missing = extra.filterNot { it.id in known }
        if (missing.isEmpty()) return feed
        return feed.copy(posts = (feed.posts + missing).sortedWith(PROFILE_ORDER))
    }

    /**
     * Appends a newly fetched page to what is already stored, keeping the newer
     * cursor. Deduplicated by post id, because Nitter pages overlap at their
     * boundary and a repeated post breaks LazyColumn's key contract.
     */
    suspend fun append(page: Feed, isPagedFetch: Boolean = false): Feed = withContext(Dispatchers.IO) {
        val incoming = canonical(page)
        val existing = read(incoming.handle)
        if (existing == null) {
            log.record(
                kind = RequestLog.Kind.CACHE,
                url = "cache/${incoming.handle}",
                outcome = "first",
                detail = "nothing stored yet | in: ${incoming.posts.size} | " +
                    "in newest: ${incoming.posts.maxByOrNull { it.publishedAtMillis }?.id ?: "NONE"}" +
                    " oldest: ${incoming.posts.minByOrNull { it.publishedAtMillis }?.id ?: "NONE"}"
            )
            write(incoming)
            return@withContext incoming
        }

        val known = existing.posts.map { it.id }.toSet()
        val newPosts = incoming.posts.filterNot { it.id in known }

        // A post already stored may come back richer: x.com serves the head
        // without cards or polls and with long texts cut off, Nitter serves
        // them whole for the same ids. Keep the stored post and fill in what
        // it lacked, take the fuller text, plus fresher counts.
        //
        // Pin status is the exception to "the stored post keeps its identity".
        // Only page one of a Nitter profile read states it with authority:
        // the pin is marked there, and its absence there means the author
        // unpinned. mergedWith leaves isPinned alone, so a post stored before
        // its author pinned it could otherwise never become pinned.
        val statesPins = !isPagedFetch && incoming.pinAware
        val seenAgain = incoming.posts.filter { it.id in known }.associateBy { it.id }
        val refreshed = if (seenAgain.isEmpty()) existing.posts else existing.posts.map { post ->
            seenAgain[post.id]?.let { fresh ->
                val merged = post.mergedWith(fresh)
                if (statesPins && merged.isPinned != fresh.isPinned) merged.copy(isPinned = fresh.isPinned) else merged
            } ?: post
        }

        // A paged fetch that brings back nothing new means the cursor did not
        // advance. Continuing would loop forever on the same page, so stop
        // offering to load more rather than spinning against the instance.
        val exhausted = isPagedFetch && newPosts.isEmpty()

        // A refresh reads the first page again. When that page meets posts
        // already stored, what is stored runs on from the head without a
        // hole, so the deeper cursor kept from earlier paging is still where
        // to continue. Before 2.4.2 the page's own cursor replaced it, the
        // next scroll asked for page two, found only known posts, and paging
        // ended for that account. Only between cursors of the same source: a
        // cursor left by the source MTGA dropped cannot continue a Nitter one.
        val keepDeeperCursor = !isPagedFetch &&
            seenAgain.isNotEmpty() &&
            existing.nextCursor != null &&
            (incoming.nextCursor == null ||
                LegacyCursor.fromDroppedSource(incoming.nextCursor) ==
                LegacyCursor.fromDroppedSource(existing.nextCursor))

        val combined = incoming.copy(
            posts = (refreshed + newPosts).sortedWith(PROFILE_ORDER),
            displayName = incoming.displayName.ifBlank { existing.displayName },
            avatarUrl = incoming.avatarUrl ?: existing.avatarUrl,
            bio = incoming.bio ?: existing.bio,
            bannerUrl = incoming.bannerUrl ?: existing.bannerUrl,
            location = incoming.location ?: existing.location,
            website = incoming.website ?: existing.website,
            joined = incoming.joined ?: existing.joined,
            stats = incoming.stats ?: existing.stats,
            nextCursor = when {
                exhausted -> null
                keepDeeperCursor -> existing.nextCursor
                else -> incoming.nextCursor ?: existing.nextCursor
            }
        )
        // Everything needed to tell a post that was dropped from a post that
        // was never fetched. A refresh whose page meets nothing already stored
        // has landed clear of the store, which leaves a hole between the two.
        val newestStored = existing.posts.maxOfOrNull { it.publishedAtMillis } ?: 0L
        val oldestIncoming = incoming.posts.minOfOrNull { it.publishedAtMillis } ?: 0L
        val hole = !isPagedFetch && seenAgain.isEmpty() &&
            existing.posts.isNotEmpty() && incoming.posts.isNotEmpty() &&
            oldestIncoming > newestStored
        log.record(
            kind = RequestLog.Kind.CACHE,
            url = "cache/${incoming.handle}",
            outcome = if (hole) "gap" else "ok",
            detail = buildString {
                append(if (isPagedFetch) "paged" else "refresh")
                append(" | in: ${incoming.posts.size}")
                append(" | new: ${newPosts.size}")
                append(" | seen again: ${seenAgain.size}")
                append(" | stored was: ${existing.posts.size} now: ${combined.posts.size}")
                append(" | in newest: ${incoming.posts.maxByOrNull { it.publishedAtMillis }?.id ?: "NONE"}")
                append(" oldest: ${incoming.posts.minByOrNull { it.publishedAtMillis }?.id ?: "NONE"}")
                append(" | stored newest was: ${existing.posts.maxByOrNull { it.publishedAtMillis }?.id ?: "NONE"}")
                append(" | pins in: ${incoming.posts.count { it.isPinned }} stored: ${combined.posts.count { it.isPinned }}")
                append(" | pin authority: ${if (statesPins) "yes" else "no"}")
                append(
                    " | cursor: " + when {
                        exhausted -> "STOPPED, page brought nothing new"
                        keepDeeperCursor -> "kept deeper"
                        incoming.nextCursor != null -> "from this page"
                        existing.nextCursor != null -> "kept existing"
                        else -> "none"
                    }
                )
                if (hole) append(" | GAP, posts between the two are not stored and nothing will fetch them")
            }
        )
        write(combined)
        combined
    }

    /**
     * Finds one stored post. [hint] is the account whose file most likely holds
     * it. Otherwise every file is scanned, which is a few local reads and only
     * happens when a post is opened from a place that could not tell.
     */
    suspend fun find(id: String, hint: String?): Post? = withContext(Dispatchers.IO) {
        val wanted = PostId.normalize(id)
        hint?.let { read(it) }?.posts?.firstOrNull { it.id == wanted }?.let { return@withContext it }
        directory.listFiles().orEmpty().asSequence()
            .mapNotNull { file -> runCatching { json.decodeFromString<Feed>(file.readText()) }.getOrNull() }
            .map(::canonical)
            .firstNotNullOfOrNull { feed -> feed.posts.firstOrNull { it.id == wanted } }
    }

    /** Every saved post across all accounts, each once, newest first. */
    suspend fun allPosts(): List<Post> = withContext(Dispatchers.IO) {
        directory.listFiles().orEmpty().asSequence()
            .mapNotNull { file -> runCatching { json.decodeFromString<Feed>(file.readText()) }.getOrNull() }
            .flatMap { canonical(it).posts.asSequence() }
            .distinctBy { it.id }
            .sortedByDescending { it.publishedAtMillis }
            .toList()
    }

    suspend fun forget(handle: String) = withContext(Dispatchers.IO) {
        scrolledBack.remove(handle.lowercase())
        runCatching { fileFor(handle).delete() }
        Unit
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        scrolledBack.clear()
        runCatching { directory.listFiles()?.forEach { it.delete() } }
        Unit
    }

    suspend fun sizeBytes(): Long = withContext(Dispatchers.IO) {
        directory.listFiles()?.sumOf { it.length() } ?: 0L
    }

    /**
     * Rewrites ids to their canonical form and drops duplicates. Also repairs
     * caches written before 1.1.3, where the same post could be stored twice
     * under two id formats. The cleaned version is persisted on the next write.
     */
    private fun canonical(feed: Feed): Feed {
        val posts = feed.posts
            .map { post -> PostId.normalize(post.id).let { if (it == post.id) post else post.copy(id = it) } }
            .distinctBy { it.id }
        return if (posts == feed.posts) feed else feed.copy(posts = posts)
    }

    /**
     * Drops posts older than the reader's retention, and nothing else.
     *
     * There used to be a hard cap of 300 posts per account here. On an account
     * that posts twenty times in two hours that is barely three days of
     * history, and it could never be passed: each page fetched deeper pushed
     * the oldest posts straight back out at the next write, so scrolling back
     * looked like an upstream failure when it was this line. Depth is now the
     * reader's own business, governed by "Keep posts" alone.
     */
    private fun trim(feed: Feed): Feed {
        val days = retentionDays()
        val cutoff = if (days > 0) System.currentTimeMillis() - days * DAY_MS else Long.MIN_VALUE
        val kept = feed.posts
            // A pin survives the retention window. It is the author's own
            // statement about their profile, not a post that happens to be old.
            .filter { it.isPinned || it.publishedAtMillis <= 0L || it.publishedAtMillis >= cutoff }
        return if (kept.size == feed.posts.size) feed else feed.copy(posts = kept)
    }

    /**
     * Applies the retention to every saved account now, rather than at each
     * account's next fetch. Run at launch and when the setting changes.
     */
    suspend fun applyRetention() = withContext(Dispatchers.IO) {
        if (retentionDays() <= 0) return@withContext
        directory.listFiles().orEmpty().forEach { file ->
            runCatching {
                val feed = json.decodeFromString<Feed>(file.readText())
                val trimmed = trim(feed)
                if (trimmed !== feed) file.writeText(json.encodeToString(trimmed))
            }
        }
    }

    /**
     * Every post id the disk still holds.
     *
     * Read by the media store so a saved picture never outlives the post it
     * belongs to. Retention is set once, in "Keep posts", and it governs the
     * files as well as the text.
     */
    suspend fun storedPostIds(): Set<String> = withContext(Dispatchers.IO) {
        directory.listFiles().orEmpty().flatMapTo(HashSet()) { file ->
            runCatching { json.decodeFromString<Feed>(file.readText()).posts.map { it.id } }
                .getOrDefault(emptyList())
        }
    }

    private fun fileFor(handle: String) = File(directory, "${handle.lowercase()}.json")

    private companion object {
        /** Scrolling back further than this in one session drops the oldest again. */
        const val MAX_SCROLLED_BACK_PER_ACCOUNT = 1_000
        const val DAY_MS = 24L * 60 * 60 * 1000

        /**
         * A profile's own order: the pinned post first, then newest first, which
         * is how Nitter draws it. Sorting by date alone parked a pin that was a
         * week old at the very bottom of the feed, where it also became the
         * oldest post on screen, so paging behaved as though the account had
         * already been read back a week.
         */
        val PROFILE_ORDER: Comparator<Post> = compareByDescending<Post> { it.isPinned }
            .thenByDescending { it.publishedAtMillis }
    }
}
