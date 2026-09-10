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
         * Seed pool. Since September 2026 only xcancel is enabled by default.
         *
         * Every other instance sat behind the same WAF and never returned a
         * post to MTGA, while xcancel runs its own check that a real browser
         * engine passes. The others stay listed but disabled, so recovering
         * one is a single tap in Diagnostics rather than an app update.
         *
         * The Nitter wiki asks that these not be used for scraping. MTGA reads
         * one profile when a person opens it, paced by HostThrottle, which is
         * browsing, not scraping.
         */
        /**
         * Bumped when the default enabled set changes for a reason that must
         * override earlier choices. Revision 2: only xcancel stays enabled.
         */
        const val POOL_REVISION = 2

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
                enabled = false,
                builtIn = true
            ),
            NitterInstance(
                id = "nitter-tiekoetter",
                label = "nitter.tiekoetter.com",
                baseUrl = "https://nitter.tiekoetter.com",
                enabled = false,
                builtIn = true
            ),
            NitterInstance(
                id = "nitter-catsarch",
                label = "nitter.catsarch.com",
                baseUrl = "https://nitter.catsarch.com",
                enabled = false,
                builtIn = true
            ),
            NitterInstance(
                id = "nitter-kareem",
                label = "nitter.kareem.one",
                baseUrl = "https://nitter.kareem.one",
                enabled = false,
                builtIn = true
            ),
            NitterInstance(
                id = "nuku-trabun",
                label = "nuku.trabun.org",
                baseUrl = "https://nuku.trabun.org",
                enabled = false,
                builtIn = true
            ),
            NitterInstance(
                id = "lightbrd",
                label = "lightbrd.com",
                baseUrl = "https://lightbrd.com",
                enabled = false,
                builtIn = true
            ),
            NitterInstance(
                id = "nitter-poast",
                label = "nitter.poast.org",
                baseUrl = "https://nitter.poast.org",
                enabled = false,
                builtIn = true
            ),
            // Carries ads.
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
