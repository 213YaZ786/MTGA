package com.mtga.app.data.xcom

import com.mtga.app.core.common.AppError
import com.mtga.app.core.common.Outcome
import com.mtga.app.core.debug.RequestLog
import com.mtga.app.core.model.Feed
import com.mtga.app.core.model.Post
import com.mtga.app.core.network.ErrorMapper
import com.mtga.app.core.network.HostThrottle
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.withContext

/**
 * Reads the newest posts straight from x.com.
 *
 * X server side renders the top of a public profile for logged out visitors.
 * That payload is small, roughly five posts, with no cursor, because scrolling
 * further calls authenticated endpoints. So this source is the most accurate
 * available for the head of a feed and useless for depth.
 *
 * Deliberately, the page is only mined for status ids. Everything else comes
 * from [SyndicationSource], which returns structured JSON. A digit run after
 * "/status/" is about as stable as markup gets, so a redesign of x.com costs us
 * nothing as long as posts still link to themselves.
 *
 * Cost to be honest about: these requests go to X directly, so X sees the
 * device's address and which profiles it opens. Nitter proxies that away. This
 * source is therefore switchable in Settings.
 */
class XComSource(
    private val client: HttpClient,
    private val syndication: SyndicationSource,
    private val log: RequestLog,
    private val throttle: HostThrottle
) {

    suspend fun fetchLatest(handle: String): Outcome<Feed> = withContext(Dispatchers.IO) {
        val url = "https://$HOST/$handle"
        if (!throttle.acquire(HOST)) {
            return@withContext Outcome.Failure(AppError.RateLimited(HOST, null))
        }

        val startedAt = System.nanoTime()
        val body = try {
            val response = client.get(url) {
                header("User-Agent", BROWSER_USER_AGENT)
                header("Accept", "text/html,application/xhtml+xml")
                header("Accept-Language", "en;q=0.9")
            }
            val text = response.bodyAsText()
            ErrorMapper.fromResponse(HOST, response, text, handle)?.let { error ->
                log.record(
                    RequestLog.Kind.PROFILE, url, "HTTP error",
                    response.status.value, text.length,
                    (System.nanoTime() - startedAt) / 1_000_000,
                    error::class.java.simpleName
                )
                return@withContext Outcome.Failure(error)
            }
            text
        } catch (t: Throwable) {
            // Leaving the screen cancels the read. That is not a failure of
            // the host and must not be logged or recorded as one.
            if (t is CancellationException) throw t
            log.record(
                RequestLog.Kind.PROFILE, url, "transport failure",
                durationMillis = (System.nanoTime() - startedAt) / 1_000_000,
                detail = "${t::class.java.simpleName}: ${t.message}"
            )
            return@withContext Outcome.Failure(ErrorMapper.fromThrowable(HOST, t))
        }

        val ids = extractStatusIds(body, handle)
        val elapsed = (System.nanoTime() - startedAt) / 1_000_000

        if (ids.isEmpty()) {
            log.record(
                RequestLog.Kind.PROFILE, url, "no status ids in page",
                bodyBytes = body.length, durationMillis = elapsed,
                detail = "logged out rendering may have changed, or the account is protected"
            )
            return@withContext Outcome.Failure(
                AppError.ParseFailure(HOST, SELECTOR_SET_VERSION, "no /status/ links found")
            )
        }

        val posts = ids.mapNotNull { syndication.fetchPost(it) }
        if (posts.isEmpty()) {
            log.record(
                RequestLog.Kind.PROFILE, url, "ids found but no posts fetched",
                bodyBytes = body.length, durationMillis = elapsed,
                detail = "ids: ${ids.size}, syndication returned nothing for all of them"
            )
            return@withContext Outcome.Failure(
                AppError.ParseFailure(HOST, SELECTOR_SET_VERSION, "syndication returned nothing")
            )
        }

        log.record(
            RequestLog.Kind.PROFILE, url, "ok",
            bodyBytes = body.length, durationMillis = elapsed,
            detail = "ids: ${ids.size} | posts: ${posts.size} | head of feed only, no cursor"
        )

        Outcome.Success(
            Feed(
                handle = handle,
                displayName = posts.first().authorName,
                posts = posts.sortedByDescending { it.publishedAtMillis },
                fetchedFromHost = HOST,
                fetchedAtMillis = System.currentTimeMillis(),
                avatarUrl = posts.first().avatarUrl,
                // No cursor exists here. Depth is Nitter and twstalker's job.
                nextCursor = null
            )
        )
    }

    /**
     * Pulls every "/status/<digits>" out of the page, keeping order and
     * dropping duplicates. Replies and quotes inside the payload belong to
     * other authors, so anything not under this handle's path is discarded.
     */
    internal fun extractStatusIds(body: String, handle: String): List<String> {
        val marker = "/status/"
        val ids = LinkedHashSet<String>()
        var cursor = 0

        while (ids.size < MAX_POSTS) {
            val at = body.indexOf(marker, cursor).takeIf { it >= 0 } ?: break
            val idStart = at + marker.length
            var idEnd = idStart
            while (idEnd < body.length && body[idEnd].isDigit()) idEnd++

            if (idEnd > idStart) {
                // Confirm the link belongs to this profile rather than to
                // someone quoted or replied to inside the same payload.
                val prefixStart = (at - handle.length - 1).coerceAtLeast(0)
                val owner = body.substring(prefixStart, at)
                if (owner.equals("/$handle", ignoreCase = true)) {
                    ids += body.substring(idStart, idEnd)
                }
            }
            cursor = idEnd.coerceAtLeast(at + marker.length)
        }
        return ids.toList()
    }

    companion object {
        const val HOST = "x.com"
        private const val MAX_POSTS = 10
        private const val SELECTOR_SET_VERSION = 1

        private const val BROWSER_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/124.0.0.0 Mobile Safari/537.36"
    }
}
