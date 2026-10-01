package com.mtga.app.data.archive

import java.io.File
import kotlinx.coroutines.launch
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.CoroutineScope
import com.mtga.app.core.common.writeTextAtomically
import android.content.Context
import com.mtga.app.core.common.Outcome
import com.mtga.app.core.debug.RequestLog
import com.mtga.app.core.model.ArchiveCursor
import com.mtga.app.core.model.Feed
import com.mtga.app.core.model.Post
import com.mtga.app.data.settings.SettingsStore
import com.mtga.app.data.xcom.SyndicationSource
import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException

/**
 * An account's older posts, past what any source pages to: X shows a logged
 * out visitor about five posts, Nitter pages only so deep. Post ids cannot
 * be guessed (a snowflake holds its time to the millisecond, then a worker
 * and a counter), but web archives keep the addresses of posts people saved:
 *
 * - the Internet Archive's index of x.com and twitter.com status pages, deep
 *   and wide for known accounts, a few weeks behind;
 * - DuckDuckGo's results for the account's status pages, the latest weeks.
 *
 * Each post is then read from X's embed CDN by its id (SyndicationSource),
 * with its media, so nothing needs to be saved to be read again while the
 * post exists. The id list is asked once per account and kept for a while.
 *
 * Each list is its own setting. A list kept from a lookup that did not ask a
 * list switched on since is asked again; one asked of a list switched off
 * since is kept until it expires, it costs no request.
 */
class ArchiveSource(
    context: Context,
    private val client: HttpClient,
    private val syndication: SyndicationSource,
    private val log: RequestLog,
    private val settings: SettingsStore
) {

    /** [asked] names the lists the lookup asked, [ARCHIVE] and [DUCKDUCKGO]. */
    private class Known(val atMillis: Long, val ids: List<Long>, val asked: Set<String>)

    private val known = ConcurrentHashMap<String, Known>()
    private val asking = ConcurrentHashMap<String, Deferred<List<Long>>>()
    private val failedAt = ConcurrentHashMap<String, Long>()
    /**
     * Ids X's CDN said are gone (deleted, withheld, age restricted), per
     * account: never asked again. Before 2.15.0 the same five deleted posts
     * were read again at every refresh.
     */
    private val gone = ConcurrentHashMap<String, MutableSet<Long>>()
    private val reading = Semaphore(PARALLEL)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val folder = File(context.filesDir, "archive").apply { mkdirs() }

    /**
     * Starts asking the archives for [handle]'s ids, without waiting: done
     * when a profile opens, so the list is ready by the time the reader
     * reaches the end of what the other sources give. The archive's index
     * can take a minute to answer.
     */
    fun warm(handle: String) {
        scope.launch { runCatching { ids(handle) } }
    }

    /** A page of the account's posts older than [below], and where the next starts. */
    suspend fun page(handle: String, below: Long): Outcome<Feed> = withContext(Dispatchers.IO) {
        val ids = ids(handle)
        val dropped = goneFor(handle)
        var cursor = below
        // A batch where every post is gone (deleted, age restricted) is not
        // the end: the next batch is tried, a few times at most.
        repeat(EMPTY_BATCHES_TRIED) {
            val batch = ids.filter { it < cursor && it !in dropped }.take(PAGE)
            if (batch.isEmpty()) {
                log.record(
                    RequestLog.Kind.PAGE, "archive/$handle",
                    if (ids.isEmpty()) "archive: no post ids known for this account" else "archive: nothing older known, the end",
                    detail = "below ${java.time.Instant.ofEpochMilli((cursor shr 22) + 1_288_834_974_657L)}"
                )
                return@withContext Outcome.Success(feed(handle, emptyList(), null))
            }
            val started = System.currentTimeMillis()
            val posts = readAll(handle, batch)
            cursor = batch.min()
            val more = ids.any { it < cursor }
            log.record(
                RequestLog.Kind.PAGE, "archive/$handle", "archive: ${posts.size} of ${batch.size} posts read",
                durationMillis = System.currentTimeMillis() - started,
                detail = "from ${java.time.Instant.ofEpochMilli((batch.max() shr 22) + 1_288_834_974_657L)} | ${ids.count { it < cursor }} older known"
            )
            if (posts.isNotEmpty() || !more) {
                return@withContext Outcome.Success(feed(handle, posts, if (more) ArchiveCursor.of(cursor) else null))
            }
        }
        Outcome.Success(feed(handle, emptyList(), ArchiveCursor.of(cursor)))
    }

    /**
     * The account's posts the archives know and the phone does not hold yet,
     * newest first, between [sinceMillis] and [untilMillis], [max] at most: what a refresh
     * adds to fill the gaps the other sources leave. The next pass goes on
     * where this one stopped, the posts it added being held by then.
     */
    suspend fun fill(handle: String, held: Set<String>, sinceMillis: Long, untilMillis: Long, max: Int): List<Post> = withContext(Dispatchers.IO) {
        val ids = ids(handle)
        val dropped = goneFor(handle)
        val missing = ids.asSequence()
            .filter { ((it shr 22) + 1_288_834_974_657L) in sinceMillis..untilMillis }
            .filter { it.toString() !in held && it !in dropped }
            .take(max)
            .toList()
        if (missing.isEmpty()) {
            log.record(RequestLog.Kind.PAGE, "archive/$handle", "archive: no gap to fill", detail = "${ids.size} ids known | ${dropped.size} gone")
            return@withContext emptyList()
        }
        val started = System.currentTimeMillis()
        val posts = readAll(handle, missing)
        log.record(
            RequestLog.Kind.PAGE, "archive/$handle", "archive: ${posts.size} of ${missing.size} missing posts read",
            durationMillis = System.currentTimeMillis() - started,
            detail = "newest ${java.time.Instant.ofEpochMilli((missing.max() shr 22) + 1_288_834_974_657L)}, oldest ${java.time.Instant.ofEpochMilli((missing.min() shr 22) + 1_288_834_974_657L)}"
        )
        posts
    }

    /**
     * Reads [ids] from X's CDN, 4 at a time, and remembers those that are
     * gone. A post by someone else (an address that named the wrong account)
     * will never be this account's either. The whole text of a long post is
     * asked of FxTwitter only while that source is on.
     */
    private suspend fun readAll(handle: String, ids: List<Long>): List<Post> {
        val longText = settings.current.useFxTwitter
        val reads = coroutineScope {
            ids.map { id -> async { id to reading.withPermit { syndication.read(id.toString(), throttled = false, completeLongText = longText) } } }.awaitAll()
        }
        val posts = mutableListOf<Post>()
        val newlyGone = mutableListOf<Long>()
        for ((id, read) in reads) {
            when (read) {
                is SyndicationSource.Read.Found ->
                    if (read.post.authorHandle.equals(handle, ignoreCase = true)) posts += read.post else newlyGone += id
                SyndicationSource.Read.Gone -> newlyGone += id
                SyndicationSource.Read.Failed -> Unit
            }
        }
        if (newlyGone.isNotEmpty()) {
            val set = goneFor(handle)
            set += newlyGone
            runCatching { File(folder, "${handle.lowercase()}.gone").writeTextAtomically(set.joinToString("\n")) }
            log.record(RequestLog.Kind.PAGE, "archive/$handle", "archive: ${newlyGone.size} posts gone, not asked again", detail = "${set.size} gone in all")
        }
        return posts
    }

    private fun goneFor(handle: String): MutableSet<Long> =
        gone.getOrPut(handle.lowercase()) {
            val stored = runCatching { File(folder, "${handle.lowercase()}.gone").readLines().mapNotNull { it.trim().toLongOrNull() } }.getOrDefault(emptyList())
            java.util.Collections.newSetFromMap(ConcurrentHashMap<Long, Boolean>()).apply { addAll(stored) }
        }

    private fun feed(handle: String, posts: List<Post>, next: String?) = Feed(
        handle = handle,
        // Blank: the stored profile keeps its name.
        displayName = "",
        posts = posts.sortedByDescending { it.publishedAtMillis },
        fetchedFromHost = SyndicationSource.HOST,
        fetchedAtMillis = System.currentTimeMillis(),
        nextCursor = next
    )

    /**
     * Every id the archives know for [handle], newest first. Kept a day in
     * memory and on disk; one lookup at a time per account.
     */
    private suspend fun ids(handle: String): List<Long> {
        val key = handle.lowercase()
        val now = System.currentTimeMillis()
        val wanted = wanted()
        if (wanted.isEmpty()) return emptyList()
        fun usable(entry: Known?) = entry?.takeIf { now - it.atMillis < KEEP_MS && it.asked.containsAll(wanted) }
        usable(known[key])?.let { return it.ids }
        usable(stored(key))?.let { known[key] = it; return it.ids }
        failedAt[key]?.takeIf { now - it < RETRY_AFTER_MS }?.let { return emptyList() }
        val pending = asking.computeIfAbsent(key) { scope.async { lookUp(handle, wanted) } }
        return try {
            pending.await()
        } finally {
            asking.remove(key, pending)
        }
    }

    /** The lists switched on. */
    private fun wanted(): Set<String> = buildSet {
        if (settings.current.olderFromArchives) add(ARCHIVE)
        if (settings.current.useDuckDuckGo) add(DUCKDUCKGO)
    }

    private suspend fun lookUp(handle: String, wanted: Set<String>): List<Long> {
        val key = handle.lowercase()
        val archive = ARCHIVE in wanted
        val duck = DUCKDUCKGO in wanted
        // The lookups at once: the archive's index can take many seconds.
        val (onX, onTwitter, onSearch) = coroutineScope {
            listOf(
                async { if (archive) read(cdx("x.com", handle))?.let { ArchiveIds.fromCdx(it, handle) }.orEmpty() else emptySet() },
                async { if (archive) read(cdx("twitter.com", handle))?.let { ArchiveIds.fromCdx(it, handle) }.orEmpty() else emptySet() },
                async { if (duck) read("https://html.duckduckgo.com/html/?q=site%3Ax.com%2F$handle%2Fstatus", tries = 1)?.let { ArchiveIds.fromLinks(it, handle) }.orEmpty() else emptySet() }
            ).awaitAll()
        }
        val found = onX + onTwitter + onSearch
        // What each source brought, and what only DuckDuckGo knew: the latest
        // weeks the archive has not caught yet.
        log.record(
            RequestLog.Kind.LIST, "archive/$handle", "archive: ids by source",
            detail = (if (archive) "archive x.com ${onX.size}, archive twitter.com ${onTwitter.size}" else "Internet Archive off") +
                ", " + (if (duck) "DuckDuckGo ${onSearch.size} (${(onSearch - onX - onTwitter).size} only there)" else "DuckDuckGo off")
        )
        val ids = ArchiveIds.plausible(found, System.currentTimeMillis())
        log.record(RequestLog.Kind.LIST, "archive/$handle", "archive: ${ids.size} post ids known")
        if (ids.isEmpty()) failedAt[key] = System.currentTimeMillis()
        if (ids.isNotEmpty()) {
            failedAt.remove(key)
            val entry = Known(System.currentTimeMillis(), ids, wanted)
            known[key] = entry
            runCatching { File(folder, "$key.txt").writeTextAtomically("${entry.atMillis}|${wanted.joinToString(",")}\n" + ids.joinToString("\n")) }
        }
        return ids
    }

    /** A file written before 2.15.0 has no list names: both were asked then. */
    private fun stored(key: String): Known? = runCatching {
        val lines = File(folder, "$key.txt").readLines()
        val head = lines.first()
        val asked = if ('|' in head) head.substringAfter('|').split(',').filter { it.isNotBlank() }.toSet() else setOf(ARCHIVE, DUCKDUCKGO)
        Known(head.substringBefore('|').toLong(), lines.drop(1).mapNotNull { it.toLongOrNull() }, asked)
    }.getOrNull()

    /**
     * The index sorts addresses as text, so the newest ids come last; a
     * negative limit takes the rows from the end, the newest when an account
     * has more than the limit.
     */
    private fun cdx(host: String, handle: String) =
        "https://web.archive.org/cdx/search/cdx?url=$host/$handle/status/*&output=json&fl=original&collapse=urlkey&limit=-$CDX_LIMIT"

    /** A slow answer is tried again once: the archive's index is often just busy. */
    private suspend fun read(url: String, tries: Int = 2): String? {
        repeat(tries - 1) { readOnce(url)?.let { return it } }
        return readOnce(url)
    }

    private suspend fun readOnce(url: String): String? {
        val started = System.currentTimeMillis()
        return try {
            val response = client.get(url) {
                header("User-Agent", BROWSER_USER_AGENT)
                header("Accept", "text/html,application/json")
                timeout {
                    requestTimeoutMillis = LOOKUP_TIMEOUT_MS
                    socketTimeoutMillis = LOOKUP_TIMEOUT_MS
                }
            }
            val body = response.bodyAsText()
            log.record(
                RequestLog.Kind.LIST, url, if (response.status.value == 200) "ok" else "http ${response.status.value}",
                httpStatus = response.status.value, bodyBytes = body.length,
                durationMillis = System.currentTimeMillis() - started
            )
            body.takeIf { response.status.value == 200 }
        } catch (failure: Throwable) {
            if (failure is CancellationException) throw failure
            log.record(RequestLog.Kind.LIST, url, "transport failure", durationMillis = System.currentTimeMillis() - started, detail = failure.toString())
            null
        }
    }

    companion object {
        const val PAGE = 12
        private const val ARCHIVE = "archive"
        private const val DUCKDUCKGO = "duckduckgo"
        private const val PARALLEL = 4
        private const val EMPTY_BATCHES_TRIED = 3
        private const val CDX_LIMIT = 20_000
        private const val KEEP_MS = 24 * 60 * 60_000L
        private const val LOOKUP_TIMEOUT_MS = 60_000L
        private const val RETRY_AFTER_MS = 15 * 60_000L
        private const val BROWSER_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
    }
}

/** Post ids out of what the archives answer. Pure, tested. */
internal object ArchiveIds {

    /** The Internet Archive's CDX answer: a JSON list of rows holding the original address. */
    fun fromCdx(body: String, handle: String): Set<Long> = fromLinks(body, handle)

    /**
     * Every "<handle>/status/<id>" in a page, whatever the host and the case,
     * links written for an address bar (%2F) included. A plain search: a
     * case blind pattern over the archive's 2.6 MB answer for a busy account
     * took about a minute on a phone.
     */
    fun fromLinks(body: String, handle: String): Set<Long> {
        val text = (if ('%' in body) body.replace("%2F", "/").replace("%2f", "/") else body).lowercase()
        val needle = handle.lowercase() + "/status"
        val ids = HashSet<Long>()
        var at = text.indexOf(needle)
        while (at >= 0) {
            val before = if (at == 0) '/' else text[at - 1]
            var i = at + needle.length
            if (text.startsWith("es", i)) i += 2
            if ((before == '/' || before == '"' || before == '\'') && i < text.length && text[i] == '/') {
                val start = ++i
                while (i < text.length && text[i].isDigit() && i - start <= MAX_DIGITS) i++
                if (i - start in MIN_DIGITS..MAX_DIGITS) text.substring(start, i).toLongOrNull()?.let(ids::add)
            }
            at = text.indexOf(needle, at + needle.length)
        }
        return ids
    }

    /**
     * Newest first, keeping only ids whose time is possible: a mistyped or
     * padded id in an archived address would otherwise land in 2100.
     */
    fun plausible(ids: Set<Long>, nowMillis: Long): List<Long> =
        ids.filter { id ->
            val at = (id shr 22) + 1_288_834_974_657L
            id < 100_000_000_000_000L || at in EPOCH..(nowMillis + DAY)
        }.sortedDescending()

    private const val MIN_DIGITS = 6
    private const val MAX_DIGITS = 20
    private const val EPOCH = 1_288_834_974_657L
    private const val DAY = 86_400_000L
}
