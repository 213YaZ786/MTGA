package com.mtga.app.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// Test protocol: JUnit 4 (https://github.com/junit-team/junit4). The marker is
// FxEmbed's note tweet test
// (https://github.com/FxEmbed/FxEmbed/blob/main/packages/atmosphere/src/providers/twitter/processor.ts).
/** The cut marker of a post and how a fuller text clears it. */
class PostTextCutTest {

    @Test
    fun `a fuller text from a whole source clears the marker`() {
        val stored = post("cut head of a long post", cut = true)
        val fresh = post("cut head of a long post and the rest of it", cut = false)

        val merged = stored.mergedWith(fresh)

        assertEquals(fresh.text, merged.text)
        assertFalse(merged.textCut)
    }

    @Test
    fun `a fuller text that is itself cut keeps the marker`() {
        val merged = post("short", cut = false).mergedWith(post("short but a bit longer", cut = true))

        assertTrue(merged.textCut)
    }

    @Test
    fun `an equal text from a whole source settles a cut post`() {
        val merged = post("same text", cut = true).mergedWith(post("same text", cut = false))

        assertFalse(merged.textCut)
    }

    @Test
    fun `an equal text from another cut copy keeps the marker`() {
        val merged = post("same text", cut = true).mergedWith(post("same text", cut = true))

        assertTrue(merged.textCut)
    }

    @Test
    fun `a whole stored post is not marked by a cut copy`() {
        val merged = post("the whole text of the post", cut = false).mergedWith(post("the whole text", cut = true))

        assertEquals("the whole text of the post", merged.text)
        assertFalse(merged.textCut)
    }

    @Test
    fun `a quoted post follows the same rule`() {
        val stored = QuotedPost("a", "A", "cut", "https://x.com/a/status/9", textCut = true)
        val merged = stored.mergedWith(QuotedPost("a", "A", "cut and more", "https://x.com/a/status/9"))

        assertEquals("cut and more", merged.text)
        assertFalse(merged.textCut)
        assertEquals("9", merged.quotedId)
    }

    // Source: the idea of Nitter's Redis cache, which keeps what it settled across
    // restarts (https://github.com/zedeus/nitter/blob/master/src/redis_cache.nim),
    // tested with JUnit 4 (https://github.com/junit-team/junit4).
    @Test
    fun `a checked post stays checked when the same text comes again`() {
        val stored = post("same text", cut = false).copy(textChecked = true)

        assertTrue(stored.mergedWith(post("same text", cut = false)).textChecked)
        assertTrue(stored.mergedWith(post("shorter", cut = false)).textChecked)
    }

    // Source: as above, JUnit 4 (https://github.com/junit-team/junit4).
    @Test
    fun `a fuller text from another source asks again`() {
        val stored = post("same text", cut = false).copy(textChecked = true)

        val merged = stored.mergedWith(post("same text and a lot more", cut = false))

        assertEquals("same text and a lot more", merged.text)
        assertFalse(merged.textChecked)
    }

    // Source: as above, JUnit 4 (https://github.com/junit-team/junit4).
    @Test
    fun `a quoted post keeps its checked mark the same way`() {
        val stored = QuotedPost("a", "A", "text", "https://x.com/a/status/9", textChecked = true)

        assertTrue(stored.mergedWith(QuotedPost("a", "A", "text", "https://x.com/a/status/9")).textChecked)
        assertFalse(stored.mergedWith(QuotedPost("a", "A", "text and more", "https://x.com/a/status/9")).textChecked)
    }

    @Test
    fun `the length window is the fallback, in code points and without a trailing link`() {
        val body = "a".repeat(260)

        assertTrue(looksCut(body, marked = false))
        assertTrue(looksCut("$body https://t.co/AbCd1234", marked = false))
        assertFalse(looksCut("a".repeat(120), marked = false))
        // 150 emoji are 300 UTF-16 units but 150 code points: under the window.
        assertFalse(looksCut("😀".repeat(150), marked = false))
        assertTrue(looksCut("short", marked = true))
    }

    @Test
    fun `files stored before the marker still load`() {
        val old = """{"id":"1","authorHandle":"h","authorName":"H","text":"t","publishedAtMillis":1,"permalink":"p"}"""

        val post = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }.decodeFromString<Post>(old)

        assertFalse(post.textCut)
        assertFalse(post.textChecked)
    }

    private fun post(text: String, cut: Boolean) = Post(
        id = "1", authorHandle = "h", authorName = "H", text = text, publishedAtMillis = 1,
        permalink = "https://x.com/h/status/1", textCut = cut
    )
}
