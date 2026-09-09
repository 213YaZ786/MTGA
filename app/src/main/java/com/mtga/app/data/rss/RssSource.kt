package com.mtga.app.data.rss

import com.mtga.app.core.common.AppError
import com.mtga.app.core.common.Outcome
import com.mtga.app.core.model.Feed
import com.mtga.app.core.network.ErrorMapper
import com.mtga.app.data.instances.InstancePool
import com.mtga.app.data.instances.InstanceProbe
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

                val feed = parser.parse(body, handle, instance.host)
                    ?: return@withContext Outcome.Failure(
                        AppError.ParseFailure(
                            host = instance.host,
                            selectorSetVersion = InstanceProbe.SELECTOR_SET_VERSION,
                            snippet = body.take(200)
                        )
                    )

                Outcome.Success(feed)
            } catch (t: Throwable) {
                Outcome.Failure(ErrorMapper.fromThrowable(instance.host, t))
            }
        }
    }
}
