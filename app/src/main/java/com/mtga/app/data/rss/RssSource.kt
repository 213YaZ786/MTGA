package com.mtga.app.data.rss

import com.mtga.app.core.common.AppError
import com.mtga.app.core.common.Outcome
import com.mtga.app.core.model.Feed
import com.mtga.app.core.network.ErrorMapper
import com.mtga.app.data.instances.InstancePool
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Fetches one account's feed, letting the pool decide which instance to use and
 * failing over automatically. This class never picks a host.
 */
class RssSource(
    private val client: HttpClient,
    private val pool: InstancePool,
    private val parser: RssFeedParser
) {

    /**
     * Detects the "RSS reader not yet whitelisted" notice some instances serve.
     * It is valid RSS with a 200, so nothing else in the stack would catch it,
     * and treating it as a feed means showing the reader a non post.
     */
    private fun gateRequestId(body: String): String? {
        if ("not yet whitelist" !in body.lowercase()) return null
        return longestHexRun(body).takeIf { it.length >= 32 } ?: ""
    }

    /** The operator's ID is a long hex string, so find the longest one present. */
    private fun longestHexRun(body: String): String {
        var best = ""
        var current = StringBuilder()
        for (c in body) {
            if (c.isDigit() || c in 'a'..'f') {
                current.append(c)
            } else {
                if (current.length > best.length) best = current.toString()
                current = StringBuilder()
            }
        }
        if (current.length > best.length) best = current.toString()
        return best
    }

    suspend fun fetchFeed(handle: String): Outcome<Feed> = pool.withInstance { instance ->
        withContext(Dispatchers.IO) {
            try {
                val response = client.get(instance.rssUrlFor(handle))
                val body = response.bodyAsText()

                val transportError = ErrorMapper.fromResponse(
                    host = instance.host,
                    response = response,
                    bodyHint = body,
                    handle = handle
                )
                if (transportError != null) return@withContext Outcome.Failure(transportError)

                gateRequestId(body)?.let { id ->
                    return@withContext Outcome.Failure(AppError.FeedGated(instance.host, id))
                }

                val feed = parser.parse(body, handle, instance.host)
                    ?: return@withContext Outcome.Failure(
                        AppError.ParseFailure(
                            host = instance.host,
                            selectorSetVersion = RSS_SELECTOR_SET_VERSION,
                            snippet = body.take(200)
                        )
                    )

                Outcome.Success(feed)
            } catch (t: Throwable) {
                Outcome.Failure(ErrorMapper.fromThrowable(instance.host, t))
            }
        }
    }

    private companion object {
        const val RSS_SELECTOR_SET_VERSION = 1
    }
}
