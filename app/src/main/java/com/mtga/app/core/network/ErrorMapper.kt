package com.mtga.app.core.network

import com.mtga.app.core.common.AppError
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.statement.HttpResponse
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * Turns transport exceptions and HTTP statuses into the named cases in
 * AppError. This is the only place in MTGA that is allowed to look at raw
 * exceptions, everything above it deals in Outcome and AppError.
 */
object ErrorMapper {

    fun fromThrowable(host: String, t: Throwable): AppError = when (t) {
        is UnknownHostException -> AppError.DnsFailure(host)
        is SSLException -> AppError.TlsFailure(host, t.message)
        is HttpRequestTimeoutException -> AppError.Timeout(host, HttpClientFactory.REQUEST_TIMEOUT_MS)
        is SocketTimeoutException -> AppError.Timeout(host, HttpClientFactory.REQUEST_TIMEOUT_MS)
        is ConnectException -> AppError.InstanceError(host, 0)
        else -> AppError.Unknown("${t::class.java.simpleName}: ${t.message}")
    }

    /**
     * Returns null when the response is genuinely usable. A 200 that carries a
     * bot challenge is not usable, so [bodyHint] lets the caller pass the first
     * chunk of the body for inspection.
     */
    fun fromResponse(
        host: String,
        response: HttpResponse,
        bodyHint: String? = null,
        handle: String? = null
    ): AppError? {
        val code = response.status.value

        if (code == 429) {
            val retryAfter = response.headers["Retry-After"]?.toLongOrNull()
            return AppError.RateLimited(host, retryAfter)
        }
        if (code == 403 || code == 401 || code == 406) return AppError.ClientRefused(host, code)
        if (code == 404 || code == 410) return AppError.AccountNotFound(handle ?: host)
        if (code >= 500) return AppError.InstanceError(host, code)
        if (code >= 400) return AppError.ClientRefused(host, code)

        if (bodyHint != null && looksLikeChallenge(bodyHint)) {
            return AppError.ClientRefused(host, code)
        }
        return null
    }

    /**
     * Cheap heuristics for interstitials that answer 200 while serving no
     * content. Kept narrow, a false positive here takes a healthy instance out
     * of the pool.
     */
    private fun looksLikeChallenge(body: String): Boolean {
        val lower = body.take(4_000).lowercase()
        return "just a moment" in lower ||
            "cf-browser-verification" in lower ||
            "checking your browser" in lower ||
            "enable javascript and cookies to continue" in lower ||
            "attention required" in lower
    }
}
