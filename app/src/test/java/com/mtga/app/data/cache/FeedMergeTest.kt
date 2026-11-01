package com.mtga.app.data.cache

import com.mtga.app.core.model.Feed
import com.mtga.app.core.model.Post
import com.mtga.app.core.model.QuotedPost
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Test protocol: JUnit 4 (https://github.com/junit-team/junit4). The pin cases
// follow how Nitter treats the TimelinePinEntry of a first page
// (https://github.com/zedeus/nitter/blob/master/src/parser.nim); the whole text
// case follows FxEmbed's note tweet rule
// (https://github.com/FxEmbed/FxEmbed/blob/main/packages/atmosphere/src/providers/twitter/processor.ts).
/** The pin rules and the whole texts of the cache's merge, see [FeedMerge]. */
class FeedMergeTest {

    @Test
    fun `x com names the pin and a stored post becomes pinned`() {
        // FxTwitter served the first page: the pinned post was stored without its flag.
        val stored = feed(post("1", 100), post("2", 90), post("3", 80))
        val head = feed(post("1", 100), post("2", 90, pinned = true), pinNamed = true)

        val merged = FeedMerge.combine(stored, head, isPagedFetch = false).feed

        assertEquals(listOf("2"), merged.posts.filter { it.isPinned }.map { it.id })
        assertEquals("2", merged.posts.first().id)
    }

    @Test
    fun `a FxTwitter refresh does not clear the stored pin`() {
        val stored = feed(post("1", 100), post("2", 90, pinned = true), post("3", 80))
        val fxPage = feed(post("1", 100), post("2", 90), post("3", 80))

        val merged = FeedMerge.combine(stored, fxPage, isPagedFetch = false).feed

        assertEquals(listOf("2"), merged.posts.filter { it.isPinned }.map { it.id })
    }

    @Test
    fun `a page without a pin entry says nothing about pins`() {
        val stored = feed(post("1", 100), post("2", 90, pinned = true))
        val head = feed(post("1", 100), post("2", 90), pinNamed = false)

        val merged = FeedMerge.combine(stored, head, isPagedFetch = false).feed

        assertTrue(merged.posts.single { it.id == "2" }.isPinned)
    }

    @Test
    fun `x com naming another post moves the pin`() {
        // The old pin is not even on the head, it is old: it is still unpinned.
        val stored = feed(post("1", 100), post("5", 10, pinned = true))
        val head = feed(post("1", 100, pinned = true), post("9", 120), pinNamed = true)

        val merged = FeedMerge.combine(stored, head, isPagedFetch = false).feed

        assertEquals(listOf("1"), merged.posts.filter { it.isPinned }.map { it.id })
        assertFalse(merged.posts.single { it.id == "5" }.isPinned)
        assertEquals(listOf("1", "9", "5"), merged.posts.map { it.id })
    }

    @Test
    fun `a new pinned post arriving with older stored posts keeps its flag`() {
        val stored = feed(post("1", 100), post("2", 90))
        val head = feed(post("7", 20, pinned = true), post("1", 100), pinNamed = true)

        val merged = FeedMerge.combine(stored, head, isPagedFetch = false).feed

        assertTrue(merged.posts.single { it.id == "7" }.isPinned)
        assertEquals("7", merged.posts.first().id)
    }

    @Test
    fun `a paged fetch never states pins`() {
        val stored = feed(post("1", 100, pinned = true), post("2", 90))
        val page = feed(post("2", 90, pinned = true), post("0", 50), pinNamed = true)

        val merged = FeedMerge.combine(stored, page, isPagedFetch = true).feed

        assertEquals(listOf("1"), merged.posts.filter { it.isPinned }.map { it.id })
    }

    @Test
    fun `a Nitter first page keeps its authority over a post it shows again`() {
        val stored = feed(post("1", 100), post("2", 90, pinned = true))
        val nitter = feed(post("1", 100, pinned = true), post("2", 90), pinAware = true)

        val merged = FeedMerge.combine(stored, nitter, isPagedFetch = false)

        assertTrue(merged.statesPins)
        assertEquals(listOf("1"), merged.feed.posts.filter { it.isPinned }.map { it.id })
    }

    @Test
    fun `whole texts replace stored ones and clear the cut marker`() {
        val quoted = QuotedPost("a", "A", "cut quote", "https://x.com/a/status/55", textCut = true)
        val stored = feed(
            post("1", 100, text = "cut", cut = true).copy(quoted = quoted),
            post("2", 90, text = "other")
        )

        val done = FeedMerge.withWholeTexts(stored, mapOf("1" to "the whole text"), mapOf("1" to "the whole quote"))

        val first = done.posts.single { it.id == "1" }
        assertEquals("the whole text", first.text)
        assertFalse(first.textCut)
        assertEquals("the whole quote", first.quoted?.text)
        assertFalse(first.quoted!!.textCut)
        assertEquals("other", done.posts.single { it.id == "2" }.text)
    }

    // Source: the idea of Nitter's Redis cache, which keeps what it settled across
    // restarts (https://github.com/zedeus/nitter/blob/master/src/redis_cache.nim),
    // tested with JUnit 4 (https://github.com/junit-team/junit4).
    @Test
    fun `checked ids are marked on the post and on the quote that points to them`() {
        val quote = QuotedPost("a", "A", "q", "https://x.com/a/status/77")
        val stored = feed(
            post("1", 100, text = "unmarked"),
            post("2", 90).copy(quoted = quote),
            post("3", 80)
        )

        val done = FeedMerge.withWholeTexts(stored, emptyMap(), emptyMap(), setOf("1", "77"))

        assertTrue(done.posts.single { it.id == "1" }.textChecked)
        assertTrue(done.posts.single { it.id == "2" }.quoted!!.textChecked)
        assertFalse(done.posts.single { it.id == "2" }.textChecked)
        assertFalse(done.posts.single { it.id == "3" }.textChecked)
    }

    @Test
    fun `no stored post gone leaves the quoted post alone when there is none`() {
        val stored = feed(post("1", 100, text = "cut", cut = true))

        val done = FeedMerge.withWholeTexts(stored, emptyMap(), mapOf("1" to "x"))

        assertNull(done.posts.single().quoted)
        assertTrue(done.posts.single().textCut)
    }

    private fun post(id: String, at: Long, pinned: Boolean = false, text: String = "t$id", cut: Boolean = false) = Post(
        id = id, authorHandle = "h", authorName = "H", text = text, publishedAtMillis = at,
        permalink = "https://x.com/h/status/$id", isPinned = pinned, textCut = cut
    )

    private fun feed(vararg posts: Post, pinAware: Boolean = false, pinNamed: Boolean = false) = Feed(
        handle = "h", displayName = "H", posts = posts.toList(), fetchedFromHost = "x", fetchedAtMillis = 1,
        pinAware = pinAware, pinNamed = pinNamed
    )
}
