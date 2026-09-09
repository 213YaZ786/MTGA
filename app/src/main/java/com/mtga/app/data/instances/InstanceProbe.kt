package com.mtga.app.data.instances

import com.mtga.app.core.common.AppError
import com.mtga.app.core.network.ConnectivityMonitor
import com.mtga.app.core.network.ErrorMapper
import com.mtga.app.core.network.HttpClientFactory
import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ProbeResult(
    val instanceId: String,
    val latencyMillis: Long,
    val httpStatus: Int?,
    val error: AppError?
)

/**
 * One request against an instance, timed and classified.
 *
 * Probes the RSS host when the instance has one, since that is the endpoint
 * MTGA actually depends on. An instance whose web front end is up but whose
 * feeds are blocked is not useful to us, and this surfaces that.
 */
class InstanceProbe(
    private val client: HttpClient,
    private val connectivity: ConnectivityMonitor
) {

    suspend fun probe(instance: NitterInstance): ProbeResult = withContext(Dispatchers.IO) {
        if (!connectivity.isOnline()) {
            return@withContext ProbeResult(instance.id, 0, null, AppError.Offline)
        }

        val target = instance.rssUrlFor(PROBE_HANDLE)
        val started = System.nanoTime()

        try {
            val response = client.get(target) {
                timeout { requestTimeoutMillis = HttpClientFactory.PROBE_TIMEOUT_MS }
            }
            val body = runCatching { response.bodyAsText() }.getOrDefault("")
            val elapsed = elapsedMillis(started)
            val error = ErrorMapper.fromResponse(instance.host, response, body, PROBE_HANDLE)
                ?: verifyFeedShape(instance, body)

            ProbeResult(instance.id, elapsed, response.status.value, error)
        } catch (t: Throwable) {
            ProbeResult(
                instanceId = instance.id,
                latencyMillis = elapsedMillis(started),
                httpStatus = null,
                error = ErrorMapper.fromThrowable(instance.host, t)
            )
        }
    }

    /**
     * A 200 with no RSS in it means the instance answered but is not serving
     * feeds, which is a parse level failure rather than a network one.
     */
    private fun verifyFeedShape(instance: NitterInstance, body: String): AppError? {
        val head = body.take(1_000)
        val looksLikeFeed = "<rss" in head || "<feed" in head || "<?xml" in head
        return if (looksLikeFeed) null else AppError.ParseFailure(
            host = instance.host,
            selectorSetVersion = SELECTOR_SET_VERSION,
            snippet = head.take(200)
        )
    }

    private fun elapsedMillis(startedNanos: Long): Long =
        (System.nanoTime() - startedNanos) / 1_000_000

    companion object {
        /** A high profile handle that exists on any working instance. */
        const val PROBE_HANDLE = "nytimes"

        /** Bumped whenever the parsing expectations change. */
        const val SELECTOR_SET_VERSION = 1
    }
}
