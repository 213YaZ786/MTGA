package com.mtga.app.data.xcom

import com.mtga.app.core.network.HostThrottle
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// Test protocol: JUnit 4 (https://github.com/junit-team/junit4), the style of SyndicationCutTest.
// The mediaDetails shape is the one cdn.syndication.twimg.com/tweet-result answered on 2026-11-06
// for a post of four photos: original_info {width, height, focus_rects}, and for a video
// video_info.aspect_ratio [w, h], read as Squawker's TweetVideoMetadata.fromMedia does
// (https://github.com/j-fbriere/squawker/blob/master/lib/tweet/_video.dart#L75-L79).
class SyndicationMediaSizeTest {

    private val client = HttpClient(OkHttp)
    private val source = SyndicationSource(client, HostThrottle())

    @After
    fun close() = client.close()

    private fun payload(extra: String): JsonObject = Json.parseToJsonElement(
        """{"id_str":"1","text":"t","user":{"screen_name":"a","name":"A"},$extra}"""
    ) as JsonObject

    @Test
    fun `a photo takes the size of its original_info`() {
        val post = source.toPost(
            payload(
                """"mediaDetails":[{"type":"photo","media_url_https":"https://pbs.twimg.com/media/a.jpg",
                "original_info":{"focus_rects":[{"h":896,"w":1600,"x":0,"y":502}],"height":2000,"width":1600}}]"""
            )
        )!!

        assertEquals(1600 to 2000, post.media.single().width to post.media.single().height)
    }

    @Test
    fun `a video takes its aspect_ratio when it has no original_info`() {
        val post = source.toPost(
            payload(
                """"mediaDetails":[{"type":"video","media_url_https":"https://pbs.twimg.com/v/t.jpg",
                "video_info":{"aspect_ratio":[16,9],"variants":[]}}]"""
            )
        )!!

        assertEquals(16 to 9, post.media.single().width to post.media.single().height)
    }

    @Test
    fun `a media without a size has none, and a quote's media are read`() {
        val post = source.toPost(
            payload(
                """"mediaDetails":[{"type":"photo","media_url_https":"https://pbs.twimg.com/media/a.jpg"}],
                "quoted_tweet":{"id_str":"2","text":"q","user":{"screen_name":"q","name":"Q"},
                "mediaDetails":[{"type":"photo","media_url_https":"https://pbs.twimg.com/media/q.jpg","original_info":{"width":800,"height":600}}]}"""
            )
        )!!

        assertNull(post.media.single().width)
        assertEquals(800 to 600, post.quoted!!.media.single().width to post.quoted!!.media.single().height)
    }
}
