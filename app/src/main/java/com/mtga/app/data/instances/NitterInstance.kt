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
         * Seed pool, taken from the Nitter project's own instance table and
         * limited to ones it lists as both online and working.
         *
         * Order is the failover order. More instances is the cheapest
         * redundancy available: each one has its own rate limit, its own
         * upstream accounts, and its own reasons for failing.
         *
         * The Nitter wiki asks that these not be used for scraping. MTGA reads
         * one profile when a person opens it, paced by HostThrottle, which is
         * browsing, not scraping.
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
                id = "nitter-privacyredirect",
                label = "nitter.privacyredirect.com",
                baseUrl = "https://nitter.privacyredirect.com",
                builtIn = true
            ),
            NitterInstance(
                id = "nitter-tiekoetter",
                label = "nitter.tiekoetter.com",
                baseUrl = "https://nitter.tiekoetter.com",
                builtIn = true
            ),
            NitterInstance(
                id = "nitter-catsarch",
                label = "nitter.catsarch.com",
                baseUrl = "https://nitter.catsarch.com",
                builtIn = true
            ),
            NitterInstance(
                id = "nitter-kareem",
                label = "nitter.kareem.one",
                baseUrl = "https://nitter.kareem.one",
                builtIn = true
            ),
            NitterInstance(
                id = "nuku-trabun",
                label = "nuku.trabun.org",
                baseUrl = "https://nuku.trabun.org",
                builtIn = true
            ),
            NitterInstance(
                id = "lightbrd",
                label = "lightbrd.com",
                baseUrl = "https://lightbrd.com",
                builtIn = true
            ),
            NitterInstance(
                id = "nitter-poast",
                label = "nitter.poast.org",
                baseUrl = "https://nitter.poast.org",
                builtIn = true
            ),
            // Carries ads. Last resort, and disabled until you choose it.
            NitterInstance(
                id = "nitter-space",
                label = "nitter.space",
                baseUrl = "https://nitter.space",
                enabled = false,
                builtIn = true
            ),
            NitterInstance(
                id = "nitter-net",
                label = "nitter.net",
                baseUrl = "https://nitter.net",
                enabled = false,
                builtIn = true
            )
        )

    }
}
