package com.mtga.app.data.repository

import com.mtga.app.core.model.ArchiveCursor
import com.mtga.app.core.model.FxCursor
import com.mtga.app.core.model.Post
import com.mtga.app.core.model.PostKind
import com.mtga.app.core.model.SearchCursor
import com.mtga.app.data.settings.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SourcesTest {

    private val all = Sources.of(Settings())
    private val stored = listOf(post(2105000000000000000), post(2104000000000000000), post(2090000000000000000, pinned = true))

    @Test
    fun `every source is on by default`() {
        assertEquals(Sources(xcom = true, fxtwitter = true, nitter = true, archive = true, duckDuckGo = true), all)
        assertFalse(all.none)
    }

    @Test
    fun `paging below a post goes through the archives, else Nitter search, else nowhere`() {
        assertEquals(ArchiveCursor.of(2104000000000000000), all.below(stored))
        assertEquals(ArchiveCursor.of(2104000000000000000), all.copy(archive = false).below(stored))
        assertEquals(SearchCursor.belowId(2104000000000000000), all.copy(archive = false, duckDuckGo = false).below(stored))
        assertNull(Sources(xcom = true, fxtwitter = true, nitter = false, archive = false, duckDuckGo = false).below(stored))
    }

    @Test
    fun `each cursor follows its own source only while it is on`() {
        val fx = FxCursor.of("DAAHCg")
        assertTrue(all.follows(fx))
        assertFalse(all.copy(fxtwitter = false).follows(fx))
        assertFalse(all.copy(archive = false, duckDuckGo = false).follows(ArchiveCursor.of(5)))
        assertTrue(all.copy(archive = false).follows(ArchiveCursor.of(5)))
        assertFalse(all.copy(nitter = false).follows("DAABCgABF"))
    }

    @Test
    fun `a cursor of a source switched off becomes the same place for another`() {
        val noFx = all.copy(fxtwitter = false)
        assertEquals(ArchiveCursor.of(2104000000000000000), noFx.translate(FxCursor.of("DAAHCg"), stored))

        val noIds = all.copy(archive = false, duckDuckGo = false)
        assertEquals(SearchCursor.belowId(2104000000000000000), noIds.translate(ArchiveCursor.of(2104000000000000000), stored))

        val noNitter = all.copy(nitter = false)
        assertEquals(ArchiveCursor.of(2104000000000000000), noNitter.translate(SearchCursor.belowId(2104000000000000000)!!, stored))

        val nothingDeeper = Sources(xcom = true, fxtwitter = false, nitter = false, archive = false, duckDuckGo = false)
        assertNull(nothingDeeper.translate(FxCursor.of("DAAHCg"), stored))
    }

    @Test
    fun `nothing on at all is said`() {
        assertTrue(Sources(xcom = false, fxtwitter = false, nitter = false, archive = false, duckDuckGo = false).none)
        assertFalse(Sources(xcom = false, fxtwitter = false, nitter = false, archive = false, duckDuckGo = true).none)
    }

    @Test
    fun `fx cursors read back and are told apart`() {
        assertEquals("DAAHCg", FxCursor.parse(FxCursor.of("DAAHCg")))
        assertNull(FxCursor.parse("DAAHCg"))
        assertNull(ArchiveCursor.parse(FxCursor.of("1")))
        assertEquals("FxTwitter, switched off", all.copy(fxtwitter = false).via(FxCursor.of("x")))
    }

    private fun post(id: Long, pinned: Boolean = false) = Post(
        id = id.toString(), authorHandle = "a", authorName = "a", text = "", publishedAtMillis = 0,
        permalink = "", kind = PostKind.ORIGINAL, isPinned = pinned
    )
}
