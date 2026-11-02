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

    // Source: Feeder's fullTextDownloaded flag, kept on the stored item
    // (https://github.com/spacecowboy/Feeder/blob/master/app/src/main/java/com/nononsenseapps/feeder/db/room/FeedItem.kt#L117),
    // tested with JUnit 4 (https://github.com/junit-team/junit4).
    @Test
    fun `a post with its full text downloaded stays so when the same text comes again`() {
        val stored = post("same text", cut = false).copy(fullTextDownloaded = true)

        assertTrue(stored.mergedWith(post("same text", cut = false)).fullTextDownloaded)
        assertTrue(stored.mergedWith(post("shorter", cut = false)).fullTextDownloaded)
    }

    // Source: as above, JUnit 4 (https://github.com/junit-team/junit4).
    @Test
    fun `a fuller text from another source asks again`() {
        val stored = post("same text", cut = false).copy(fullTextDownloaded = true)

        val merged = stored.mergedWith(post("same text and a lot more", cut = false))

        assertEquals("same text and a lot more", merged.text)
        assertFalse(merged.fullTextDownloaded)
    }

    // Source: as above, JUnit 4 (https://github.com/junit-team/junit4).
    @Test
    fun `a quoted post keeps its fullTextDownloaded flag the same way`() {
        val stored = QuotedPost("a", "A", "text", "https://x.com/a/status/9", fullTextDownloaded = true)

        assertTrue(stored.mergedWith(QuotedPost("a", "A", "text", "https://x.com/a/status/9")).fullTextDownloaded)
        assertFalse(stored.mergedWith(QuotedPost("a", "A", "text and more", "https://x.com/a/status/9")).fullTextDownloaded)
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
        assertFalse(post.fullTextDownloaded)
    }

    // Source: kotlinx.serialization's JsonNames (https://github.com/Kotlin/kotlinx.serialization/blob/master/docs/json.md#alternative-json-names),
    // tested with JUnit 4 (https://github.com/junit-team/junit4).
    @Test
    fun `files written with the old textChecked key still read as downloaded`() {
        val old = """{"id":"1","authorHandle":"h","authorName":"H","text":"t","publishedAtMillis":1,"permalink":"p","textChecked":true,""" +
            """"quoted":{"handle":"a","name":"A","text":"q","permalink":"https://x.com/a/status/9","textChecked":true}}"""

        val post = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }.decodeFromString<Post>(old)

        assertTrue(post.fullTextDownloaded)
        assertTrue(post.quoted!!.fullTextDownloaded)
    }

    private fun post(text: String, cut: Boolean) = Post(
        id = "1", authorHandle = "h", authorName = "H", text = text, publishedAtMillis = 1,
        permalink = "https://x.com/h/status/1", textCut = cut
    )
}
