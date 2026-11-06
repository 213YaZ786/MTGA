package com.mtga.app.data.xcom

import com.mtga.app.core.model.MediaType
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// Test protocol: JUnit 4 (https://github.com/junit-team/junit4), the style of XPageTextResultTest.
// The media entity is the legacy shape Nitter's parseLegacyMediaEntities reads
// (https://github.com/zedeus/nitter/blob/master/src/parser.nim#L313-L331), with the original_info
// and video_info.aspect_ratio of the answers (the sizes Squawker reads in
// https://github.com/j-fbriere/squawker/blob/master/lib/tweet/_video.dart#L75-L79), the
// tombstone is the one of Squawker's TweetWithCard.tombstone
// (https://github.com/j-fbriere/squawker/blob/master/lib/client/client.dart#L1398-L1405).
class XPageMediaQuoteTest {

    private val user = """"core":{"user_results":{"result":{"core":{"name":"A","screen_name":"a"}}}}"""

    private fun tweet(id: String, extra: String): String =
        """{"__typename":"Tweet","rest_id":"$id",$user,"details":{"created_at_ms":1790000000000,"full_text":"t"},$extra}"""

    /** A one tweet answer as the page streams it: the JSON text as one escaped string. */
    private fun page(tweet: String): String {
        val answer = """{"data":{"items":[{"__typename":"TimelineTweet","tweet_results":{"result":$tweet}}]}}"""
        return "<script>(\$R=>\$R[1]={kind:\"Result.Ok\",value:" + JsonPrimitive(answer) + "})</script>"
    }

    private fun photo(url: String, extra: String) =
        """{"type":"photo","media_url_https":"$url",$extra}"""

    @Test
    fun `a photo takes its original_info size`() {
        val media = tweet("1", """"media_entities2":[${photo("https://pbs.twimg.com/media/a.jpg", """"original_info":{"width":1600,"height":2000}""")}]""")
        val item = XPagePayload.posts(page(media)).single().media.single()

        assertEquals(MediaType.PHOTO, item.type)
        assertEquals(1600 to 2000, item.width to item.height)
    }

    @Test
    fun `a photo without original_info takes the legacy sizes large`() {
        val media = tweet("1", """"media_entities2":[${photo("https://pbs.twimg.com/media/a.jpg", """"sizes":{"large":{"w":1000,"h":500}}""")}]""")
        val item = XPagePayload.posts(page(media)).single().media.single()

        assertEquals(1000 to 500, item.width to item.height)
    }

    @Test
    fun `a video without original_info takes its aspect_ratio`() {
        val media = tweet(
            "1",
            """"media_entities2":[{"type":"video","media_url_https":"https://pbs.twimg.com/v/t.jpg",
            "video_info":{"aspect_ratio":[9,16],"duration_millis":12000,"variants":[{"bitrate":256000,"content_type":"video/mp4","url":"https://video.twimg.com/v.mp4"}]}}]"""
        )
        val item = XPagePayload.posts(page(media)).single().media.single()

        assertEquals(MediaType.VIDEO, item.type)
        assertEquals(9 to 16, item.width to item.height)
        assertEquals("https://video.twimg.com/v.mp4", item.downloadUrl)
    }

    @Test
    fun `a media with no size gives none`() {
        val media = tweet("1", """"media_entities2":[${photo("https://pbs.twimg.com/media/a.jpg", """"id_str":"7"""")}]""")
        val item = XPagePayload.posts(page(media)).single().media.single()

        assertNull(item.width)
        assertNull(item.height)
    }

    @Test
    fun `a quoted post carries its media`() {
        val quote = tweet("9", """"media_entities2":[${photo("https://pbs.twimg.com/media/q.jpg", """"original_info":{"width":800,"height":600}""")}]""")
        val post = XPagePayload.posts(page(tweet("1", """"quoted_status_result":{"result":$quote}"""))).single()

        val media = post.quoted!!.media.single()
        assertEquals("https://pbs.twimg.com/media/q.jpg", media.previewUrl)
        assertEquals(800 to 600, media.width to media.height)
        assertNull(post.quoted!!.unavailable)
    }

    @Test
    fun `a tombstone quote takes its richText, else its text, without Learn more`() {
        fun unavailable(tombstone: String) = XPagePayload.posts(
            page(tweet("1", """"quoted_status_result":{"result":{"__typename":"TweetTombstone","tombstone":$tombstone}}"""))
        ).single().quoted?.unavailable

        assertEquals(
            "This Post is from a suspended account.",
            unavailable("""{"richText":{"text":"This Post is from a suspended account. Learn more"},"text":{"text":"other"}}""")
        )
        assertEquals(
            "This Post was deleted by the Post author.",
            unavailable("""{"text":{"text":"This Post was deleted by the Post author. Learn more"}}""")
        )
        assertEquals("This post is unavailable", unavailable("{}"))
    }

    @Test
    fun `an unavailable tweet quote is kept with no text of its own`() {
        val post = XPagePayload.posts(
            page(tweet("1", """"quoted_status_result":{"result":{"__typename":"TweetUnavailable"}}"""))
        ).single()

        assertEquals("This post is unavailable", post.quoted?.unavailable)
        assertEquals("", post.quoted?.text)
    }
}
