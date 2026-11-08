package com.mtga.app.data.xcom

import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Test protocol: JUnit 4 (https://github.com/junit-team/junit4), the page shape of XPageMediaQuoteTest.
// The card is the one Nitter's parsePoll reads, a poll2choice_text_only card with choiceN_label,
// choiceN_count, end_datetime_utc and X's counts_are_final:
// https://github.com/zedeus/nitter/blob/master/src/parser.nim#parsePoll
class XPagePollTest {

    private val user = """"core":{"user_results":{"result":{"core":{"name":"A","screen_name":"a"}}}}"""

    private fun binding(key: String, value: String) = """{"key":"$key","value":{"string_value":"$value","type":"STRING"}}"""

    private fun page(card: String): String {
        val tweet = """{"__typename":"Tweet","rest_id":"1",$user,"details":{"created_at_ms":1790000000000,"full_text":"Vote"},"card":$card}"""
        val answer = """{"data":{"items":[{"__typename":"TimelineTweet","tweet_results":{"result":$tweet}}]}}"""
        return "<script>(\$R=>\$R[1]={kind:\"Result.Ok\",value:" + JsonPrimitive(answer) + "})</script>"
    }

    private fun pollCard(end: String, final: Boolean) = """{"legacy":{"name":"poll3choice_text_only","binding_values":[
        ${binding("choice1_label", "Yes")},${binding("choice1_count", "60")},
        ${binding("choice2_label", "No")},${binding("choice2_count", "30")},
        ${binding("choice3_label", "Maybe")},${binding("choice3_count", "10")},
        ${binding("end_datetime_utc", end)},
        {"key":"counts_are_final","value":{"boolean_value":$final,"type":"BOOLEAN"}}]}}"""

    @Test
    fun `a finished poll card gives its options, percentages, leader, votes and final status`() {
        val post = XPagePayload.posts(page(pollCard("2020-01-01T00:00:00Z", true))).single()

        val poll = post.poll!!
        assertEquals(listOf("Yes", "No", "Maybe"), poll.options.map { it.label })
        assertEquals(listOf(60, 30, 10), poll.options.map { it.percent })
        assertEquals(listOf(true, false, false), poll.options.map { it.leader })
        assertEquals(100L, poll.votes)
        assertEquals("Final results", poll.status)
        assertNull(post.card)
    }

    @Test
    fun `a poll that ends later says how long is left`() {
        val poll = XPagePayload.posts(page(pollCard("2999-01-01T00:00:00Z", false))).single().poll!!

        assertTrue(poll.status!!.endsWith(" days"))
    }

    @Test
    fun `counts_are_final ends a poll before its time`() {
        val poll = XPagePayload.posts(page(pollCard("2999-01-01T00:00:00Z", true))).single().poll!!

        assertEquals("Final results", poll.status)
    }

    @Test
    fun `a link card is still a card and not a poll`() {
        val card = """{"legacy":{"name":"summary","binding_values":[${binding("title", "A page")}]}}"""
        val post = XPagePayload.posts(page(card)).single()

        assertNull(post.poll)
        assertEquals("A page", post.card?.title)
    }
}
