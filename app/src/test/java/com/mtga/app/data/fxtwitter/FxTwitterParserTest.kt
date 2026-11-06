package com.mtga.app.data.fxtwitter

import com.mtga.app.core.model.MediaType
import com.mtga.app.core.model.PostKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The fixtures keep the exact shape of FxTwitter's answers of 1 October 2026
 * (a timeline page of clashreport and elonmusk posts, a conversation), with
 * neutral text in place of the real posts.
 */
class FxTwitterParserTest {

    private val timeline = resource("fxtwitter/timeline.json")
    private val conversation = resource("fxtwitter/conversation.json")

    @Test
    fun `reads every post of a page and its cursor`() {
        val page = FxTwitterParser.timeline(timeline)!!

        assertEquals(
            listOf("2105631682481041591", "2105628450220363959", "2105372581725356099", "2105543992637100269"),
            page.posts.map { it.id }
        )
        assertEquals("DAAHCgABHTi0lwI__-oLAAIAAAATMjEwNTYwNjc2NTkzNzM3NzYyMwgAAwAAAAIAAA", page.bottomCursor)
        assertEquals(1_790_856_688_000L, page.posts[0].publishedAtMillis)
    }

    @Test
    fun `keeps a long text whole and its picture in two sizes`() {
        val post = FxTwitterParser.timeline(timeline)!!.posts[0]

        assertTrue(post.text.length > 280)
        val photo = post.media.single()
        assertEquals(MediaType.PHOTO, photo.type)
        assertEquals("https://pbs.twimg.com/media/HTi0jsgW4AATctB.jpg", photo.previewUrl)
        assertEquals("https://pbs.twimg.com/media/HTi0jsgW4AATctB.jpg?name=orig", photo.downloadUrl)
    }

    @Test
    fun `takes the best mp4 of a video and the typed link`() {
        val post = FxTwitterParser.timeline(timeline)!!.posts[1]

        val video = post.media.single()
        assertEquals(MediaType.VIDEO, video.type)
        assertTrue(video.downloadUrl.endsWith(".mp4?tag=29"))
        assertTrue(video.previewUrl.contains("thumb"))
        assertEquals(listOf("https://x.com/DamienObrador/status/2105576505254285448/video/1"), post.links)
    }

    @Test
    fun `a repost is the original post marked with who reposted it`() {
        val post = FxTwitterParser.timeline(timeline)!!.posts[2]

        assertEquals(PostKind.REPOST, post.kind)
        assertEquals("0xGenAi", post.authorHandle)
        assertEquals("elonmusk", post.relatedHandle)
    }

    @Test
    fun `a quote carries the quoted post`() {
        val post = FxTwitterParser.timeline(timeline)!!.posts[3]

        assertEquals(PostKind.QUOTE, post.kind)
        assertEquals("The quoted post.", post.quoted?.text)
    }

    @Test
    fun `the profile comes from the account's own posts`() {
        val profile = FxTwitterParser.profile(timeline, "clashreport")

        assertEquals("Clash Report", profile?.name)
        assertNotNull(profile?.avatarUrl)
    }

    @Test
    fun `a conversation splits the thread around the post and lists replies`() {
        val read = FxTwitterParser.conversation(conversation, "api.fxtwitter.com")!!

        assertEquals("2105144998522171565", read.main?.id)
        assertEquals(listOf("2105139959720014101"), read.ancestors.map { it.id })
        assertTrue(read.continuation.isEmpty())
        assertEquals(3, read.replies.size)
        assertEquals(PostKind.REPLY, read.main?.kind)
    }

    @Test
    fun `an answer that is not a timeline gives nothing`() {
        assertNull(FxTwitterParser.timeline("""{"code":404,"message":"Not found"}"""))
        assertNull(FxTwitterParser.timeline("<html>"))
        assertEquals(0, FxTwitterParser.timeline("""{"code":404,"results":[],"cursor":{"top":null,"bottom":null}}""")!!.posts.size)
    }

    // Shapes of FxEmbed's API specification (docs/specs/fxtwitter-openapi.json): photo and video
    // carry width and height, a quote is a status or a tombstone.
    // https://github.com/FxEmbed/FxEmbed/blob/main/packages/atmosphere/src/providers/twitter/processor.ts#L198-L205
    private fun page(extra: String): String =
        """{"results":[{"id":"1","author":{"screen_name":"a","name":"A"},"text":"t","created_timestamp":1790000000,$extra}],"cursor":{"bottom":null}}"""

    @Test
    fun `photos and videos keep their width and height`() {
        val post = FxTwitterParser.timeline(
            page(
                """"media":{"all":[
                {"type":"photo","url":"https://pbs.twimg.com/media/x.jpg?name=orig","width":1600,"height":2000},
                {"type":"video","url":"https://video.twimg.com/v.mp4","thumbnail_url":"https://pbs.twimg.com/t.jpg","width":1280,"height":720,"duration":12.0,"formats":[]}
                ]}"""
            )
        )!!.posts.single()

        assertEquals(1600 to 2000, post.media[0].width to post.media[0].height)
        assertEquals(1280 to 720, post.media[1].width to post.media[1].height)
    }

    @Test
    fun `a media without a size has none`() {
        val post = FxTwitterParser.timeline(
            page(""""media":{"all":[{"type":"photo","url":"https://pbs.twimg.com/media/x.jpg"}]}""")
        )!!.posts.single()

        assertNull(post.media.single().width)
        assertNull(post.media.single().height)
    }

    @Test
    fun `a quote carries its media`() {
        val post = FxTwitterParser.timeline(
            page(
                """"quote":{"id":"9","url":"https://x.com/nasa/status/9","text":"q","author":{"screen_name":"nasa","name":"NASA"},
                "media":{"all":[{"type":"photo","url":"https://pbs.twimg.com/media/q1.jpg","width":800,"height":600},
                {"type":"photo","url":"https://pbs.twimg.com/media/q2.jpg","width":800,"height":600}]}}"""
            )
        )!!.posts.single()

        val quote = post.quoted!!
        assertEquals(listOf("https://pbs.twimg.com/media/q1.jpg", "https://pbs.twimg.com/media/q2.jpg"), quote.media.map { it.previewUrl })
        assertEquals(800 to 600, quote.media[0].width to quote.media[0].height)
        assertNull(quote.unavailable)
    }

    @Test
    fun `a tombstone quote is kept as an unavailable quote with FxTwitter's message`() {
        val post = FxTwitterParser.timeline(
            page(""""quote":{"type":"tombstone","provider":"twitter","reason":"deleted","message":"This post is deleted","id":"9"}""")
        )!!.posts.single()

        assertEquals(PostKind.QUOTE, post.kind)
        assertEquals("This post is deleted", post.quoted?.unavailable)
        assertEquals("", post.quoted?.text)
    }

    @Test
    fun `a tombstone without a message takes the words of its reason`() {
        fun message(reason: String) = FxTwitterParser.timeline(
            page(""""quote":{"type":"tombstone","provider":"twitter","reason":"$reason"}""")
        )!!.posts.single().quoted?.unavailable

        assertEquals("This post is from a suspended account", message("suspended"))
        assertEquals("This post is from a private account", message("private"))
        assertEquals("This post is unavailable", message("unavailable"))
    }

    private fun resource(path: String): String =
        javaClass.classLoader!!.getResource(path)!!.readText()
}
