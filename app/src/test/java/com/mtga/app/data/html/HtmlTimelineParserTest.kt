package com.mtga.app.data.html

import com.mtga.app.core.model.MediaType
import com.mtga.app.core.model.PostKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Markup as Nitter writes it (AGPL-3.0, zedeus/nitter): renderVideoAttachment per video
 * (video, overlay-duration, video-download) and renderReply for "Replying to".
 * Test shape: JUnit 4 (https://github.com/junit-team/junit4/wiki/Getting-started).
 * https://github.com/zedeus/nitter/blob/master/src/views/tweet.nim#renderVideo
 */
class HtmlTimelineParserTest {

    private val parser = HtmlTimelineParser()

    private fun item(reply: String = "", media: String = ""): String =
        "<div class=\"timeline-item \" data-username=\"alice\">" +
            "<a class=\"tweet-link\" href=\"/alice/status/123#m\"></a><div class=\"tweet-body\"><div>" +
            "<div class=\"tweet-header\"><a class=\"tweet-avatar\" href=\"/alice\"><img src=\"/pic/a.jpg\"></a>" +
            "<div class=\"tweet-name-row\"><div class=\"fullname-and-username\">" +
            "<a class=\"fullname\" href=\"/alice\" title=\"Alice\">Alice</a>" +
            "<a class=\"username\" href=\"/alice\" title=\"@alice\">@alice</a></div>" +
            "<span class=\"tweet-date\"><a href=\"/alice/status/123#m\" title=\"Nov 5, 2026 &#183; 10:00 AM UTC\">1h</a></span>" +
            "</div></div>" +
            reply +
            "<div class=\"tweet-content media-body\" dir=\"auto\">hello</div>" +
            media +
            "</div></div></div>"

    private val hls =
        "<div class=\"attachment\"><video poster=\"/pic/one.jpg\" data-url=\"/video/h/one.m3u8\" data-autoload=\"false\"></video>" +
            "<div class=\"video-overlay\" onclick=\"playVideo(this)\"><div class=\"overlay-circle\"><span class=\"overlay-triangle\"></span></div>" +
            "<div class=\"overlay-duration\">0:12</div></div>" +
            "<a class=\"video-download\" href=\"/video/h/one.mp4\" download=\"\" title=\"Download video\"><span class=\"icon-download-alt\"></span></a></div>"

    private val mp4 =
        "<div class=\"attachment\"><video poster=\"/pic/two.jpg\" controls=\"\"><source src=\"/video/h/two.mp4\" type=\"video/mp4\"></video>" +
            "<a class=\"video-download\" href=\"/video/h/two-best.mp4\" download=\"\" title=\"Download video\"><span class=\"icon-download-alt\"></span></a></div>"

    @Test
    fun `two videos in one post each keep their own download link and duration`() {
        val media = "<div class=\"attachments\"><div class=\"gallery-row mixed-row\">$hls$mp4</div></div>"
        val post = parser.parse(item(media = media), "alice", "nitter.example")!!.posts.single()

        assertEquals(2, post.media.size)
        assertEquals("https://nitter.example/video/h/one.mp4", post.media[0].downloadUrl)
        assertEquals("0:12", post.media[0].durationLabel)
        assertEquals("https://nitter.example/video/h/two-best.mp4", post.media[1].downloadUrl)
        assertNull(post.media[1].durationLabel)
        assertEquals(MediaType.VIDEO, post.media[1].type)
    }

    @Test
    fun `one video still reads as before`() {
        val media = "<div class=\"attachments card\"><div class=\"gallery-video\">$hls</div></div>"
        val post = parser.parse(item(media = media), "alice", "nitter.example")!!.posts.single()

        assertEquals(1, post.media.size)
        assertEquals("https://nitter.example/video/h/one.mp4", post.media[0].downloadUrl)
        assertEquals("0:12", post.media[0].durationLabel)
    }

    @Test
    fun `a gif beside a video does not take the video's link`() {
        val gif = "<div class=\"attachment\"><video class=\"gif\" poster=\"/pic/g.jpg\" autoplay=\"\" loop=\"\">" +
            "<source src=\"/video/h/g.mp4\" type=\"video/mp4\"></video></div>"
        val post = parser.parse(item(media = "<div class=\"attachments\">$gif$mp4</div>"), "alice", "nitter.example")!!
            .posts.single()

        assertEquals(MediaType.GIF, post.media[0].type)
        assertEquals("https://nitter.example/video/h/g.mp4", post.media[0].downloadUrl)
        assertEquals("https://nitter.example/video/h/two-best.mp4", post.media[1].downloadUrl)
    }

    @Test
    fun `a reply to the author alone keeps the author as the replied-to name`() {
        val reply = "<div class=\"replying-to\">Replying to <a href=\"/alice\">@alice</a></div>"
        val post = parser.parse(item(reply = reply), "alice", "nitter.example")!!.posts.single()

        assertEquals(PostKind.REPLY, post.kind)
        assertEquals("alice", post.relatedHandle)
    }

    @Test
    fun `a reply to the author and another names the other`() {
        val reply = "<div class=\"replying-to\">Replying to <a href=\"/alice\">@alice</a> <a href=\"/bob\">@bob</a></div>"
        val post = parser.parse(item(reply = reply), "alice", "nitter.example")!!.posts.single()

        assertEquals("bob", post.relatedHandle)
    }

    @Test
    fun `a reply to another account names it`() {
        val reply = "<div class=\"replying-to\">Replying to <a href=\"/bob\">@bob</a></div>"
        val post = parser.parse(item(reply = reply), "alice", "nitter.example")!!.posts.single()

        assertEquals("bob", post.relatedHandle)
    }

    // renderQuote and renderQuoteMedia, as Nitter writes them (AGPL-3.0, zedeus/nitter):
    // https://github.com/zedeus/nitter/blob/master/src/views/tweet.nim#renderQuote
    // https://github.com/zedeus/nitter/blob/master/src/views/tweet.nim#renderQuoteMedia
    private val quoteHead =
        "<div class=\"quote quote-big\"><a class=\"quote-link\" href=\"/nasa/status/9#m\"></a>" +
            "<div class=\"tweet-name-row\"><div class=\"fullname-and-username\">" +
            "<a class=\"fullname\" href=\"/nasa\" title=\"NASA\">NASA</a>" +
            "<a class=\"username\" href=\"/nasa\" title=\"@nasa\">@nasa</a></div>" +
            "<span class=\"tweet-date\"><a href=\"/nasa/status/9#m\" title=\"Nov 5, 2026\">5h</a></span></div>" +
            "<div class=\"quote-text\" dir=\"auto\">New images from the telescope.</div>"

    // https://github.com/zedeus/nitter/blob/master/src/views/tweet.nim#renderPhotoAttachment
    private fun photoMarkup(name: String) =
        "<div class=\"attachment image\"><a href=\"/pic/orig/media%2F$name.jpg\" class=\"still-image\" target=\"_blank\">" +
            "<img src=\"/pic/media%2F$name.jpg%3Fname%3Dsmall%26format%3Dwebp\" alt=\"\" loading=\"lazy\"></a></div>"

    private fun previewUrl(name: String) = "https://nitter.example/pic/media%2F$name.jpg%3Fname%3Dsmall%26format%3Dwebp"

    @Test
    fun `a quote keeps the media of its quote-media-container, not the post's own`() {
        // renderPhotoAttachment's markup, https://github.com/zedeus/nitter/blob/master/src/views/tweet.nim#renderPhotoAttachment
        // the link before its class and the names as long as X's.
        val own = "<div class=\"attachments\"><div class=\"gallery-row\">" + photoMarkup("OwnPictureHTi0jsgW4AATctB") + "</div></div>"
        val container = "<div class=\"quote-media-container\"><div class=\"attachments\"><div class=\"gallery-row\">" +
            photoMarkup("FirstPictureHTi0jsgW4AATctB") + photoMarkup("SecondPictureHTi0jsgW4AATctB") +
            "</div></div></div>"
        val post = parser.parse(item(media = own + quoteHead + container + "</div>"), "alice", "nitter.example")!!
            .posts.single()

        assertEquals(listOf(previewUrl("OwnPictureHTi0jsgW4AATctB")), post.media.map { it.previewUrl })
        val quote = post.quoted!!
        assertEquals("New images from the telescope.", quote.text)
        assertEquals(
            listOf(previewUrl("FirstPictureHTi0jsgW4AATctB"), previewUrl("SecondPictureHTi0jsgW4AATctB")),
            quote.media.map { it.previewUrl }
        )
        assertEquals(MediaType.PHOTO, quote.media[0].type)
        assertNull(quote.unavailable)
    }

    @Test
    fun `a quote without media has none`() {
        val post = parser.parse(item(media = quoteHead + "</div>"), "alice", "nitter.example")!!.posts.single()

        assertEquals(emptyList<Any>(), post.quoted!!.media)
    }

    @Test
    fun `an unavailable quote gives its tombstone text`() {
        val unavailable = "<div class=\"quote unavailable\"><a class=\"unavailable-quote\" href=\"//status/0#m\">" +
            "This Post is from an account that no longer exists.</a></div>"
        val post = parser.parse(item(media = unavailable), "alice", "nitter.example")!!.posts.single()

        assertEquals(PostKind.QUOTE, post.kind)
        assertEquals("This Post is from an account that no longer exists.", post.quoted?.unavailable)
        assertEquals("", post.quoted?.permalink)
    }

    @Test
    fun `an unavailable quote with no text says so as Nitter does`() {
        val unavailable = "<div class=\"quote unavailable\"><a class=\"unavailable-quote\" href=\"//status/0#m\"></a></div>"
        val post = parser.parse(item(media = unavailable), "alice", "nitter.example")!!.posts.single()

        assertEquals("This tweet is unavailable", post.quoted?.unavailable)
    }
}
