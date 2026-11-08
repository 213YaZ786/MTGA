package com.mtga.app.core.text

import com.mtga.app.data.fxtwitter.FxTwitterParser
import com.mtga.app.data.html.HtmlTimelineParser
import com.mtga.app.data.rss.RssFeedParser
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

// Test protocol: JUnit 4 (https://github.com/junit-team/junit4). The cases are those of jsoup's own
// EntitiesTest, https://github.com/jhy/jsoup/blob/master/src/test/java/org/jsoup/nodes/EntitiesTest.java
// (unescape of numeric and named references, a base name without its semicolon, an unknown name
// left alone), then the parsers that now share the decoder.
class EntitiesTest {

    @Test
    fun `decimal, hexadecimal and named references decode once`() {
        assertEquals("'&…&lt;", Entities.unescape("&#x27;&#38;&hellip;&amp;lt;"))
    }

    @Test
    fun `a base name decodes without its semicolon, an extended one needs it`() {
        assertEquals("a © b", Entities.unescape("a &copy b"))
        assertEquals("a &hellip b", Entities.unescape("a &hellip b"))
        assertEquals("a … b", Entities.unescape("a &hellip; b"))
    }

    @Test
    fun `an unknown or half written reference stays as it was written`() {
        assertEquals("R&D &nosuch; AT&T &#; &#xZ; end &", Entities.unescape("R&D &nosuch; AT&T &#; &#xZ; end &"))
    }

    @Test
    fun `a numeric reference out of range or in the windows 1252 block is repaired`() {
        assertEquals("�", Entities.unescape("&#1114112;"))
        assertEquals("€", Entities.unescape("&#128;"))
        assertEquals("😀", Entities.unescape("&#x1F600;"))
    }

    @Test
    fun `strict mode leaves a semicolonless name followed by a letter or equals alone`() {
        assertEquals("?a=1&copy=2", Entities.unescape("?a=1&copy=2", true))
        assertEquals("?a=1©=2", Entities.unescape("?a=1&copy=2", false))
    }

    @Test
    fun `the Nitter page parser decodes a hexadecimal and a named reference in a post`() {
        val page = "<div class=\"timeline-item \" data-username=\"alice\">" +
            "<a class=\"tweet-link\" href=\"/alice/status/123#m\"></a><div class=\"tweet-body\">" +
            "<div class=\"tweet-content media-body\" dir=\"auto\">it&#x27;s 5 &#38; 6&hellip; &amp;lt;</div></div></div>"
        val post = HtmlTimelineParser().parse(page, "alice", "nitter.example")!!.posts.single()

        assertEquals("it's 5 & 6… &lt;", post.text)
    }

    @Test
    fun `the RSS parser decodes the description`() {
        assertEquals("it's & …", RssFeedParser().htmlToPlainText("<p>it&#x27;s &#38; &hellip;</p>"))
    }

    @Test
    fun `the FxTwitter parser decodes the text of a post`() {
        val obj = Json.parseToJsonElement(
            """{"id":"1","text":"Q&amp;A &#x27;live&#x27; &hellip;","author":{"screen_name":"a","name":"A"}}"""
        ) as JsonObject

        assertEquals("Q&A 'live' …", FxTwitterParser.post(obj)!!.text)
    }
}
