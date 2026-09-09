package com.mtga.app.data.instances

import kotlinx.serialization.Serializable

/**
 * A Nitter front end MTGA can talk to.
 *
 * [rssBaseUrl] is separate because some instances serve feeds from a different
 * host. xcancel is the notable case, its feeds live on rss.xcancel.com.
 */
@Serializable
data class NitterInstance(
    val id: String,
    val label: String,
    val baseUrl: String,
    val rssBaseUrl: String? = null,
    val enabled: Boolean = true,
    val builtIn: Boolean = false
) {
    val host: String get() = baseUrl.substringAfter("://").substringBefore("/")

    fun rssUrlFor(handle: String): String =
        "${(rssBaseUrl ?: baseUrl).trimEnd('/')}/$handle/rss"

    fun profileUrlFor(handle: String): String = "${baseUrl.trimEnd('/')}/$handle"

    companion object {
        /**
         * Seed pool. Order is the failover order. xcancel first because it is
         * the most actively maintained public instance, but the whole point of
         * the pool is that no single one of these is assumed to survive.
         */
        val defaults: List<NitterInstance> = listOf(
            NitterInstance(
                id = "xcancel",
                label = "xcancel.com",
                baseUrl = "https://xcancel.com",
                rssBaseUrl = "https://rss.xcancel.com",
                builtIn = true
            ),
            NitterInstance(
                id = "nitter-net",
                label = "nitter.net",
                baseUrl = "https://nitter.net",
                builtIn = true
            ),
            NitterInstance(
                id = "nitter-poast",
                label = "nitter.poast.org",
                baseUrl = "https://nitter.poast.org",
                builtIn = true
            ),
            NitterInstance(
                id = "nitter-privacydev",
                label = "nitter.privacydev.net",
                baseUrl = "https://nitter.privacydev.net",
                builtIn = true
            )
        )
    }
}
