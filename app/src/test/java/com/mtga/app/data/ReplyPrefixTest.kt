package com.mtga.app.data

import com.mtga.app.data.fxtwitter.FxTwitterParser
import com.mtga.app.data.html.HtmlTimelineParser
import com.mtga.app.data.xcom.XPagePayload
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

// Test protocol: JUnit 4 (https://github.com/junit-team/junit4). A reply's text starts with the
// accounts it answers and X hides them with display_text_range, as Nitter's expandTextEntities
// cuts its textSlice: https://github.com/zedeus/nitter/blob/master/src/parserutils.nim#expandTextEntities
class ReplyPrefixTest {

    @Test
    fun `leading mentions of the replied account are removed from a reply`() {
        val text = XPagePayload.expandTextEntities("@nasa @esa Great work", JsonArray(emptyList()), null, listOf("nasa"), false)

        assertEquals("Great work", text)
    }

    @Test
    fun `a text that starts with another account is left alone`() {
        val text = XPagePayload.expandTextEntities("@bob @esa Great work", JsonArray(emptyList()), null, listOf("nasa"), false)

        assertEquals("@bob @esa Great work", text)
    }

    @Test
    fun `a mention inside the text stays`() {
        val text = XPagePayload.expandTextEntities("@nasa thanks to @esa", JsonArray(emptyList()), null, listOf("nasa"), false)

        assertEquals("thanks to @esa", text)
    }

    @Test
    fun `x com's display_text_range cuts the text`() {
        val user = """"core":{"user_results":{"result":{"core":{"name":"A","screen_name":"a"}}}}"""
        val tweet = """{"__typename":"Tweet","rest_id":"1",$user,"details":{"created_at_ms":1790000000000,
            "full_text":"@nasa @esa Great work &amp; more https://t.co/abc","display_text_range":[11,45]},
            "url_entities":[{"url":"https://t.co/abc","display_url":"example.com/x","expanded_url":"https://example.com/x"}]}"""
        val answer = """{"data":{"items":[{"__typename":"TimelineTweet","tweet_results":{"result":$tweet}}]}}"""
        val page = "<script>(\$R=>\$R[1]={kind:\"Result.Ok\",value:" + JsonPrimitive(answer) + "})</script>"

        assertEquals("Great work & more example.com/x", XPagePayload.posts(page).single().text)
    }

    @Test
    fun `FxTwitter's raw_text display range is carried over to its expanded text`() {
        val obj = Json.parseToJsonElement(
            """{"id":"1","text":"@nasa @esa Great work https://example.com/x","replying_to":{"screen_name":"nasa"},
                "raw_text":{"text":"@nasa @esa Great work https://t.co/abc","display_text_range":[11,32]},
                "author":{"screen_name":"a","name":"A"}}"""
        ) as JsonObject

        assertEquals("Great work https://example.com/x", FxTwitterParser.post(obj)!!.text)
    }

    @Test
    fun `FxTwitter without a display range removes the replied account's mentions`() {
        val obj = Json.parseToJsonElement(
            """{"id":"1","text":"@nasa @esa Great work","replying_to":{"screen_name":"nasa"},"author":{"screen_name":"a","name":"A"}}"""
        ) as JsonObject

        assertEquals("Great work", FxTwitterParser.post(obj)!!.text)
    }

    @Test
    fun `FxTwitter keeps a text whose range hides nothing`() {
        val obj = Json.parseToJsonElement(
            """{"id":"1","text":"@nasa hello","replying_to":{"screen_name":"nasa"},
                "raw_text":{"text":"@nasa hello","display_text_range":[0,11]},"author":{"screen_name":"a","name":"A"}}"""
        ) as JsonObject

        assertEquals("@nasa hello", FxTwitterParser.post(obj)!!.text)
    }

    @Test
    fun `a Nitter reply drops the accounts it starts with`() {
        val page = "<div class=\"timeline-item \" data-username=\"alice\">" +
            "<a class=\"tweet-link\" href=\"/alice/status/123#m\"></a><div class=\"tweet-body\">" +
            "<div class=\"replying-to\">Replying to <a href=\"/nasa\">@nasa</a> <a href=\"/esa\">@esa</a></div>" +
            "<div class=\"tweet-content media-body\" dir=\"auto\">@nasa @esa Great work</div></div></div>"
        val post = HtmlTimelineParser().parse(page, "alice", "nitter.example")!!.posts.single()

        assertEquals("Great work", post.text)
    }
}
