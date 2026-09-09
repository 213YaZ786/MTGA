package com.mtga.app.core.common

/**
 * Every failure the app can surface, named precisely.
 *
 * Design rule: no generic "something went wrong". Each case carries enough
 * context for the Diagnostics screen to explain why MTGA cannot show content,
 * and whether the user, the network, the instance or the app is at fault.
 */
sealed interface AppError {

    /** Who or what is responsible. Drives the tone of the message shown. */
    val blame: Blame

    /** Whether retrying the exact same call could plausibly succeed. */
    val retryable: Boolean

    // ---- device side -------------------------------------------------------

    /** No network transport available at all. */
    data object Offline : AppError {
        override val blame = Blame.DEVICE
        override val retryable = true
    }

    /** Host name did not resolve. Instance gone, or DNS blocked upstream. */
    data class DnsFailure(val host: String) : AppError {
        override val blame = Blame.NETWORK
        override val retryable = true
    }

    /** TLS handshake failed. Possible interception or an expired certificate. */
    data class TlsFailure(val host: String, val detail: String?) : AppError {
        override val blame = Blame.NETWORK
        override val retryable = false
    }

    /** Connected but no answer in time. */
    data class Timeout(val host: String, val millis: Long) : AppError {
        override val blame = Blame.INSTANCE
        override val retryable = true
    }

    // ---- instance side -----------------------------------------------------

    /** The instance actively refused our client, for example a bot challenge. */
    data class ClientRefused(val host: String, val status: Int) : AppError {
        override val blame = Blame.INSTANCE
        override val retryable = false
    }

    /** Rate limited. retryAfterSeconds comes from the Retry-After header. */
    data class RateLimited(val host: String, val retryAfterSeconds: Long?) : AppError {
        override val blame = Blame.INSTANCE
        override val retryable = true
    }

    /** The instance is up but broken, a 5xx. */
    data class InstanceError(val host: String, val status: Int) : AppError {
        override val blame = Blame.INSTANCE
        override val retryable = true
    }

    /** Every instance in the pool has been tried and none answered. */
    data class NoHealthyInstance(val tried: List<String>) : AppError {
        override val blame = Blame.INSTANCE
        override val retryable = true
    }

    // ---- upstream account side ---------------------------------------------

    /** Handle does not exist, was renamed, or the profile is gone. */
    data class AccountNotFound(val handle: String) : AppError {
        override val blame = Blame.UPSTREAM
        override val retryable = false
    }

    /** The account exists but X suspended or protected it. */
    data class AccountUnavailable(val handle: String, val reason: String?) : AppError {
        override val blame = Blame.UPSTREAM
        override val retryable = false
    }

    // ---- our side ----------------------------------------------------------

    /**
     * HTTP 200 arrived but the parser found nothing it recognised. This almost
     * always means the instance changed its markup and MTGA needs an update.
     * This is the case that triggers the WebView fallback offer.
     */
    data class ParseFailure(
        val host: String,
        val selectorSetVersion: Int,
        val snippet: String?
    ) : AppError {
        override val blame = Blame.APP
        override val retryable = false
    }

    /** Local storage failed. */
    data class StorageFailure(val detail: String?) : AppError {
        override val blame = Blame.APP
        override val retryable = true
    }

    /** Genuinely unclassified. Should stay empty in practice. */
    data class Unknown(val detail: String?) : AppError {
        override val blame = Blame.APP
        override val retryable = true
    }
}

enum class Blame { DEVICE, NETWORK, INSTANCE, UPSTREAM, APP }
