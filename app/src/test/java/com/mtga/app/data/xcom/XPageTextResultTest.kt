package com.mtga.app.data.xcom

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// Test protocol: JUnit 4 (https://github.com/junit-team/junit4), the style of
// XPagePayloadTest. The pin entry and the note tweet are the shapes that
// Nitter (https://github.com/zedeus/nitter/blob/master/src/parser.nim) and
// FxEmbed (https://github.com/FxEmbed/FxEmbed/blob/main/packages/atmosphere/src/providers/twitter/processor.ts)
// read from the same GraphQL answer.
/**
 * The answer of the profile query as x.com streamed it on 1 November 2026,
 * `kind:"Result.Ok",value:"<json text>"`, trimmed from the real page of
 * @clashreport: its TimelinePinEntry (id 2027804563164573893) and one long
 * post (2116926409788580202, 430 characters with the note tweet's text).
 * Cards, media and the user's profile fields are cut out, the keys kept.
 * Before this the page read gave no post at all, the head fell back to
 * syndication and the pin was lost.
 */
class XPageTextResultTest {

    @Test
    fun `reads the posts of a streamed json text answer`() {
        val posts = XPagePayload.posts(page(LITERAL))

        assertEquals(listOf("2027804563164573893", "2116926409788580202"), posts.map { it.id })
        assertEquals("clashreport", posts[0].authorHandle)
    }

    @Test
    fun `marks the pinned post from the TimelinePinEntry`() {
        val posts = XPagePayload.posts(page(LITERAL))

        assertTrue(posts.single { it.id == "2027804563164573893" }.isPinned)
        assertFalse(posts.single { it.id == "2116926409788580202" }.isPinned)
    }

    @Test
    fun `takes the whole text of a long post and does not mark it cut`() {
        val long = XPagePayload.posts(page(LITERAL)).single { it.id == "2116926409788580202" }

        assertTrue(long.text.endsWith("Source: WaPo"))
        assertFalse(long.textCut)
    }

    @Test
    fun `a note tweet without its text marks the post cut`() {
        val answer = Json.parseToJsonElement(LITERAL).jsonPrimitive.content
        val withoutNote = answer.replace(Regex(",\"text\":\"Pete Hegseth[^\"]*\""), "")
        assertTrue("the fixture must have lost the note text", withoutNote.length < answer.length)

        val long = XPagePayload.posts(page(JsonPrimitive(withoutNote).toString()))
            .single { it.id == "2116926409788580202" }

        assertTrue(long.textCut)
        assertTrue(long.text.length < 430)
    }

    @Test
    fun `a page without a pin entry names no pinned post`() {
        val answer = Json.parseToJsonElement(LITERAL).jsonPrimitive.content
        val unpinned = answer.replace("\"context_type\":\"Pin\"", "\"context_type\":\"Other\"")

        val posts = XPagePayload.posts(page(JsonPrimitive(unpinned).toString()))

        assertEquals(2, posts.size)
        assertTrue(posts.none { it.isPinned })
    }

    private fun page(literal: String): String =
        "<script>(${D}R=>${D}R[24]={kind:1,requestId:2,key:\"k\",result:${D}R[25]={kind:\"Result.Ok\",value:" +
            literal + "}})</script>"

    private companion object {
        const val D = "$"

        /** The real answer, trimmed, as the page writes it: one escaped JSON string. */
        val LITERAL = """"{\"data\":{\"user_result_by_screen_name\":{\"result\":{\"__typename\":\"User\",\"profile_user_originals_timeline\":{\"timeline\":{\"instructions\":[{\"__typename\":\"TimelineClearCache\"},{\"__typename\":\"TimelinePinEntry\",\"entry\":{\"content\":{\"__typename\":\"TimelineTimelineItem\",\"content\":{\"__typename\":\"TimelineTweet\",\"display_type\":\"Tweet\",\"social_context\":{\"__typename\":\"TimelineGeneralContext\",\"context_type\":\"Pin\",\"text\":\"Pinned\"},\"tweet_results\":{\"rest_id\":\"2027804563164573893\",\"result\":{\"__typename\":\"Tweet\",\"core\":{\"user_results\":{\"result\":{\"__typename\":\"User\",\"avatar\":{\"image_url\":\"https://pbs.twimg.com/profile_images/1942576646457868288/gOMYVVRf_normal.jpg\"},\"core\":{\"name\":\"Clash Report\",\"screen_name\":\"clashreport\"},\"rest_id\":\"3247652319\"}}},\"counts\":{\"favorite_count\":717,\"reply_count\":65,\"retweet_count\":76},\"details\":{\"created_at_ms\":1772301257000,\"full_text\":\"Follow Clash Report\\u2019s Telegram channel for instant, all-in-one updates.\\nhttps://t.co/X2qBeHOCkv\"},\"media_entities2\":[],\"rest_id\":\"2027804563164573893\",\"url_entities\":[{\"display_url\":\"t.me/ClashReport\",\"expanded_url\":\"https://t.me/ClashReport\",\"indices\":[72,95],\"url\":\"https://t.co/X2qBeHOCkv\"}],\"views\":{\"count\":\"15217797\"}}}}},\"entry_id\":\"tweet-2027804563164573893\"}},{\"__typename\":\"TimelineAddEntries\",\"entries\":[{\"content\":{\"__typename\":\"TimelineTimelineItem\",\"content\":{\"__typename\":\"TimelineTweet\",\"display_type\":\"Tweet\",\"tweet_results\":{\"rest_id\":\"2116926409788580202\",\"result\":{\"__typename\":\"Tweet\",\"core\":{\"user_results\":{\"result\":{\"__typename\":\"User\",\"avatar\":{\"image_url\":\"https://pbs.twimg.com/profile_images/1942576646457868288/gOMYVVRf_normal.jpg\"},\"core\":{\"name\":\"Clash Report\",\"screen_name\":\"clashreport\"},\"rest_id\":\"3247652319\"}}},\"counts\":{\"favorite_count\":43,\"reply_count\":6,\"retweet_count\":11},\"details\":{\"created_at_ms\":1793549561000,\"full_text\":\"Pete Hegseth plans to remain in office through the end of Trump's term, even if Democrats regain control of Congress and launch investigations against him.\\n\\nHis priorities for the next two years include building a new U.S. military base and adopting advanced weaponry.\\n\\nHegseth is https://t.co/EqISbaptKG\"},\"media_entities2\":[],\"rest_id\":\"2116926409788580202\",\"url_entities\":[],\"views\":{\"count\":\"6673\"},\"note_tweet\":{\"is_expandable\":true,\"note_tweet_results\":{\"result\":{\"__typename\":\"NoteTweet\",\"entity_set\":{\"urls\":[]},\"rest_id\":\"2116926409717231616\",\"text\":\"Pete Hegseth plans to remain in office through the end of Trump's term, even if Democrats regain control of Congress and launch investigations against him.\\n\\nHis priorities for the next two years include building a new U.S. military base and adopting advanced weaponry.\\n\\nHegseth is preparing for intensified congressional scrutiny but has no intention of stepping down, according to people familiar with his thinking.\\n\\nSource: WaPo\"}}}}}}},\"entry_id\":\"tweet-2116926409788580202\"}]},{\"__typename\":\"TimelineTerminateTimeline\",\"direction\":\"TopAndBottom\"}]}}}}}}""""
    }
}
