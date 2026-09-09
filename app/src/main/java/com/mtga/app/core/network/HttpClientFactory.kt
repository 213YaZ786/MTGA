package com.mtga.app.core.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header

/**
 * The single HTTP client for the app.
 *
 * expectSuccess stays false on purpose. A 429 or a 403 is information we want
 * to classify and report, not an exception thrown from deep inside a plugin.
 */
object HttpClientFactory {

    /**
     * Instances that still serve RSS tend to accept only recognisable feed
     * readers, so MTGA identifies itself as one rather than pretending to be a
     * browser. Honest, and it is what actually gets served.
     */
    const val USER_AGENT = "MTGA/0.2 (+https://github.com/213YaZ786/MTGA) FeedReader"

    const val CONNECT_TIMEOUT_MS = 8_000L
    const val REQUEST_TIMEOUT_MS = 15_000L
    const val PROBE_TIMEOUT_MS = 6_000L

    fun create(): HttpClient = HttpClient(OkHttp) {
        expectSuccess = false
        followRedirects = true

        install(HttpTimeout) {
            connectTimeoutMillis = CONNECT_TIMEOUT_MS
            requestTimeoutMillis = REQUEST_TIMEOUT_MS
            socketTimeoutMillis = REQUEST_TIMEOUT_MS
        }

        defaultRequest {
            header("User-Agent", USER_AGENT)
            header("Accept-Language", "en;q=0.9")
        }
    }
}
