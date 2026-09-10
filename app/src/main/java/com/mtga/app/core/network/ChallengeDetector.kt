package com.mtga.app.core.network

import com.mtga.app.core.common.ChallengeKind

/**
 * Recognises a bot check by what the host served, as its own condition rather
 * than as a parse failure.
 *
 * Pure function, no Android, so it can run on a native response and on the
 * HTML a WebView ends up showing, which is how the solver knows it is through.
 *
 * Kept deliberately narrow. A false positive here sends a healthy page through
 * the slow path, so real content always wins over any marker.
 */
object ChallengeDetector {

    fun detect(status: Int, body: String): ChallengeKind? {
        // A page carrying posts is content, whatever else it says. A tweet that
        // happens to quote "not a bot" must not trip the detector.
        if (CONTENT_MARKERS.any { it in body }) return null

        val small = body.length < SMALL_PAGE_CHARS
        // Challenge pages are small and name themselves early. On a large page
        // only the head is inspected, where a title or a script tag would sit.
        val scope = (if (small) body else body.take(HEAD_CHARS)).lowercase()

        return when {
            BLOCK_MARKERS.any { it in scope } -> ChallengeKind.WAF_BLOCK
            POW_MARKERS.any { it in scope } -> ChallengeKind.PROOF_OF_WORK
            JS_MARKERS.any { it in scope } -> ChallengeKind.JS_INTERSTITIAL
            // The four 403 bodies seen in the field were within 200 bytes of
            // each other, one WAF product. A small 403 is that wall.
            status == 403 && small -> ChallengeKind.WAF_BLOCK
            else -> null
        }
    }

    private const val SMALL_PAGE_CHARS = 40_000
    private const val HEAD_CHARS = 4_000

    private val CONTENT_MARKERS = listOf(
        "class=\"timeline-item",
        "class=\"tweet-content",
        "activity-posts",
        "<rss"
    )

    private val BLOCK_MARKERS = listOf(
        "sorry, you have been blocked"
    )

    /** Anubis. Its page title, its asset path, and its challenge payload id. */
    private val POW_MARKERS = listOf(
        "not a bot",
        ".within.website",
        "anubis_challenge"
    )

    private val JS_MARKERS = listOf(
        "verifying your browser",
        "antibot-ref-id",
        "just a moment",
        "checking your browser",
        "cf-browser-verification",
        "challenge-platform",
        "enable javascript and cookies to continue",
        "attention required"
    )
}
