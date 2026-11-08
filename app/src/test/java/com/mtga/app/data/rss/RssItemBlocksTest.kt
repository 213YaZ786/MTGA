package com.mtga.app.data.rss

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mtga.app.core.model.MediaType
import com.mtga.app.core.model.PostKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

// Test protocol: JUnit 4 (https://github.com/junit-team/junit4) on Robolectric for android.util.Xml, the
// shape of the screenshot tests. The descriptions are what Nitter's RSS template writes:
// https://github.com/zedeus/nitter/blob/master/src/views/rss.nimf#renderRssTweet
// (the text paragraph, renderRssMedia, renderRssPoll, the blockquote of the quote).
@RunWith(AndroidJUnit4::class)
@Config(application = android.app.Application::class, sdk = [36])
class RssItemBlocksTest {

    private fun feed(description: String, title: String = "gif, poll and quote") =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?><rss xmlns:dc=\"http://purl.org/dc/elements/1.1/\" version=\"2.0\"><channel>" +
            "<title>Alice / @alice</title><item><title>$title</title><dc:creator>@alice</dc:creator>" +
            "<description><![CDATA[$description]]></description><pubDate>Fri, 06 Nov 2026 10:00:00 GMT</pubDate>" +
            "<guid>https://nitter.example/alice/status/123#m</guid><link>https://nitter.example/alice/status/123#m</link></item>" +
            "</channel></rss>"

    private val gif = "<video poster=\"https://nitter.example/pic/tweet_video_thumb%2Fg.jpg\" autoplay muted loop style=\"max-width:250px;\">\n" +
        "  <source src=\"https://nitter.example/pic/video.twimg.com%2Ftweet_video%2Fg.mp4\" type=\"video/mp4\"></video>"

    private val poll = "<hr/>\n<b>Poll</b>\n<p>\n70% — Yes<br>\n30% — No &amp; maybe<br>\n<i>1,234 votes • Final results</i>\n</p>"

    private val quote = "<hr/>\n<blockquote>\n<b>NASA (@nasa)</b>\n<p>\n<p>New images from the telescope.</p>\n" +
        "<img src=\"https://nitter.example/pic/media%2Fq.jpg\" style=\"max-width:250px;\" />\n</p>\n<footer>\n" +
        "— <cite><a href=\"https://nitter.example/nasa/status/9#m\">https://nitter.example/nasa/status/9#m</a>\n</footer>\n</blockquote>"

    @Test
    fun `a gif keeps its poster and its source, a poll its options and a quote its own text`() {
        val description = "<p>Look at this<br>\nand vote</p>\n$gif\n$poll\n$quote"
        val post = RssFeedParser().parse(feed(description), "alice", "nitter.example")!!.posts.single()

        // The post's own text is its paragraph only: no "Poll", no percent, no quote.
        assertEquals("Look at this\nand vote", post.text)

        val media = post.media.single()
        assertEquals(MediaType.GIF, media.type)
        assertEquals("https://nitter.example/pic/tweet_video_thumb%2Fg.jpg", media.previewUrl)
        assertEquals("https://nitter.example/pic/video.twimg.com%2Ftweet_video%2Fg.mp4", media.downloadUrl)

        val poll = post.poll!!
        assertEquals(listOf("Yes", "No & maybe"), poll.options.map { it.label })
        assertEquals(listOf(70, 30), poll.options.map { it.percent })
        assertEquals(listOf(true, false), poll.options.map { it.leader })
        assertEquals(1234L, poll.votes)
        assertEquals("Final results", poll.status)

        val quoted = post.quoted!!
        assertEquals("nasa", quoted.handle)
        assertEquals("NASA", quoted.name)
        assertEquals("New images from the telescope.", quoted.text)
        assertEquals("https://nitter.example/nasa/status/9#m", quoted.permalink)
        assertEquals(listOf("https://nitter.example/pic/media%2Fq.jpg"), quoted.media.map { it.previewUrl })
        assertEquals(PostKind.QUOTE, post.kind)
    }

    @Test
    fun `the quote's picture is not the post's own`() {
        val description = "<p>Hello</p>\n<img src=\"https://nitter.example/pic/media%2Fown.jpg\" style=\"max-width:250px;\" />\n$quote"
        val post = RssFeedParser().parse(feed(description), "alice", "nitter.example")!!.posts.single()

        assertEquals(listOf("https://nitter.example/pic/media%2Fown.jpg"), post.media.map { it.previewUrl })
        assertEquals(MediaType.PHOTO, post.media.single().type)
    }

    @Test
    fun `a plain post has no poll and no quote`() {
        val post = RssFeedParser().parse(feed("<p>Just text</p>"), "alice", "nitter.example")!!.posts.single()

        assertEquals("Just text", post.text)
        assertNull(post.poll)
        assertNull(post.quoted)
        assertEquals(PostKind.ORIGINAL, post.kind)
    }
}
