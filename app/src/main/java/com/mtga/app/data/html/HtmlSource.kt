package com.mtga.app.data.html

import com.mtga.app.core.common.AppError
import com.mtga.app.core.common.Outcome
import com.mtga.app.core.debug.RequestLog
import com.mtga.app.core.model.Conversation
import com.mtga.app.core.model.Feed
import com.mtga.app.core.model.ProfileTab
import com.mtga.app.core.network.ErrorMapper
import com.mtga.app.core.network.HostThrottle
import com.mtga.app.core.web.ChallengeGateway
import com.mtga.app.data.instances.NitterInstance
import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Reads a profile page and parses it. This is MTGA's primary source, because
 * the surviving instance restricts its RSS to approved clients while its web
 * pages remain open.
 *
 * Requests carry a browser User-Agent. These are real page views triggered by a
 * person tapping an account in the app, not crawling, and the feed reader
 * identity we use for RSS gets served differently here.
 *
 * Fetching goes through [ChallengeGateway], so a bot check in front of the
 * page is passed in an offscreen browser instead of ending the read.
 */
class HtmlSource(
    private val gateway: ChallengeGateway,
    private val parser: HtmlTimelineParser,
    private val log: RequestLog,
    private val throttle: HostThrottle
) {

    suspend fun fetchProfile(
        instance: NitterInstance,
        handle: String,
        cursor: String? = null,
        tab: ProfileTab = ProfileTab.POSTS
    ): Outcome<Feed> = withContext(Dispatchers.IO) {
        // Cursors are opaque Nitter tokens containing +, / and =. Concatenating
        // one raw into a URL means the server sees a space where a plus was and
        // silently answers with page one, which reads as "loading did nothing".
        val url = buildString {
            append(instance.profileUrlFor(handle))
            append(tab.path)
            val params = listOfNotNull(
                tab.query,
                cursor?.takeIf { it.isNotBlank() }
                    ?.let { "cursor=" + URLEncoder.encode(it, StandardCharsets.UTF_8.name()) }
            )
            if (params.isNotEmpty()) append("?").append(params.joinToString("&"))
        }

        val startedAt = System.nanoTime()
        val kind = if (cursor == null) RequestLog.Kind.PROFILE else RequestLog.Kind.PAGE

        // Wait our turn, or hand the job to another instance if this one is
        // still cooling off from a 429.
        if (!throttle.acquire(instance.host)) {
            val remaining = throttle.cooldownRemainingMs(instance.host) / 1_000
            log.record(
                kind = kind,
                url = url,
                outcome = "skipped, cooling down",
                detail = "${remaining}s left on this host"
            )
            return@withContext Outcome.Failure(AppError.RateLimited(instance.host, remaining))
        }

        try {
            val page = gateway.getPage(
                url = url,
                host = instance.host,
                kind = kind,
                requestHeaders = mapOf(
                    "User-Agent" to BROWSER_USER_AGENT,
                    "Accept" to "text/html,application/xhtml+xml"
                )
            )
            val body = page.body
            val elapsed = (System.nanoTime() - startedAt) / 1_000_000

            if (error429(page.status)) {
                throttle.penalise(instance.host, page.retryAfterSeconds)
            } else {
                throttle.clear(instance.host)
            }

            ErrorMapper.fromStatus(
                host = instance.host,
                url = url,
                code = page.status,
                retryAfterSeconds = page.retryAfterSeconds,
                bodyHint = body,
                handle = handle
            )?.let { error ->
                log.record(
                    kind = kind,
                    url = url,
                    outcome = "HTTP error",
                    httpStatus = page.status,
                    bodyBytes = body.length,
                    durationMillis = elapsed,
                    detail = error::class.java.simpleName + " via " + page.via.name
                )
                return@withContext Outcome.Failure(error)
            }

            unavailableReason(body)?.let {
                log.record(
                    kind = kind,
                    url = url,
                    outcome = "account unavailable",
                    httpStatus = page.status,
                    bodyBytes = body.length,
                    durationMillis = elapsed,
                    detail = it
                )
                return@withContext Outcome.Failure(AppError.AccountUnavailable(handle, it))
            }

            val feed = parser.parse(body, handle, instance.host)
            if (feed == null) {
                log.record(
                    kind = kind,
                    url = url,
                    outcome = "parse found no posts",
                    httpStatus = page.status,
                    bodyBytes = body.length,
                    durationMillis = elapsed,
                    detail = "timeline-item markers: ${body.split("class=\"timeline-item").size - 1}" +
                        " | page text: " + plainSummary(body)
                )
                return@withContext Outcome.Failure(
                    AppError.ParseFailure(
                        host = instance.host,
                        selectorSetVersion = HtmlTimelineParser.SELECTOR_SET_VERSION,
                        snippet = plainSummary(body)
                    )
                )
            }

            log.record(
                kind = kind,
                url = url,
                outcome = "ok",
                httpStatus = page.status,
                bodyBytes = body.length,
                durationMillis = elapsed,
                detail = "posts: ${feed.posts.size} | via ${page.via.name} | next cursor: " +
                    (feed.nextCursor?.take(24)?.plus("...") ?: "NONE FOUND")
            )
            Outcome.Success(feed)
        } catch (t: Throwable) {
            // Leaving the screen cancels the read. That is not a failure of
            // the host and must not be logged or recorded as one.
            if (t is CancellationException) throw t
            log.record(
                kind = kind,
                url = url,
                outcome = "transport failure",
                durationMillis = (System.nanoTime() - startedAt) / 1_000_000,
                detail = "${t::class.java.simpleName}: ${t.message}"
            )
            Outcome.Failure(ErrorMapper.fromThrowable(instance.host, t))
        }
    }

    /**
     * Reads a post's own page for its conversation. Same path as a profile:
     * throttled, through the bot check gateway, and fully logged.
     */
    suspend fun fetchConversation(
        instance: NitterInstance,
        handle: String,
        id: String
    ): Outcome<Conversation> = withContext(Dispatchers.IO) {
        val url = "${instance.profileUrlFor(handle)}/status/$id"
        val kind = RequestLog.Kind.THREAD
        val startedAt = System.nanoTime()

        if (!throttle.acquire(instance.host)) {
            val remaining = throttle.cooldownRemainingMs(instance.host) / 1_000
            log.record(kind = kind, url = url, outcome = "skipped, cooling down", detail = "${remaining}s left on this host")
            return@withContext Outcome.Failure(AppError.RateLimited(instance.host, remaining))
        }

        try {
            val page = gateway.getPage(
                url = url,
                host = instance.host,
                kind = kind,
                requestHeaders = mapOf(
                    "User-Agent" to BROWSER_USER_AGENT,
                    "Accept" to "text/html,application/xhtml+xml"
                )
            )
            val body = page.body
            val elapsed = (System.nanoTime() - startedAt) / 1_000_000

            if (error429(page.status)) throttle.penalise(instance.host, page.retryAfterSeconds) else throttle.clear(instance.host)

            // On a post page a 404 is about the post, not the account, and a
            // page can also say so with a 200 (through the browser path, or
            // a main post drawn as unavailable). Checks and rate limits keep
            // their own meaning.
            val statusError = ErrorMapper.fromStatus(
                host = instance.host,
                url = url,
                code = page.status,
                retryAfterSeconds = page.retryAfterSeconds,
                bodyHint = body,
                handle = handle
            )
            val unavailable = parser.unavailableReason(body)
            val error = when {
                statusError is AppError.ChallengeRequired || statusError is AppError.RateLimited -> statusError
                statusError is AppError.AccountNotFound -> AppError.PostUnavailable(instance.host, unavailable)
                // A 200 that says "unavailable" is decided after parsing, below.
                else -> statusError
            }
            if (error != null) {
                log.record(
                    kind = kind, url = url,
                    outcome = if (error is AppError.PostUnavailable) "post unavailable" else "HTTP error",
                    httpStatus = page.status,
                    bodyBytes = body.length, durationMillis = elapsed,
                    detail = (if (error is AppError.PostUnavailable) "says: ${unavailable ?: "not found"} | " else "") +
                        error::class.java.simpleName + " via " + page.via.name
                )
                return@withContext Outcome.Failure(error)
            }

            val conversation = parser.parseConversation(body, instance.host)

            // The page answered but the post itself is not on it. A thread
            // around a missing post is not what was asked for.
            if (unavailable != null && conversation?.main == null) {
                log.record(
                    kind = kind, url = url, outcome = "post unavailable", httpStatus = page.status,
                    bodyBytes = body.length, durationMillis = elapsed,
                    detail = "says: $unavailable | via ${page.via.name}"
                )
                return@withContext Outcome.Failure(AppError.PostUnavailable(instance.host, unavailable))
            }

            if (conversation == null) {
                log.record(
                    kind = kind, url = url, outcome = "parse found no conversation", httpStatus = page.status,
                    bodyBytes = body.length, durationMillis = elapsed, detail = "page text: " + plainSummary(body)
                )
                return@withContext Outcome.Failure(
                    AppError.ParseFailure(instance.host, HtmlTimelineParser.SELECTOR_SET_VERSION, plainSummary(body))
                )
            }

            log.record(
                kind = kind, url = url, outcome = "ok", httpStatus = page.status,
                bodyBytes = body.length, durationMillis = elapsed,
                // "main" first: before 1.14.2 a post read fine with no replies
                // logged all zeros and looked like an empty page.
                detail = "main: ${if (conversation.main != null) "yes" else "no"}" +
                    " | before: ${conversation.ancestors.size} | thread: ${conversation.continuation.size}" +
                    " | reply chains: ${conversation.replies.size} | via ${page.via.name}"
            )
            Outcome.Success(conversation)
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            log.record(
                kind = kind, url = url, outcome = "transport failure",
                durationMillis = (System.nanoTime() - startedAt) / 1_000_000,
                detail = "${t::class.java.simpleName}: ${t.message}"
            )
            Outcome.Failure(ErrorMapper.fromThrowable(instance.host, t))
        }
    }

    /**
     * Nitter renders upstream problems as an error panel with HTTP 200, so the
     * status code alone would call these successes.
     */
    private fun unavailableReason(body: String): String? {
        val lower = body.lowercase()
        return when {
            "user has been suspended" in lower -> "X has suspended this account."
            "tweets are protected" in lower -> "This account is protected."
            "user not found" in lower -> null
            else -> null
        }
    }

    private fun error429(status: Int) = status == 429

    /** Tags stripped so the log shows what a reader would see, not the doctype. */
    private fun plainSummary(body: String): String {
        val text = StringBuilder()
        var inTag = false
        for (c in body) {
            when {
                c == '<' -> inTag = true
                c == '>' -> inTag = false
                !inTag -> text.append(c)
            }
            if (text.length > 3_000) break
        }
        return text.toString().replace(Regex("\\s+"), " ").trim().take(300)
            .ifBlank { "empty body" }
    }

    companion object {
        private const val BROWSER_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/124.0.0.0 Mobile Safari/537.36"
    }
}
