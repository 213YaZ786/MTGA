package com.mtga.app.core.common

/**
 * Turns an AppError into something a human reads, plus the action that
 * actually helps. Kept out of the UI so it can be unit tested and localised.
 */
data class ErrorPresentation(
    val headline: String,
    val explanation: String,
    val action: ErrorAction
)

enum class ErrorAction { RETRY, OPEN_DIAGNOSTICS, CHANGE_INSTANCE, OPEN_FALLBACK_VIEWER, NONE }

fun AppError.present(): ErrorPresentation = when (this) {
    AppError.Offline -> ErrorPresentation(
        headline = "You are offline",
        explanation = "MTGA found no network connection. Cached posts are still readable below.",
        action = ErrorAction.RETRY
    )

    is AppError.DnsFailure -> ErrorPresentation(
        headline = "Cannot find $host",
        explanation = "The address did not resolve. The instance may be gone, or your DNS or network is blocking it.",
        action = ErrorAction.CHANGE_INSTANCE
    )

    is AppError.TlsFailure -> ErrorPresentation(
        headline = "Secure connection to $host failed",
        explanation = "The TLS handshake was rejected. Something is intercepting the connection, or the certificate is invalid. MTGA will not fall back to plain HTTP.",
        action = ErrorAction.OPEN_DIAGNOSTICS
    )

    is AppError.Timeout -> ErrorPresentation(
        headline = "$host did not answer",
        explanation = "No response within ${millis / 1000} seconds. The instance is likely overloaded.",
        action = ErrorAction.RETRY
    )

    is AppError.ClientRefused -> ErrorPresentation(
        headline = "$host refused MTGA",
        explanation = "The instance returned $status and blocked the request, usually a bot challenge. Another instance may work.",
        action = ErrorAction.CHANGE_INSTANCE
    )

    is AppError.RateLimited -> ErrorPresentation(
        headline = "Slow down",
        explanation = retryAfterSeconds
            ?.let { "$host is rate limiting MTGA. It asked us to wait $it seconds." }
            ?: "$host is rate limiting MTGA. Backing off automatically.",
        action = ErrorAction.RETRY
    )

    is AppError.InstanceError -> ErrorPresentation(
        headline = "$host is having a bad day",
        explanation = "The instance answered $status. This is a problem on their side, not yours.",
        action = ErrorAction.RETRY
    )

    is AppError.NoHealthyInstance -> ErrorPresentation(
        headline = "No instance is reachable",
        explanation = "MTGA tried ${tried.size} instance(s) and none answered. Nitter instances are under legal pressure from X Corp and can go down without warning. This is not a fault in the app.",
        action = ErrorAction.OPEN_DIAGNOSTICS
    )

    is AppError.AccountNotFound -> ErrorPresentation(
        headline = "@$handle not found",
        explanation = "The handle does not exist on X, or it was renamed.",
        action = ErrorAction.NONE
    )

    is AppError.AccountUnavailable -> ErrorPresentation(
        headline = "@$handle is not viewable",
        explanation = reason ?: "X has suspended or protected this account, so no front end can show it.",
        action = ErrorAction.NONE
    )

    is AppError.ParseFailure -> ErrorPresentation(
        headline = "MTGA could not read this page",
        explanation = "$host answered normally but the layout no longer matches what MTGA expects (selector set v$selectorSetVersion). The app needs an update. You can still view this in the fallback viewer.",
        action = ErrorAction.OPEN_FALLBACK_VIEWER
    )

    is AppError.StorageFailure -> ErrorPresentation(
        headline = "Local storage error",
        explanation = detail ?: "MTGA could not read or write its local database.",
        action = ErrorAction.RETRY
    )

    is AppError.Unknown -> ErrorPresentation(
        headline = "Unexpected error",
        explanation = detail ?: "MTGA hit a case it does not recognise. Please report it.",
        action = ErrorAction.OPEN_DIAGNOSTICS
    )
}
