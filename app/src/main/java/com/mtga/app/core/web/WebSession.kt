package com.mtga.app.core.web

import android.os.LocaleList
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * What MTGA knows, for this run only, about hosts that sit behind a bot check.
 *
 * Deliberately not persisted, like instance health. The cookies themselves live
 * in the WebView cookie store and survive restarts. After a restart the first
 * read of a host is challenged once more, the WebView presents its stored
 * cookie, passes instantly, and the host is marked cleared again. That costs
 * one request and never acts on a stale assumption.
 */
class WebSession {

    /** Hosts where the WebView passed a check. Only these receive cookies. */
    private val cleared: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /** Hosts that still challenged the native client while carrying the cookie. */
    private val nativeRejected: MutableSet<String> = ConcurrentHashMap.newKeySet()

    private val failedAt = ConcurrentHashMap<String, Long>()

    /**
     * The WebView's own User-Agent, captured the first time one is created.
     * Clearance cookies are commonly bound to the User-Agent that earned them,
     * so the native client must present exactly this one to the same host.
     */
    @Volatile
    var userAgent: String? = null
        private set

    /**
     * Built the way Chromium builds it from the device locales, for example
     * "fr-FR,fr;q=0.9,en-US;q=0.8,en;q=0.7". Best effort, and said so: some
     * checks hash this header, and the request log will show if it misses.
     */
    val acceptLanguage: String by lazy { chromiumAcceptLanguage() }

    fun onUserAgent(value: String) {
        if (value.isNotBlank()) userAgent = value
    }

    fun isCleared(host: String): Boolean = host in cleared

    fun markCleared(host: String) {
        cleared += host
        failedAt.remove(host)
    }

    fun markNativeRejected(host: String) {
        nativeRejected += host
    }

    /** True once the cookie alone proved not enough for the native client. */
    fun prefersWebView(host: String): Boolean = host in nativeRejected

    fun recordFailure(host: String) {
        failedAt[host] = System.currentTimeMillis()
    }

    /**
     * A host whose check could not be passed in the background is not retried
     * automatically for a while. Otherwise every read would burn twenty seconds
     * on the same wall. The user can still complete it by hand at any time.
     */
    fun mayAutoSolve(host: String): Boolean {
        val last = failedAt[host] ?: return true
        return System.currentTimeMillis() - last > AUTO_RETRY_AFTER_MS
    }

    private fun chromiumAcceptLanguage(): String {
        val tags = mutableListOf<String>()
        val locales = LocaleList.getAdjustedDefault()
        for (i in 0 until locales.size()) {
            val locale = locales[i] ?: continue
            val full = locale.toLanguageTag()
            if (full.isNotBlank() && full != "und" && full !in tags) tags += full
            val base = locale.language
            if (base.isNotBlank() && base !in tags) tags += base
        }
        if (tags.isEmpty()) return "en-US,en;q=0.9"
        return tags.mapIndexed { index, tag ->
            if (index == 0) {
                tag
            } else {
                val q = (10 - index).coerceAtLeast(1) / 10.0
                tag + ";q=" + String.format(Locale.US, "%.1f", q)
            }
        }.joinToString(",")
    }

    private companion object {
        const val AUTO_RETRY_AFTER_MS = 10 * 60_000L
    }
}
