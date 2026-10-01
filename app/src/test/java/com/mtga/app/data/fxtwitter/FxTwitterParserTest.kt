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

    private fun resource(path: String): String =
        javaClass.classLoader!!.getResource(path)!!.readText()
}
