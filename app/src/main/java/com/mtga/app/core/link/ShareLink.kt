package com.mtga.app.core.link

/**
 * The link to hand to someone else for a post. Pure, so it can be checked
 * outside Android.
 *
 * Numeric ids build a clean URL on x.com, or on [nitterHost] when sharing as
 * Nitter is on. Nitter uses X's own paths, so the same handle and id work
 * there. Anything else falls back to the permalink the source gave, which
 * is the best link MTGA has for it.
 */
object ShareLink {

    fun forPost(handle: String, id: String, permalink: String, nitterHost: String?): String {
        if (id.isEmpty() || !id.all(Char::isDigit) || handle.isBlank()) return permalink
        val host = nitterHost?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: "x.com"
        return "https://$host/$handle/status/$id"
    }
}
