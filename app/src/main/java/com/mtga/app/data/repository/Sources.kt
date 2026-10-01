package com.mtga.app.data.repository

import com.mtga.app.core.model.ArchiveCursor
import com.mtga.app.core.model.FxCursor
import com.mtga.app.core.model.Post
import com.mtga.app.core.model.SearchCursor
import com.mtga.app.data.settings.Settings

/**
 * The sources the reader allows and how they follow each other. Pure, tested.
 *
 * - x.com gives the newest few posts, beside everything else.
 * - A page source gives the timeline page after page: FxTwitter first (no
 *   bot check, whole posts), Nitter when FxTwitter fails or is off.
 * - Where a page source ends, or fails mid scroll, paging goes on below the
 *   oldest post through post ids: the archives' lists (Internet Archive,
 *   DuckDuckGo) when on, else Nitter search.
 *
 * A cursor belongs to one source. One left by a source since switched off
 * is never handed to another: it becomes a position below a post id, which
 * every id source understands.
 */
data class Sources(
    val xcom: Boolean,
    val fxtwitter: Boolean,
    val nitter: Boolean,
    val archive: Boolean,
    val duckDuckGo: Boolean
) {
    /** Any list of post ids: the archives' paging and gap filling. */
    val idLists: Boolean get() = archive || duckDuckGo

    val none: Boolean get() = !xcom && !fxtwitter && !nitter && !idLists

    /** Where paging continues below [posts], or null when no id source is on. */
    fun below(posts: List<Post>): String? = when {
        idLists -> ArchiveCursor.below(posts)
        nitter -> SearchCursor.below(posts)
        else -> null
    }

    /** Below the post [id], the same way as [below]. */
    fun belowId(id: Long): String? = when {
        idLists -> ArchiveCursor.of(id)
        nitter -> SearchCursor.belowId(id)
        else -> null
    }

    /** Whether the source that wrote [cursor] is still on. */
    fun follows(cursor: String): Boolean = when {
        FxCursor.parse(cursor) != null -> fxtwitter
        ArchiveCursor.parse(cursor) != null -> idLists
        SearchCursor.parse(cursor) != null -> nitter
        else -> nitter
    }

    /**
     * The same place for a source still on: an id cursor keeps its own
     * position, a page cursor (FxTwitter, Nitter) gives way to below the
     * oldest post stored, [stored]. Null when nothing can go further.
     */
    fun translate(cursor: String, stored: List<Post>): String? {
        ArchiveCursor.parse(cursor)?.let { return if (idLists) cursor else belowId(it) }
        SearchCursor.parse(cursor)?.let { return if (nitter) cursor else belowId(it.maxId + 1) }
        return below(stored)
    }

    /** Which source a cursor asks, for the log. */
    fun via(cursor: String): String = when {
        FxCursor.parse(cursor) != null -> "FxTwitter"
        ArchiveCursor.parse(cursor) != null -> "the archives"
        SearchCursor.parse(cursor) != null -> "Nitter search"
        else -> "a Nitter page"
    } + if (follows(cursor)) "" else ", switched off"

    companion object {
        fun of(settings: Settings) = Sources(
            xcom = settings.useXcomDirect,
            fxtwitter = settings.useFxTwitter,
            nitter = settings.useNitter,
            archive = settings.olderFromArchives,
            duckDuckGo = settings.useDuckDuckGo
        )
    }
}
