package com.mtga.app.core.model

/**
 * Paging past the end of a source through the post ids that web archives
 * know for an account, see ArchiveSource: "archive|<the id to read below>".
 * Marked so it is never handed to a Nitter page.
 */
object ArchiveCursor {

    private const val PREFIX = "archive|"

    /** Below the oldest own post of [posts], pins and reposts aside. */
    fun below(posts: List<Post>): String? =
        posts.asSequence()
            .filter { !it.isPinned && it.kind != PostKind.REPOST }
            .mapNotNull { it.id.toLongOrNull() }
            .minOrNull()
            ?.let { "$PREFIX$it" }

    fun of(id: Long): String = "$PREFIX$id"

    fun parse(cursor: String?): Long? =
        cursor?.takeIf { it.startsWith(PREFIX) }?.removePrefix(PREFIX)?.toLongOrNull()
}
