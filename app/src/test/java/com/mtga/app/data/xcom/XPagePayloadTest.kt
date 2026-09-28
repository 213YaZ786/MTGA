package com.mtga.app.data.xcom

import com.mtga.app.core.model.MediaType
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The fixture keeps the exact shape of a logged out x.com profile page of late
 * September 2026, Seroval notation included, with neutral text in place of the
 * real posts.
 */
class XPagePayloadTest {

    @Test
    fun `reads every post of the streamed timeline`() {
        val posts = XPagePayload.posts(PAGE)

        assertEquals(listOf("1001", "1002", "1003"), posts.map { it.id })
    }

    @Test
    fun `takes the whole text of a long post`() {
        val long = XPagePayload.posts(PAGE).single { it.id == "1002" }

        assertTrue(long.text.endsWith("and this is the part after show more."))
        // The trailing link only points at the video, which is shown anyway.
        assertFalse(long.text.contains("t.co"))
    }

    @Test
    fun `picks the best mp4 and keeps the duration`() {
        val video = XPagePayload.posts(PAGE).single { it.id == "1002" }.media.single()

        assertEquals(MediaType.VIDEO, video.type)
        assertEquals("https://video.twimg.com/v/1280x720/high.mp4?tag=29", video.downloadUrl)
        assertEquals("1:51", video.durationLabel)
    }

    @Test
    fun `marks the pinned post and reads its card`() {
        val pinned = XPagePayload.posts(PAGE).single { it.id == "1001" }

        assertTrue(pinned.isPinned)
        assertEquals("Example channel", pinned.card?.title)
        assertEquals("https://example.org/channel", pinned.card?.url)
        // A typed link stays in the text, shown the way x.com shows it.
        assertTrue(pinned.text.endsWith("example.org/channel"))
        assertEquals(listOf("https://example.org/channel"), pinned.links)
    }

    @Test
    fun `reads author, date and counts`() {
        val post = XPagePayload.posts(PAGE).single { it.id == "1003" }

        assertEquals("example", post.authorHandle)
        assertEquals("Example Account", post.authorName)
        assertEquals(1_790_577_458_000, post.publishedAtMillis)
        assertEquals(8, post.stats?.replies)
        assertEquals(11, post.stats?.reposts)
        assertEquals(44, post.stats?.likes)
        assertEquals(6965, post.stats?.views)
        assertEquals(MediaType.PHOTO, post.media.single().type)
        assertNull(post.card)
    }

    @Test
    fun `finds nothing on a page without the timeline`() {
        assertEquals(emptyList<Any>(), XPagePayload.posts("<html><body>nothing here</body></html>"))
    }

    @Test
    fun `converts seroval notation to json`() {
        val json = JsLiteral.toJson(
            """${'$'}R[1]={a:!0,b:!1,c:void 0,d:${'$'}R[7],e:"x\x3Cy",f:[1,-2.5],g:12n}"""
        )

        // Compared as parsed values, so the test does not depend on how the
        // escape for < is spelled on either side.
        assertEquals(
            Json.parseToJsonElement("{\"a\":true,\"b\":false,\"c\":null,\"d\":null,\"e\":\"x<y\",\"f\":[1,-2.5],\"g\":12}"),
            Json.parseToJsonElement(json)
        )
    }

    private companion object {
        private val D = '$'

        private fun user() = """core:${D}R[9]={user_results:${D}R[10]={result:${D}R[11]={__typename:"User",
            avatar:${D}R[12]={image_url:"https://pbs.twimg.com/profile_images/1/a_normal.jpg"},
            core:${D}R[13]={name:"Example Account",screen_name:"example"},rest_id:"42"}}}"""

        val PAGE = """
<script nonce="n">(${D}R=>${D}R[43].next(${D}R[45]={kind:"GraphQLRequestStream.Completed",requestId:1,
key:"RUXs{\"count\":20,\"screenName\":\"example\"}",result:${D}R[46]={kind:"Result.Ok",value:${D}R[47]={data:${D}R[48]={
user_result_by_screen_name:${D}R[49]={result:${D}R[50]={__typename:"User",profile_user_originals_timeline:${D}R[51]={
timeline:${D}R[52]={instructions:${D}R[53]=[${D}R[54]={__typename:"TimelineClearCache"},
${D}R[55]={__typename:"TimelinePinEntry",entry:${D}R[56]={content:${D}R[57]={__typename:"TimelineTimelineItem",
content:${D}R[59]={__typename:"TimelineTweet",social_context:${D}R[60]={__typename:"TimelineGeneralContext",context_type:"Pin",text:"Pinned"},
tweet_results:${D}R[61]={rest_id:"1001",result:${D}R[62]={__typename:"Tweet",
card:${D}R[63]={legacy:${D}R[64]={binding_values:${D}R[65]=[
${D}R[66]={key:"title",value:${D}R[67]={string_value:"Example channel",type:"STRING"}},
${D}R[68]={key:"vanity_url",value:${D}R[69]={string_value:"example.org",type:"STRING"}},
${D}R[70]={key:"thumbnail_image_large",value:${D}R[71]={image_value:${D}R[72]={height:320,url:"https://pbs.twimg.com/card_img/1/x",width:320},type:"IMAGE"}},
${D}R[73]={key:"card_url",value:${D}R[74]={string_value:"https://t.co/card1",type:"STRING"}}],name:"summary",url:"https://t.co/card1"}},
${user()},
counts:${D}R[119]={favorite_count:649,reply_count:60,retweet_count:66},
details:${D}R[120]={created_at_ms:1772301257000,full_text:"Follow the channel for updates.\nhttps://t.co/card1"},
media_entities2:${D}R[130]=[],rest_id:"1001",
url_entities:${D}R[132]=[${D}R[133]={display_url:"example.org/channel",expanded_url:"https://example.org/channel",indices:${D}R[134]=[32,55],url:"https://t.co/card1"}],
views:${D}R[135]={count:"13944434"}}}}},entry_id:"tweet-1001"}},
${D}R[136]={__typename:"TimelineAddEntries",entries:${D}R[137]=[
${D}R[138]={content:${D}R[139]={__typename:"TimelineTimelineItem",content:${D}R[141]={__typename:"TimelineTweet",display_type:"Tweet",
tweet_results:${D}R[142]={rest_id:"1002",result:${D}R[143]={__typename:"Tweet",card:${D}R[144]={id:"Q2FyZA=="},
${user()},
counts:${D}R[156]={favorite_count:16,reply_count:0,retweet_count:4},
details:${D}R[157]={created_at_ms:1790579739000,full_text:"A long post, cut where the page shows its button"},
media_entities2:${D}R[167]=[${D}R[168]={expanded_url:"https://x.com/other/status/9/video/1",id_str:"77",
media_url_https:"https://pbs.twimg.com/amplify_video_thumb/77/img/a.jpg",type:"video",
video_info:${D}R[182]={duration_millis:111757,variants:${D}R[183]=[
${D}R[184]={content_type:"application/x-mpegURL",url:"https://video.twimg.com/v/pl/list.m3u8"},
${D}R[185]={bitrate:256000,content_type:"video/mp4",url:"https://video.twimg.com/v/480x270/low.mp4?tag=29"},
${D}R[187]={bitrate:2176000,content_type:"video/mp4",url:"https://video.twimg.com/v/1280x720/high.mp4?tag=29"}]}}],
note_tweet:${D}R[189]={is_expandable:!0,note_tweet_results:${D}R[190]={result:${D}R[191]={__typename:"NoteTweet",
entity_set:${D}R[192]={hashtags:${D}R[193]=[],urls:${D}R[196]=[${D}R[197]={display_url:"x.com/other/stat…",
expanded_url:"https://x.com/other/status/9/video/1",indices:${D}R[198]=[80,103],url:"https://t.co/vid1"}],user_mentions:${D}R[199]=[]},
rest_id:"5",text:"A long post, cut where the page shows its button, and this is the part after show more.\nhttps://t.co/vid1"}}},
rest_id:"1002",url_entities:${D}R[200]=[],views:${D}R[201]={count:"2677"}}}}},entry_id:"tweet-1002"},
${D}R[311]={content:${D}R[312]={__typename:"TimelineTimelineItem",content:${D}R[314]={__typename:"TimelineTweet",display_type:"Tweet",
tweet_results:${D}R[315]={rest_id:"1003",result:${D}R[316]={__typename:"Tweet",
${user()},
counts:${D}R[328]={favorite_count:44,reply_count:8,retweet_count:11},
details:${D}R[329]={created_at_ms:1790577458000,full_text:"A short post with a picture. https://t.co/pic1"},
media_entities2:${D}R[339]=[${D}R[340]={expanded_url:"https://x.com/example/status/1003/photo/1",
media_url_https:"https://pbs.twimg.com/media/abc.jpg",type:"photo"}],
rest_id:"1003",url_entities:${D}R[354]=[],views:${D}R[355]={count:"6965"}}}}},entry_id:"tweet-1003"}]},
${D}R[356]={__typename:"TimelineTerminateTimeline",direction:"TopAndBottom"}]}}}}}}}}))(${D}R["tsr"]);document.currentScript.remove()</script>
        """.trimIndent()
    }
}
