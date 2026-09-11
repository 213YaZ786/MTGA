package com.mtga.app.data.instances

import kotlinx.serialization.Serializable

/**
 * A Nitter front end MTGA can talk to.
 *
 * There is no list compiled into the app. Servers come from the Nitter wiki
 * through [InstanceDirectory], or are added by hand.
 *
 * [rssBaseUrl] is separate because some instances serve feeds from a different
 * host. It is only ever set by hand, the wiki does not carry it.
 *
 * The Nitter wiki asks that instances not be used for scraping. MTGA reads one
 * profile when a person opens it, paced by HostThrottle, which is browsing.
 */
@Serializable
data class NitterInstance(
    val id: String,
    val label: String,
    val baseUrl: String,
    val rssBaseUrl: String? = null,
    val enabled: Boolean = true,
    /**
     * True when the entry comes from the public list. The name predates the
     * list and is kept so stored files from 1.3.x still load. A listed server
     * leaves the pool when the list drops it, a hand added one never does.
     */
    val builtIn: Boolean = false,
    /** What the wiki said at the last update. Null for hand added servers. */
    val listedWorking: Boolean? = null
) {
    val host: String get() = baseUrl.substringAfter("://").substringBefore("/")

    fun rssUrlFor(handle: String): String =
        "${(rssBaseUrl ?: baseUrl).trimEnd('/')}/$handle/rss"

    fun profileUrlFor(handle: String): String = "${baseUrl.trimEnd('/')}/$handle"
}
