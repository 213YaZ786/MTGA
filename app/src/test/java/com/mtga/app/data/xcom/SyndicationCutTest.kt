package com.mtga.app.data.xcom

import com.mtga.app.core.network.HostThrottle
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// Test protocol: JUnit 4 (https://github.com/junit-team/junit4). The payload is
// the shape of cdn.syndication.twimg.com/tweet-result for a long post, whose
// note_tweet holds only an id; FxEmbed's note tweet rule is the marker
// (https://github.com/FxEmbed/FxEmbed/blob/main/packages/atmosphere/src/providers/twitter/processor.ts).
/** Syndication marks a long post it gives cut, and the page completes it. */
class SyndicationCutTest {

    private val client = HttpClient(OkHttp)
    private val source = SyndicationSource(client, HostThrottle())

    @After
    fun close() = client.close()

    @Test
    fun `a note tweet without text marks the post cut`() {
        val post = source.toPost(payload(note = """"note_tweet":{"id":"Tm90ZVR3ZWV0OjIx"},"""))!!

        assertTrue(post.textCut)
        assertEquals(HEAD, post.text)
    }

    @Test
    fun `a note tweet that carries its text is not cut`() {
        val post = source.toPost(
            payload(note = """"note_tweet":{"id":"x","note_tweet_results":{"result":{"text":"The whole long post"}}},""")
        )!!

        assertFalse(post.textCut)
        assertEquals("The whole long post", post.text)
    }

    @Test
    fun `a short post is not marked`() {
        assertFalse(source.toPost(payload(note = ""))!!.textCut)
    }

    @Test
    fun `a quoted long post is marked too`() {
        val quote = """"quoted_tweet":{"id_str":"88","text":"quote head","note_tweet":{"id":"q"},"user":{"screen_name":"q","name":"Q"}},"""
        val post = source.toPost(payload(note = quote))!!

        assertTrue(post.quoted!!.textCut)
        assertFalse(post.textCut)
    }

    @Test
    fun `the page completes a marked post and clears the marker`() {
        val cut = source.toPost(payload(note = """"note_tweet":{"id":"x"},"""))!!
        val page = """{"__typename":"NoteTweet","rest_id":"5","text":"$HEAD and the part after show more."}"""

        val whole = NoteTweetText.complete(listOf(cut), page)

        assertEquals("$HEAD and the part after show more.", whole.single().text)
        assertFalse(whole.single().textCut)
    }

    private fun payload(note: String): JsonObject = Json.parseToJsonElement(
        """{"id_str":"2116926409788580202",$note"text":"$HEAD","user":{"screen_name":"clashreport","name":"Clash Report"}}"""
    ) as JsonObject

    private companion object {
        /** Longer than the 40 characters the page match needs. */
        const val HEAD = "The head of a long post, cut where the embed stops"
    }
}
