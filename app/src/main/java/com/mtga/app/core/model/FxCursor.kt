package com.mtga.app.core.model

/**
 * A position in an account's timeline as read through FxTwitter, see
 * FxTwitterSource: "fx|<X's own cursor>". Marked so it is never handed to a
 * Nitter page, whose cursors look alike but do not mean the same thing.
 */
object FxCursor {

    private const val PREFIX = "fx|"

    fun of(cursor: String): String = "$PREFIX$cursor"

    fun parse(cursor: String?): String? =
        cursor?.takeIf { it.startsWith(PREFIX) }?.removePrefix(PREFIX)?.takeIf { it.isNotEmpty() }
}
