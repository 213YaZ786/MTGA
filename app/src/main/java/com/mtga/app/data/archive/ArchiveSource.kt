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
 */
class ArchiveSource(
    context: Context,
    private val client: HttpClient,
    private val syndication: SyndicationSource,
    private val log: RequestLog
) {

    private class Known(val atMillis: Long, val ids: List<Long>)

    private val known = ConcurrentHashMap<String, Known>()
    private val asking = ConcurrentHashMap<String, Deferred<List<Long>>>()
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
        var cursor = below
        // A batch where every post is gone (deleted, age restricted) is not
        // the end: the next batch is tried, a few times at most.
        repeat(EMPTY_BATCHES_TRIED) {
            val batch = ids.filter { it < cursor }.take(PAGE)
            if (batch.isEmpty()) return@withContext Outcome.Success(feed(handle, emptyList(), null))
            val started = System.currentTimeMillis()
            val posts = coroutineScope {
                batch.map { id -> async { reading.withPermit { syndication.fetchPost(id.toString(), throttled = false) } } }.awaitAll()
            }.filterNotNull().filter { it.authorHandle.equals(handle, ignoreCase = true) }
            cursor = batch.min()
            val more = ids.any { it < cursor }
            log.record(
                RequestLog.Kind.PAGE, "archive/$handle", "archive: ${posts.size} of ${batch.size} posts read",
                durationMillis = System.currentTimeMillis() - started,
                detail = "below ${batch.max()} | ${ids.count { it < cursor }} more known"
            )
            if (posts.isNotEmpty() || !more) {
                return@withContext Outcome.Success(feed(handle, posts, if (more) ArchiveCursor.of(cursor) else null))
            }
        }
        Outcome.Success(feed(handle, emptyList(), ArchiveCursor.of(cursor)))
    }

    private fun feed(handle: String, posts: List<com.mtga.app.core.model.Post>, next: String?) = Feed(
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
        known[key]?.takeIf { now - it.atMillis < KEEP_MS }?.let { return it.ids }
        stored(key)?.takeIf { now - it.atMillis < KEEP_MS }?.let { known[key] = it; return it.ids }
        val pending = asking.computeIfAbsent(key) { scope.async { lookUp(handle) } }
        return try {
            pending.await()
        } finally {
            asking.remove(key, pending)
        }
    }

    private suspend fun lookUp(handle: String): List<Long> {
        val key = handle.lowercase()
        // The three lookups at once: the archive's index can take many seconds.
        val found = coroutineScope {
            listOf(
                async { read(cdx("x.com", handle))?.let { ArchiveIds.fromCdx(it, handle) }.orEmpty() },
                async { read(cdx("twitter.com", handle))?.let { ArchiveIds.fromCdx(it, handle) }.orEmpty() },
                async { read("https://html.duckduckgo.com/html/?q=site%3Ax.com%2F$handle%2Fstatus", tries = 1)?.let { ArchiveIds.fromLinks(it, handle) }.orEmpty() }
            ).awaitAll().flatten().toSet()
        }
        val ids = ArchiveIds.plausible(found, System.currentTimeMillis())
        log.record(RequestLog.Kind.LIST, "archive/$handle", "archive: ${ids.size} post ids known")
        if (ids.isNotEmpty()) {
            val entry = Known(System.currentTimeMillis(), ids)
            known[key] = entry
            runCatching { File(folder, "$key.txt").writeTextAtomically("${entry.atMillis}\n" + ids.joinToString("\n")) }
        }
        return ids
    }

    private fun stored(key: String): Known? = runCatching {
        val lines = File(folder, "$key.txt").readLines()
        Known(lines.first().toLong(), lines.drop(1).mapNotNull { it.toLongOrNull() })
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
        private const val PARALLEL = 4
        private const val EMPTY_BATCHES_TRIED = 3
        private const val CDX_LIMIT = 20_000
        private const val KEEP_MS = 24 * 60 * 60_000L
        private const val LOOKUP_TIMEOUT_MS = 60_000L
        private const val BROWSER_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
    }
}

/** Post ids out of what the archives answer. Pure, tested. */
internal object ArchiveIds {

    /** The Internet Archive's CDX answer: a JSON list of rows holding the original address. */
    fun fromCdx(body: String, handle: String): Set<Long> = fromLinks(body, handle)

    /** Every "<handle>/status/<id>" in a page, whatever the host and the case. */
    fun fromLinks(body: String, handle: String): Set<Long> =
        Regex("(?i)(?:^|[/\"'])${Regex.escape(handle)}/status(?:es)?/(\\d{6,20})")
            .findAll(body).mapNotNull { it.groupValues[1].toLongOrNull() }.toSet()

    /**
     * Newest first, keeping only ids whose time is possible: a mistyped or
     * padded id in an archived address would otherwise land in 2100.
     */
    fun plausible(ids: Set<Long>, nowMillis: Long): List<Long> =
        ids.filter { id ->
            val at = (id shr 22) + 1_288_834_974_657L
            id < 100_000_000_000_000L || at in EPOCH..(nowMillis + DAY)
        }.sortedDescending()

    private const val EPOCH = 1_288_834_974_657L
    private const val DAY = 86_400_000L
}
