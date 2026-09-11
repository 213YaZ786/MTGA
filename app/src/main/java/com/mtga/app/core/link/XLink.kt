package com.mtga.app.core.link

/**
 * What an X link points at, when MTGA can show it. Pure, no Android.
 *
 * Recognises x.com and twitter.com in every variant, plus any extra host the
 * caller trusts to use the same paths, which covers Nitter servers and
 * twstalker. Anything else, a search, a list, a settings page, is left to the
 * browser by returning null.
 */
sealed interface XLink {
    data class Profile(val handle: String) : XLink

    /** [handle] is null for /i/web/status links, which carry only the id. */
    data class Post(val handle: String?, val id: String) : XLink

    companion object {

        fun parse(url: String, extraHosts: Set<String> = emptySet()): XLink? {
            val trimmed = url.trim()
            val scheme = trimmed.substringBefore("://", "").lowercase()
            if (scheme != "https" && scheme != "http") return null

            val rest = trimmed.substringAfter("://")
            val host = rest.substringBefore('/').substringBefore('?').substringBefore('#')
                .substringBefore(':').lowercase()
            if (host !in X_HOSTS && host !in extraHosts) return null

            val segments = rest.substringAfter('/', "")
                .substringBefore('?').substringBefore('#')
                .split('/').filter { it.isNotEmpty() }
            if (segments.isEmpty()) return null

            // /i/web/status/<id> and /i/status/<id>
            if (segments[0].equals("i", ignoreCase = true)) {
                val index = segments.indexOfFirst { it.equals("status", ignoreCase = true) }
                val id = segments.getOrNull(index + 1)
                return if (index > 0 && id != null && id.isId()) Post(null, id) else null
            }

            val first = segments[0].removePrefix("@")
            if (!first.isHandle() || first.lowercase() in RESERVED) return null

            val second = segments.getOrNull(1)?.lowercase()
            if (second == "status" || second == "statuses") {
                val id = segments.getOrNull(2) ?: return null
                return if (id.isId()) Post(first, id) else null
            }
            // /handle, /handle/with_replies, /handle/media and similar tabs.
            return if (second == null || second in PROFILE_TABS) Profile(first) else null
        }

        /** The first http or https URL inside shared text, for the share sheet. */
        fun firstUrlIn(text: String): String? = URL.find(text)?.value?.trimEnd('.', ',', ')', '!', '?')

        private fun String.isHandle() = HANDLE.matches(this)
        private fun String.isId() = length in 1..20 && all(Char::isDigit)

        private val HANDLE = Regex("[A-Za-z0-9_]{1,15}")
        private val URL = Regex("""https?://\S+""")

        private val X_HOSTS = setOf(
            "x.com", "www.x.com", "mobile.x.com",
            "twitter.com", "www.twitter.com", "mobile.twitter.com", "m.twitter.com"
        )

        private val PROFILE_TABS = setOf("with_replies", "media", "highlights", "likes", "search")

        /** First path segments that are X pages, not accounts. */
        private val RESERVED = setOf(
            "home", "explore", "search", "settings", "notifications", "messages",
            "hashtag", "intent", "share", "login", "logout", "signup", "tos",
            "privacy", "compose", "about", "account", "i", "pic", "rss"
        )
    }
}
