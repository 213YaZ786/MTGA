package com.mtga.app.data.html

import com.mtga.app.core.common.AppError
import com.mtga.app.core.common.Outcome
import com.mtga.app.core.model.Feed
import com.mtga.app.core.network.ErrorMapper
import com.mtga.app.data.instances.NitterInstance
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
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
 */
class HtmlSource(
    private val client: HttpClient,
    private val parser: HtmlTimelineParser
) {

    suspend fun fetchProfile(
        instance: NitterInstance,
        handle: String,
        cursor: String? = null
    ): Outcome<Feed> = withContext(Dispatchers.IO) {
        // Cursors are opaque Nitter tokens containing +, / and =. Concatenating
        // one raw into a URL means the server sees a space where a plus was and
        // silently answers with page one, which reads as "loading did nothing".
        val url = buildString {
            append(instance.profileUrlFor(handle))
            if (!cursor.isNullOrBlank()) {
                append("?cursor=")
                append(URLEncoder.encode(cursor, StandardCharsets.UTF_8.name()))
            }
        }

        try {
            val response = client.get(url) {
                header("User-Agent", BROWSER_USER_AGENT)
                header("Accept", "text/html,application/xhtml+xml")
            }
            val body = response.bodyAsText()

            ErrorMapper.fromResponse(instance.host, response, body, handle)
                ?.let { return@withContext Outcome.Failure(it) }

            unavailableReason(body)?.let {
                return@withContext Outcome.Failure(AppError.AccountUnavailable(handle, it))
            }

            val feed = parser.parse(body, handle, instance.host)
                ?: return@withContext Outcome.Failure(
                    AppError.ParseFailure(
                        host = instance.host,
                        selectorSetVersion = HtmlTimelineParser.SELECTOR_SET_VERSION,
                        snippet = body.take(200)
                    )
                )

            Outcome.Success(feed)
        } catch (t: Throwable) {
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

    companion object {
        private const val BROWSER_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/124.0.0.0 Mobile Safari/537.36"
    }
}
