package com.mtga.app.data

import com.mtga.app.core.model.MediaItem
import com.mtga.app.core.model.MediaType
import com.mtga.app.data.fxtwitter.FxTwitterParser
import com.mtga.app.data.html.HtmlTimelineParser
import com.mtga.app.data.xcom.XPagePayload
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// Test protocol: JUnit 4 (https://github.com/junit-team/junit4), the style of HtmlTimelineParserTest.
// The picture's alt as each source writes it: Nitter's genImg(thumb, alt=photo.altText)
// (https://github.com/zedeus/nitter/blob/master/src/views/tweet.nim#renderPhotoAttachment),
// FxEmbed's APIPhoto.altText (https://github.com/FxEmbed/FxEmbed/blob/main/src/types/types.d.ts)
// and the media entity's ext_alt_text (https://github.com/zedeus/nitter/blob/master/src/parser.nim#L313-L331).
class MediaAltTextTest {

    @Test
    fun `Nitter's img alt reaches the picture, an empty one leaves it null`() {
        fun photo(alt: String) =
            "<div class=\"attachment image\"><a href=\"/pic/orig/media%2Fa.jpg\" class=\"still-image\" target=\"_blank\">" +
                "<img src=\"/pic/media%2Fa.jpg\" alt=\"$alt\" loading=\"lazy\"></a></div>"
        val page = "<div class=\"timeline-item \" data-username=\"alice\">" +
            "<a class=\"tweet-link\" href=\"/alice/status/123#m\"></a><div class=\"tweet-body\">" +
            "<div class=\"tweet-content media-body\" dir=\"auto\">hello</div>%s</div></div>"

        val described = HtmlTimelineParser().parse(page.format(photo("A red fox &amp; its cub")), "alice", "nitter.example")!!
            .posts.single().media.single()
        val plain = HtmlTimelineParser().parse(page.format(photo("")), "alice", "nitter.example")!!
            .posts.single().media.single()

        assertEquals("A red fox & its cub", described.altText)
        assertNull(plain.altText)
    }

    // The page shape of XPageMediaQuoteTest (JUnit 4, https://github.com/junit-team/junit4), the entity
    // of Nitter's parseLegacyMediaEntities: https://github.com/zedeus/nitter/blob/master/src/parser.nim#L313-L331
    @Test
    fun `an x_com page's ext_alt_text reaches the picture`() {
        val user = """"core":{"user_results":{"result":{"core":{"name":"A","screen_name":"a"}}}}"""
        val tweet = """{"__typename":"Tweet","rest_id":"1",$user,"details":{"created_at_ms":1790000000000,"full_text":"t"},
            "media_entities2":[{"type":"photo","media_url_https":"https://pbs.twimg.com/media/a.jpg","ext_alt_text":"A harbour at dusk"},
            {"type":"photo","media_url_https":"https://pbs.twimg.com/media/b.jpg"}]}"""
        val answer = """{"data":{"items":[{"__typename":"TimelineTweet","tweet_results":{"result":$tweet}}]}}"""
        val page = "<script>(\$R=>\$R[1]={kind:\"Result.Ok\",value:" + JsonPrimitive(answer) + "})</script>"
        val media = XPagePayload.posts(page).single().media

        assertEquals("A harbour at dusk", media[0].altText)
        assertNull(media[1].altText)
    }

    @Test
    fun `FxTwitter's altText reaches the picture`() {
        val obj = Json.parseToJsonElement(
            """{"id":"1","text":"t","author":{"screen_name":"a","name":"A"},"media":{"all":[
                {"type":"photo","url":"https://pbs.twimg.com/media/a.jpg","width":10,"height":10,"altText":"A lighthouse"},
                {"type":"photo","url":"https://pbs.twimg.com/media/b.jpg","width":10,"height":10}]}}"""
        ) as JsonObject
        val media: List<MediaItem> = FxTwitterParser.post(obj)!!.media

        assertEquals(MediaType.PHOTO, media[0].type)
        assertEquals("A lighthouse", media[0].altText)
        assertNull(media[1].altText)
    }
}
