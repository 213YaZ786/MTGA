package com.mtga.app.core.model

import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * Paging past the end of a source through Nitter search.
 *
 * A profile page stops somewhere (X serves a timeline only so deep, and RSS
 * has no pages at all), but X search takes `from:<account> max_id:<id>`:
 * the account's posts up to that id, older ones included. Post ids are
 * snowflakes, their time in the high bits, so "older than the last post
 * read" is "max_id one below its id". When a search page ends, the next
 * search starts below the oldest post it gave.
 *
 * Held in the feed's cursor like any other, marked so it is never handed to
 * a profile page: "search|<max id>|<Nitter's cursor within that search>".
 */
object SearchCursor {

    private const val PREFIX = "search|"

    data class Position(val maxId: Long, val cursor: String?)

    /** Where search continues below [posts]: under their oldest own post. */
    fun below(posts: List<Post>): String? =
        posts.asSequence()
            .filter { !it.isPinned && it.kind != PostKind.REPOST }
            .mapNotNull { it.id.toLongOrNull() }
            .minOrNull()
            ?.takeIf { it > 1 }
            ?.let { "$PREFIX${it - 1}|" }

    /** The next page of the search at [maxId], from Nitter's own cursor. */
    fun within(maxId: Long, cursor: String): String = "$PREFIX$maxId|${plain(cursor)}"

    fun parse(cursor: String?): Position? {
        if (cursor == null || !cursor.startsWith(PREFIX)) return null
        val rest = cursor.removePrefix(PREFIX)
        val maxId = rest.substringBefore('|').toLongOrNull() ?: return null
        return Position(maxId, rest.substringAfter('|', "").takeIf { it.isNotEmpty() })
    }

    fun query(handle: String, maxId: Long): String = "from:$handle max_id:$maxId"

    /**
     * A search page may give its cursor already encoded for a URL; it is
     * kept plain, and encoded once when the next page is asked for.
     */
    private fun plain(cursor: String): String =
        if ('%' in cursor) runCatching { URLDecoder.decode(cursor, StandardCharsets.UTF_8.name()) }.getOrDefault(cursor) else cursor
}
