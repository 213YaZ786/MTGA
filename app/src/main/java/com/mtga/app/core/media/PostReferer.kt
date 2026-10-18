package com.mtga.app.core.media

import com.mtga.app.core.link.LinkCleaner
import com.mtga.app.core.link.SafeLinks
import java.net.URI

/**
 * The clean address of the post a save came from, the one [SaveOrigin] writes
 * into the file's media store row (REFERER_URI, as Chrome's downloads do).
 *
 * Only a clean https address on X's own hosts (those of the manifest's VIEW
 * intent filter) is kept: the permalink comes from a post and may name any
 * server. Pure, no Android.
 */
object PostReferer {
    private val OWN_HOSTS = setOf(
        "x.com", "www.x.com", "mobile.x.com",
        "twitter.com", "www.twitter.com", "mobile.twitter.com", "m.twitter.com"
    )

    /** A permalink longer than this is not a post address: none kept. */
    internal const val MAX_LENGTH = 2048

    // Taken from this app's XLink.parse (core/link/XLink.kt, the status path
    // /<handle>/status/<id> at L34-46, isHandle and isId): a handle of 1 to 15
    // letters, digits and underscores, an id of digits. X's own address form,
    // https://x.com/<handle>/status/<id>, is the one kept.
    private val HANDLE = Regex("[A-Za-z0-9_]{1,15}")
    private val ID = Regex("[0-9]{1,20}")

    /**
     * The address of a save's post: its own permalink when that is on X's
     * hosts, else (Nitter, twstalker) the canonical x.com address built from
     * the post's [handle] and [id], null when either is not of X's form.
     * Same source as above: XLink.parse's status path (core/link/XLink.kt).
     */
    fun forPost(permalink: String?, handle: String?, id: String?): String? =
        forPermalink(permalink) ?: canonical(handle, id)

    // Same source as forPost: XLink.parse's status path (core/link/XLink.kt L34-46).
    private fun canonical(handle: String?, id: String?): String? =
        if (handle != null && id != null && HANDLE.matches(handle) && ID.matches(id)) {
            "https://x.com/$handle/status/$id"
        } else {
            null
        }

    /** The Referer to send for a save of the post at [permalink], or null for none. */
    fun forPermalink(permalink: String?): String? {
        val url = SafeLinks.openable(permalink) ?: return null
        if (url.length > MAX_LENGTH || !url.startsWith("https://")) return null
        // Tracking parameters off, so only the clean post address is kept. The
        // cleaned address is the one checked: a redirect wrapper is unwrapped
        // by the cleaner and its target must be ours too.
        val clean = SafeLinks.openable(LinkCleaner.clean(url).substringBefore('#')) ?: return null
        if (clean.length > MAX_LENGTH || !clean.startsWith("https://")) return null
        val uri = runCatching { URI(clean) }.getOrNull() ?: return null
        if (uri.userInfo != null) return null
        val host = uri.host?.lowercase() ?: return null
        return clean.takeIf { host in OWN_HOSTS }
    }
}
