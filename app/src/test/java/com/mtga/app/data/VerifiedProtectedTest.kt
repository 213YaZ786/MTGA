package com.mtga.app.data

import com.mtga.app.core.model.Post
import com.mtga.app.core.model.VerifiedType
import com.mtga.app.data.fxtwitter.FxTwitterParser
import com.mtga.app.data.html.HtmlTimelineParser
import com.mtga.app.data.xcom.XPagePayload
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// Test protocol: JUnit 4 (https://github.com/junit-team/junit4). The marks as each source writes them:
// Nitter's verifiedIcon and linkUser markup
// (https://github.com/zedeus/nitter/blob/master/src/views/renderutils.nim#verifiedIcon),
// FxEmbed's author.verification and protected (docs/specs/fxtwitter-openapi.json), and x.com's
// is_blue_verified read as Nitter's parseGraphUser does
// (https://github.com/zedeus/nitter/blob/master/src/parser.nim#parseGraphUser).
class VerifiedProtectedTest {

    private fun page(nameRow: String) = "<div class=\"timeline-item \" data-username=\"alice\">" +
        "<a class=\"tweet-link\" href=\"/alice/status/123#m\"></a><div class=\"tweet-body\">" +
        "<div class=\"tweet-name-row\"><div class=\"fullname-and-username\">$nameRow" +
        "<a class=\"username\" href=\"/alice\" title=\"@alice\">@alice</a></div>" +
        "<span class=\"tweet-date\"><a href=\"/alice/status/123#m\" title=\"Nov 5, 2026\">1h</a></span></div>" +
        "<div class=\"tweet-content media-body\" dir=\"auto\">hello</div></div></div>"

    private fun nitter(nameRow: String): Post =
        HtmlTimelineParser().parse(page(nameRow), "alice", "nitter.example")!!.posts.single()

    @Test
    fun `Nitter's verified icon gives its type and its lock a protected account`() {
        val blue = nitter("<a class=\"fullname\" href=\"/alice\" title=\"Alice\">Alice</a>" +
            "<div class=\"verified-icon blue\"><span class=\"icon-circle verified-icon-circle\"></span></div>")
        val business = nitter("<a class=\"fullname\" href=\"/alice\" title=\"Alice\">Alice</a><div class=\"verified-icon business\"></div>")
        val government = nitter("<a class=\"fullname\" href=\"/alice\" title=\"Alice\">Alice</a><div class=\"verified-icon government\"></div>")
        val locked = nitter("<a class=\"fullname\" href=\"/alice\" title=\"Alice\">Alice <span class=\"icon-lock\" title=\"Protected account\"></span></a>")
        val plain = nitter("<a class=\"fullname\" href=\"/alice\" title=\"Alice\">Alice</a>")

        assertEquals(VerifiedType.BLUE, blue.verified)
        assertEquals(VerifiedType.BUSINESS, business.verified)
        assertEquals(VerifiedType.GOVERNMENT, government.verified)
        assertTrue(locked.isProtected)
        assertEquals(VerifiedType.NONE, locked.verified)
        assertEquals(VerifiedType.NONE, plain.verified)
        assertFalse(plain.isProtected)
    }

    private fun fx(author: String) = FxTwitterParser.post(
        Json.parseToJsonElement("""{"id":"1","text":"t","author":{"screen_name":"a","name":"A"$author}}""") as JsonObject
    )!!

    @Test
    fun `FxTwitter's author verification and protected are read`() {
        assertEquals(VerifiedType.BLUE, fx(""","verification":{"verified":true,"type":"individual"}""").verified)
        assertEquals(VerifiedType.BLUE, fx(""","verification":{"verified":true,"type":null}""").verified)
        assertEquals(VerifiedType.BUSINESS, fx(""","verification":{"verified":true,"type":"organization"}""").verified)
        assertEquals(VerifiedType.GOVERNMENT, fx(""","verification":{"verified":true,"type":"government"}""").verified)
        assertEquals(VerifiedType.NONE, fx(""","verification":{"verified":false,"type":null}""").verified)
        assertEquals(VerifiedType.NONE, fx("").verified)
        assertTrue(fx(""","protected":true""").isProtected)
        assertFalse(fx(""","protected":false""").isProtected)
    }

    private fun xcom(userExtra: String): Post {
        val user = """"core":{"user_results":{"result":{$userExtra"core":{"name":"A","screen_name":"a"}}}}"""
        val tweet = """{"__typename":"Tweet","rest_id":"1",$user,"details":{"created_at_ms":1790000000000,"full_text":"t"}}"""
        val answer = """{"data":{"items":[{"__typename":"TimelineTweet","tweet_results":{"result":$tweet}}]}}"""
        return XPagePayload.posts("<script>(\$R=>\$R[1]={kind:\"Result.Ok\",value:" + JsonPrimitive(answer) + "})</script>").single()
    }

    @Test
    fun `x com's is_blue_verified, its verified_type and its privacy are read`() {
        assertEquals(VerifiedType.BLUE, xcom(""""is_blue_verified":true,""").verified)
        assertEquals(VerifiedType.GOVERNMENT, xcom(""""is_blue_verified":true,"verification":{"verified_type":"Government"},""").verified)
        assertEquals(VerifiedType.NONE, xcom(""""is_blue_verified":false,""").verified)
        assertTrue(xcom(""""privacy":{"protected":true},""").isProtected)
        assertFalse(xcom("").isProtected)
    }

    @Test
    fun `a post stored before the marks existed still reads`() {
        val old = """{"id":"1","authorHandle":"a","authorName":"A","text":"t","publishedAtMillis":1,"permalink":"p"}"""
        val post = Json.decodeFromString(Post.serializer(), old)

        assertEquals(VerifiedType.NONE, post.verified)
        assertFalse(post.isProtected)
    }
}
