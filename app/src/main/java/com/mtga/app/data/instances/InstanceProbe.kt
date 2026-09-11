package com.mtga.app.data.instances

import com.mtga.app.core.common.AppError
import com.mtga.app.core.debug.RequestLog
import com.mtga.app.core.network.ConnectivityMonitor
import com.mtga.app.core.network.HostThrottle
import com.mtga.app.core.web.ChallengeGateway
import com.mtga.app.core.web.WebSession
import com.mtga.app.data.accounts.AccountStore
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
    val error: AppError?,
    /** Answered through the offscreen browser rather than the native client. */
    val viaBrowser: Boolean = false,
    /** A real read, not a probe, got posts. Recorded by the pool. */
    val delivered: Boolean = false
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
    private val parser: HtmlTimelineParser,
    private val accounts: AccountStore,
    private val gateway: ChallengeGateway,
    private val session: WebSession,
    private val throttle: HostThrottle
) {

    /**
     * Probes with an account you actually follow when there is one, so the
     * check measures what you read rather than a stranger's profile. Falls back
     * to well known handles, and tries more than one, because a single account
     * being unavailable on an instance says nothing about the instance.
     */
    suspend fun probe(instance: NitterInstance): ProbeResult {
        if (!connectivity.isOnline()) {
            return ProbeResult(instance.id, 0, null, AppError.Offline)
        }

        var last: ProbeResult? = null
        for (handle in probeHandles()) {
            val result = probeWith(instance, handle)
            if (result.error == null) return result
            last = result
            // A parse failure might be this one account, anything else is about
            // the instance itself and retrying with another handle is pointless.
            if (result.error !is AppError.ParseFailure) return result
        }
        return last ?: ProbeResult(instance.id, 0, null, AppError.Unknown("no probe handle"))
    }

    private fun probeHandles(): List<String> =
        (accounts.accounts.value.take(1).map { it.handle } + FALLBACK_HANDLES).distinct()

    private suspend fun probeWith(
        instance: NitterInstance,
        handle: String
    ): ProbeResult = withContext(Dispatchers.IO) {
        val started = System.nanoTime()

        // A host cleared this session is read through its check, and xcancel
        // only ever answers the offscreen browser. Probing it natively showed
        // "asks for a check" while reading worked. Probe the path reads take.
        if (session.isCleared(instance.host)) return@withContext probeThroughCheck(instance, handle, started)

        try {
            val response = client.get(instance.profileUrlFor(handle)) {
                header("User-Agent", PROBE_USER_AGENT)
                header("Accept", "text/html,application/xhtml+xml")
                timeout { requestTimeoutMillis = HttpClientFactory.PROBE_TIMEOUT_MS }
            }
            val body = runCatching { response.bodyAsText() }.getOrDefault("")
            val elapsed = elapsedMillis(started)

            val error = ErrorMapper.fromResponse(instance.host, response, body, handle)
                ?: verifyTimeline(instance, body, handle)

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

    private suspend fun probeThroughCheck(
        instance: NitterInstance,
        handle: String,
        started: Long
    ): ProbeResult {
        // Same pacing as a read. A probe must never be what trips a rate limit.
        if (!throttle.acquire(instance.host)) {
            val remaining = throttle.cooldownRemainingMs(instance.host) / 1_000
            return ProbeResult(instance.id, 0, null, AppError.RateLimited(instance.host, remaining))
        }
        return try {
            val page = gateway.getPage(
                url = instance.profileUrlFor(handle),
                host = instance.host,
                kind = RequestLog.Kind.PROBE,
                requestHeaders = mapOf(
                    "User-Agent" to (session.userAgent ?: PROBE_USER_AGENT),
                    "Accept" to "text/html,application/xhtml+xml"
                )
            )
            val error = ErrorMapper.fromStatus(
                host = instance.host,
                url = instance.profileUrlFor(handle),
                code = page.status,
                retryAfterSeconds = page.retryAfterSeconds,
                bodyHint = page.body,
                handle = handle
            ) ?: verifyTimeline(instance, page.body, handle)
            ProbeResult(
                instanceId = instance.id,
                latencyMillis = elapsedMillis(started),
                httpStatus = page.status,
                error = error,
                viaBrowser = page.via == ChallengeGateway.Via.WEBVIEW
            )
        } catch (t: Throwable) {
            if (t is kotlinx.coroutines.CancellationException) throw t
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
    private fun verifyTimeline(instance: NitterInstance, body: String, handle: String): AppError? =
        if (parser.parse(body, handle, instance.host) != null) {
            null
        } else {
            AppError.ParseFailure(
                host = instance.host,
                selectorSetVersion = HtmlTimelineParser.SELECTOR_SET_VERSION,
                snippet = summarise(body)
            )
        }

    /**
     * A readable summary of what arrived instead of a timeline. Tags are
     * stripped and whitespace collapsed, because a raw 200 characters of HTML
     * is usually just the doctype and tells nobody anything.
     */
    private fun summarise(body: String): String {
        val text = StringBuilder()
        var inTag = false
        for (c in body) {
            when {
                c == '<' -> inTag = true
                c == '>' -> inTag = false
                !inTag -> text.append(c)
            }
            if (text.length > 4_000) break
        }
        return text.toString()
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(180)
            .ifBlank { "empty response body" }
    }

    private fun elapsedMillis(startedNanos: Long): Long =
        (System.nanoTime() - startedNanos) / 1_000_000

    companion object {
        /** Used only when you follow nobody yet. */
        private val FALLBACK_HANDLES = listOf("nytimes", "bbcbreaking", "reuters")

        private const val PROBE_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/124.0.0.0 Mobile Safari/537.36"
    }
}
