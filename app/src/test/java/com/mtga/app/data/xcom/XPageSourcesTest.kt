package com.mtga.app.data.xcom

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// Test protocol: JUnit 4 (https://github.com/junit-team/junit4), the style of
// XPageTextResultTest. The shapes come from three sources:
// Seroval's string escapes (https://github.com/lxsmnsyc/seroval/blob/main/packages/seroval/src/core/string.ts),
// FxEmbed's note tweet entities (https://github.com/FxEmbed/FxEmbed/blob/main/packages/atmosphere/src/providers/twitter/processor.ts#L517-L537)
// and Squawker's three pin entry paths (https://github.com/j-fbriere/squawker/blob/master/lib/client/client.dart#L1010-L1040).
class XPageSourcesTest {

    @Test
    fun `decodes the escapes Seroval writes and nothing else`() {
        // Backslash, quote, n r b t f, x3C and the two separators are decoded.
        val written = "\"a\\\\b\\\"c\\nd\\re\\bf\\tg\\fh\\x3Ci\\u2028j\\u2029k\""
        val decoded = Json.parseToJsonElement(JsLiteral.toJson(written)) as JsonPrimitive

        val lineSeparator = 0x2028.toChar()
        val paragraphSeparator = 0x2029.toChar()
        val formFeed = 0x0c.toChar()
        assertEquals("a\\b\"c\nd\re\bf\tg${formFeed}h<i${lineSeparator}j${paragraphSeparator}k", decoded.content)
    }

    @Test
    fun `leaves an escape Seroval never writes as it is`() {
        // \x41, \' and a unicode escape other than the two separators are not
        // in Seroval's set: the backslash stays.
        val written = "\"p\\x41q\\'r\\u0041s\\zt\""
        val decoded = Json.parseToJsonElement(JsLiteral.toJson(written)) as JsonPrimitive

        assertEquals("p\\x41q\\'r\\u0041s\\zt", decoded.content)
    }

    @Test
    fun `a post text with a less than sign written as x3C reads back`() {
        val posts = XPagePayload.posts(page(answer(tweet("501", "Is 1 <2 and a <b> tag?"), pin = "")))

        assertEquals("Is 1 <2 and a <b> tag?", posts.single().text)
    }

    @Test
    fun `a note tweet's links replace the legacy ones and expand in a long post`() {
        val long = tweet(
            "502",
            "Cut text https://t.co/OLD",
            urls = """[{"display_url":"old.example","expanded_url":"https://old.example","url":"https://t.co/OLD"}]""",
            note = """{"note_tweet_results":{"result":{"__typename":"NoteTweet","entity_set":{"hashtags":[],"symbols":[],"urls":[
                {"display_url":"example.org/a","expanded_url":"https://example.org/a","url":"https://t.co/AAA"}],"user_mentions":[]},
                "text":"The whole text, see https://t.co/AAA for the rest."}}}"""
        )
        val post = XPagePayload.posts(page(answer(long, pin = ""))).single()

        assertEquals("The whole text, see example.org/a for the rest.", post.text)
        assertEquals(listOf("https://example.org/a"), post.links)
        assertFalse(post.textCut)
    }

    @Test
    fun `a post without a note keeps its legacy links`() {
        val short = tweet(
            "503",
            "Short https://t.co/OLD",
            urls = """[{"display_url":"old.example","expanded_url":"https://old.example","url":"https://t.co/OLD"}]"""
        )
        val post = XPagePayload.posts(page(answer(short, pin = ""))).single()

        assertEquals("Short old.example", post.text)
        assertEquals(listOf("https://old.example"), post.links)
    }

    @Test
    fun `reads a pin under entry content content tweetResult result`() {
        val pin = """{"__typename":"TimelinePinEntry","entry":{"content":{"content":{"tweetResult":{"result":${tweet("601", "Pinned one")}}}}}}"""

        val posts = XPagePayload.posts(page(answer(tweet("602", "Plain"), pin = pin)))

        assertEquals(listOf("601", "602"), posts.map { it.id })
        assertTrue(posts[0].isPinned)
        assertFalse(posts[1].isPinned)
    }

    @Test
    fun `reads a pin under entry content itemContent tweet_results result`() {
        val pin = """{"__typename":"TimelinePinEntry","entry":{"content":{"itemContent":{"tweet_results":{"result":${tweet("611", "Pinned two")}}}}}}"""

        val posts = XPagePayload.posts(page(answer(tweet("612", "Plain"), pin = pin)))

        assertEquals(listOf("611", "612"), posts.map { it.id })
        assertTrue(posts[0].isPinned)
        assertFalse(posts[1].isPinned)
    }

    @Test
    fun `reads a pin under entry content content tweet_results result`() {
        val pin = """{"type":"TimelinePinEntry","entry":{"content":{"content":{"tweet_results":{"result":${tweet("621", "Pinned three")}}}}}}"""

        val posts = XPagePayload.posts(page(answer(tweet("622", "Plain"), pin = pin)))

        assertEquals(listOf("621", "622"), posts.map { it.id })
        assertTrue(posts[0].isPinned)
        assertFalse(posts[1].isPinned)
    }

    @Test
    fun `a pin wrapped in a result without rest_id is read from its tweet`() {
        val wrapped = """{"__typename":"TweetWithVisibilityResults","tweet":${tweet("631", "Pinned behind a notice")}}"""
        val pin = """{"__typename":"TimelinePinEntry","entry":{"content":{"content":{"tweet_results":{"result":$wrapped}}}}}"""

        val posts = XPagePayload.posts(page(answer(tweet("632", "Plain"), pin = pin)))

        assertEquals(listOf("631", "632"), posts.map { it.id })
        assertTrue(posts[0].isPinned)
    }

    @Test
    fun `a pin entry naming no tweet adds no post`() {
        val pin = """{"__typename":"TimelinePinEntry","entry":{"content":{"content":{}}}}"""

        val posts = XPagePayload.posts(page(answer(tweet("641", "Plain"), pin = pin)))

        assertEquals(listOf("641"), posts.map { it.id })
        assertFalse(posts.single().isPinned)
    }

    /** A tweet result without any social context, as an ordinary post comes. */
    private fun tweet(id: String, text: String, urls: String = "[]", note: String? = null): String =
        """{"__typename":"Tweet","core":{"user_results":{"result":{"__typename":"User","core":{"name":"Example","screen_name":"example"},"rest_id":"42"}}},""" +
            """"details":{"created_at_ms":1790577458000,"full_text":${JsonPrimitive(text)}},"media_entities2":[],"rest_id":"$id",""" +
            (if (note != null) """"note_tweet":$note,""" else "") +
            """"url_entities":$urls}"""

    /** The answer of the profile query with one ordinary post and an optional pin entry. */
    private fun answer(tweet: String, pin: String): String {
        val item = """{"__typename":"TimelineTweet","tweet_results":{"result":$tweet}}"""
        val entries = """{"__typename":"TimelineAddEntries","entries":[{"content":{"__typename":"TimelineTimelineItem","content":$item}}]}"""
        val instructions = if (pin.isEmpty()) entries else "$pin,$entries"
        return """{"data":{"user_result_by_screen_name":{"result":{"__typename":"User","profile_user_originals_timeline":{"timeline":{"instructions":[$instructions]}}}}}}"""
    }

    /**
     * The page as x.com writes it: the answer as one JavaScript string literal,
     * with every less than sign spelled \x3C as Seroval does.
     */
    private fun page(answer: String): String {
        val literal = JsonPrimitive(answer).toString().replace("<", "\\x3C")
        return "<script>(${D}R=>${D}R[24]={kind:1,requestId:2,key:\"k\",result:${D}R[25]={kind:\"Result.Ok\",value:$literal}})</script>"
    }

    private companion object {
        const val D = "$"
    }
}
