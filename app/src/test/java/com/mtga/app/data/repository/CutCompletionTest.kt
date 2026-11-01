package com.mtga.app.data.repository

import com.mtga.app.core.model.Post
import com.mtga.app.core.model.QuotedPost
import com.mtga.app.data.fxtwitter.FxTwitterSource.Whole
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// Test protocol: JUnit 4 (https://github.com/junit-team/junit4). The answers
// follow FxEmbed's /2/status/<id> specification
// (https://github.com/FxEmbed/FxEmbed/blob/main/docs/specs/fxtwitter-openapi.json)
// and its note tweet rule
// (https://github.com/FxEmbed/FxEmbed/blob/main/packages/atmosphere/src/providers/twitter/processor.ts).
/** The completion of cut posts, pass after pass, and the pin kept when an id comes twice. */
class CutCompletionTest {

    @Test
    fun `a marked post is completed and settled`() = runBlocking {
        val settled = HashSet<String>()
        val posts = listOf(post("10", "head", cut = true))

        val done = CutCompletion.run(posts, settled, 15) { Whole.Text("head and the rest") }

        assertEquals(mapOf("10" to "head and the rest"), done.texts)
        assertTrue("10" in settled)
    }

    @Test
    fun `a failed completion is asked again at the next pass`() = runBlocking {
        val settled = HashSet<String>()
        val posts = listOf(post("10", "head", cut = true))
        var asks = 0

        val first = CutCompletion.run(posts, settled, 15) { asks++; Whole.Unavailable }
        val second = CutCompletion.run(posts, settled, 15) { asks++; Whole.Text("head and the rest") }

        assertTrue(first.texts.isEmpty())
        assertEquals(1, first.unanswered)
        assertEquals(2, asks)
        assertEquals("head and the rest", second.texts["10"])
        assertTrue("10" in settled)
    }

    @Test
    fun `a post FxTwitter says is gone is not asked again`() = runBlocking {
        val settled = HashSet<String>()
        val posts = listOf(post("10", "head", cut = true))
        var asks = 0

        CutCompletion.run(posts, settled, 15) { asks++; Whole.Gone }
        CutCompletion.run(posts, settled, 15) { asks++; Whole.Gone }

        assertEquals(1, asks)
    }

    @Test
    fun `marked posts come before posts only the length window suspects`() = runBlocking {
        val asked = ArrayList<String>()
        val posts = listOf(
            post("1", "a".repeat(260), cut = false),
            post("2", "short", cut = true)
        )

        CutCompletion.run(posts, HashSet(), 1) { asked += it; Whole.Gone }

        assertEquals(listOf("2"), asked)
    }

    @Test
    fun `the pass stops after the cap`() = runBlocking {
        val posts = (1..40).map { post("$it", "h", cut = true) }
        var asks = 0

        CutCompletion.run(posts, HashSet(), 15) { asks++; Whole.Gone }

        assertEquals(15, asks)
    }

    @Test
    fun `a pass gives up when FxTwitter keeps failing`() = runBlocking {
        val posts = (1..10).map { post("$it", "h", cut = true) }
        var asks = 0

        CutCompletion.run(posts, HashSet(), 15) { asks++; Whole.Unavailable }

        assertEquals(3, asks)
    }

    @Test
    fun `a cut quoted post is completed through its own id`() = runBlocking {
        val quote = QuotedPost("a", "A", "quote head", "https://x.com/a/status/77", textCut = true)
        val posts = listOf(post("10", "short whole text", cut = false).copy(quoted = quote))
        val asked = ArrayList<String>()

        val done = CutCompletion.run(posts, HashSet(), 15) { asked += it; Whole.Text("quote head and the rest") }

        assertEquals(listOf("77"), asked)
        assertTrue(done.texts.isEmpty())
        assertEquals(mapOf("10" to "quote head and the rest"), done.quotedTexts)
    }

    @Test
    fun `a marked post answered with the same text is cleared without a new text`() = runBlocking {
        val posts = listOf(post("10", "exactly the whole text", cut = true))

        val done = CutCompletion.run(posts, HashSet(), 15) { Whole.Text("exactly the whole text") }

        assertEquals(mapOf("10" to "exactly the whole text"), done.texts)
    }

    @Test
    fun `an unmarked post of the window answered the same is left alone`() = runBlocking {
        val text = "a".repeat(260)
        val posts = listOf(post("10", text, cut = false))

        val done = CutCompletion.run(posts, HashSet(), 15) { Whole.Text(text) }

        assertTrue(done.texts.isEmpty())
        assertFalse(done.quotedTexts.isNotEmpty())
    }

    @Test
    fun `the pinned copy wins when an id comes twice`() {
        val plain = post("5", "t", cut = false)
        val pinned = plain.copy(isPinned = true)

        assertTrue(distinctKeepingPin(listOf(plain, pinned)).single().isPinned)
        assertTrue(distinctKeepingPin(listOf(pinned, plain)).single().isPinned)
        assertEquals(2, distinctKeepingPin(listOf(plain, post("6", "u", cut = false))).size)
    }

    private fun post(id: String, text: String, cut: Boolean) = Post(
        id = id, authorHandle = "h", authorName = "H", text = text, publishedAtMillis = 1,
        permalink = "https://x.com/h/status/$id", textCut = cut
    )
}
