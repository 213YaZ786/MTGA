package com.mtga.app.data

import com.mtga.app.core.model.Post
import com.mtga.app.data.fxtwitter.FxTwitterParser
import com.mtga.app.data.html.HtmlTimelineParser
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// Test protocol: JUnit 4 (https://github.com/junit-team/junit4). Nitter writes the time of an edited
// post as a link "Last edited <time>" to its history (renderTweet), FxEmbed's edited_at is a time
// or null: https://github.com/zedeus/nitter/blob/master/src/views/tweet.nim#renderTweet
class EditedPostTest {

    private fun nitter(published: String, content: String = "hello"): Post {
        val page = "<div class=\"timeline-item \" data-username=\"alice\">" +
            "<a class=\"tweet-link\" href=\"/alice/status/123#m\"></a><div class=\"tweet-body\">" +
            "<div class=\"tweet-content media-body\" dir=\"auto\">$content</div>$published</div></div>"
        return HtmlTimelineParser().parse(page, "alice", "nitter.example")!!.posts.single()
    }

    @Test
    fun `Nitter's Last edited line marks the post edited`() {
        val edited = nitter("<p class=\"tweet-published\"><a href=\"/alice/status/123/history\">Last edited Nov 5, 2026 &#183; 10:00 AM UTC</a></p>")
        val plain = nitter("<p class=\"tweet-published\">Nov 5, 2026 &#183; 10:00 AM UTC</p>")
        val quoting = nitter("<p class=\"tweet-published\">Nov 5, 2026</p>", content = "Last edited my notes")

        assertTrue(edited.edited)
        assertFalse(plain.edited)
        assertFalse(quoting.edited)
    }

    private fun fx(extra: String) = FxTwitterParser.post(
        Json.parseToJsonElement("""{"id":"1","text":"t"$extra,"author":{"screen_name":"a","name":"A"}}""") as JsonObject
    )!!

    @Test
    fun `FxTwitter's edited_at marks the post edited, null does not`() {
        assertTrue(fx(""","edited_at":"2026-11-05T10:00:00.000Z"""").edited)
        assertFalse(fx(""","edited_at":null""").edited)
        assertFalse(fx("").edited)
    }

    @Test
    fun `an edit survives a merge with a copy that does not know it`() {
        val stored = fx(""","edited_at":"2026-11-05T10:00:00.000Z"""")
        val fresh = stored.copy(edited = false)

        assertEquals(true, stored.mergedWith(fresh).edited)
        assertEquals(true, fresh.mergedWith(stored).edited)
    }

    @Test
    fun `a post stored before the field existed is not edited`() {
        val old = """{"id":"1","authorHandle":"a","authorName":"A","text":"t","publishedAtMillis":1,"permalink":"p"}"""

        assertFalse(Json.decodeFromString(Post.serializer(), old).edited)
    }
}
