package com.mtga.app.data.instances

import com.mtga.app.core.common.AppError
import com.mtga.app.core.network.ConnectivityMonitor
import com.mtga.app.core.network.ErrorMapper
import com.mtga.app.core.network.HttpClientFactory
import com.mtga.app.data.html.HtmlTimelineParser
import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.header
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
 * Probes the profile page, because that is the path MTGA actually reads. An
 * earlier version probed RSS and reported xcancel as healthy while its feeds
 * were serving nothing but a whitelist notice. Probe what you depend on.
 */
class InstanceProbe(
    private val client: HttpClient,
    private val connectivity: ConnectivityMonitor,
    private val parser: HtmlTimelineParser
) {

    suspend fun probe(instance: NitterInstance): ProbeResult = withContext(Dispatchers.IO) {
        if (!connectivity.isOnline()) {
            return@withContext ProbeResult(instance.id, 0, null, AppError.Offline)
        }

        val started = System.nanoTime()

        try {
            val response = client.get(instance.profileUrlFor(PROBE_HANDLE)) {
                header("User-Agent", PROBE_USER_AGENT)
                header("Accept", "text/html,application/xhtml+xml")
                timeout { requestTimeoutMillis = HttpClientFactory.PROBE_TIMEOUT_MS }
            }
            val body = runCatching { response.bodyAsText() }.getOrDefault("")
            val elapsed = elapsedMillis(started)

            val error = ErrorMapper.fromResponse(instance.host, response, body, PROBE_HANDLE)
                ?: verifyTimeline(instance, body)

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
     * A 200 that yields no parseable posts means the instance answered but is
     * not serving content, which is a parse level failure rather than a
     * network one, and the reason "green" must mean "posts came back".
     */
    private fun verifyTimeline(instance: NitterInstance, body: String): AppError? =
        if (parser.parse(body, PROBE_HANDLE, instance.host) != null) {
            null
        } else {
            AppError.ParseFailure(
                host = instance.host,
                selectorSetVersion = HtmlTimelineParser.SELECTOR_SET_VERSION,
                snippet = body.take(200)
            )
        }

    private fun elapsedMillis(startedNanos: Long): Long =
        (System.nanoTime() - startedNanos) / 1_000_000

    companion object {
        /** A high profile handle that exists on any working instance. */
        const val PROBE_HANDLE = "nytimes"

        private const val PROBE_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/124.0.0.0 Mobile Safari/537.36"
    }
}
