package com.mtga.app.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SearchCursorTest {

    private fun post(id: String, kind: PostKind = PostKind.ORIGINAL, pinned: Boolean = false) = Post(
        id = id, authorHandle = "clashreport", authorName = "Clash Report", text = "",
        publishedAtMillis = 0, permalink = "", kind = kind, isPinned = pinned
    )

    @Test
    fun `search starts one below the oldest own post, pins and reposts aside`() {
        val posts = listOf(
            post("2104880881278288186"),
            post("2104878128711512142"),
            post("2104877236268474374"),
            // A pinned post and a repost carry older ids that are not where
            // the account's own timeline stopped.
            post("1900000000000000000", pinned = true),
            post("1800000000000000000", kind = PostKind.REPOST)
        )
        val cursor = SearchCursor.below(posts)
        assertEquals("search|2104877236268474373|", cursor)
        assertEquals(SearchCursor.Position(2104877236268474373, null), SearchCursor.parse(cursor))
        assertEquals("from:clashreport max_id:2104877236268474373", SearchCursor.query("clashreport", 2104877236268474373))
    }

    @Test
    fun `a search page's own cursor is kept plain and read back`() {
        val cursor = SearchCursor.within(42, "DAACCgACGx%2Bs%3D")
        assertEquals(SearchCursor.Position(42, "DAACCgACGx+s="), SearchCursor.parse(cursor))
        assertEquals(SearchCursor.Position(42, "scroll:abc+/="), SearchCursor.parse(SearchCursor.within(42, "scroll:abc+/=")))
    }

    @Test
    fun `a profile cursor or no own post is not a search`() {
        assertNull(SearchCursor.parse("DAABCgABF__"))
        assertNull(SearchCursor.parse(null))
        assertNull(SearchCursor.below(emptyList()))
        assertNull(SearchCursor.below(listOf(post("/user/status/abc"))))
    }
}
