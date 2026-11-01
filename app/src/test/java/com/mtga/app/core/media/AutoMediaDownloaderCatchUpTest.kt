package com.mtga.app.core.media

import com.mtga.app.core.model.MediaItem
import com.mtga.app.core.model.MediaType
import com.mtga.app.core.model.Post
import com.mtga.app.data.settings.AutoDownload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The catch-up pass's pure parts, in the shape of this app's own selection test:
 * https://github.com/213YaZ786/MTGA/blob/main/app/src/test/java/com/mtga/app/core/media/AutoMediaDownloaderSelectionTest.kt
 */
class AutoMediaDownloaderCatchUpTest {

    private val day = 24L * 60 * 60 * 1000
    private val now = 100 * day

    private fun select(
        posts: List<Post>,
        keepDays: Int = 0,
        max: Int = 40,
        have: (String, Int, Boolean) -> Boolean = { _, _, _ -> false },
        asked: (String) -> Boolean = { false }
    ) = AutoMediaDownloader.selectCatchUp(posts, now, keepDays, max, have, asked)

    @Test
    fun `newest posts first`() {
        val posts = listOf(photoPost("a", 10 * day), photoPost("c", 30 * day), photoPost("b", 20 * day))

        assertEquals(listOf("c", "b", "a"), select(posts).map { it.post.id })
    }

    @Test
    fun `stops at the cap, mid post if need be`() {
        val posts = listOf(photoPost("c", 30 * day, photos = 3), photoPost("b", 20 * day))

        val picked = select(posts, max = 2)

        assertEquals(listOf("c" to 0, "c" to 1), picked.map { it.post.id to it.position })
    }

    @Test
    fun `the cap counts the stills too`() {
        val posts = listOf(videoPost("v", 30 * day), photoPost("p", 20 * day))

        val picked = select(posts, max = 2)

        assertEquals(listOf(true, false), picked.map { it.still })
        assertEquals(listOf("v", "v"), picked.map { it.post.id })
    }

    @Test
    fun `a video's still comes before the video`() {
        val picked = select(listOf(videoPost("v", 30 * day)))

        assertEquals(listOf(true, false), picked.map { it.still })
    }

    @Test
    fun `a post older than the retention is not kept, so not saved`() {
        val posts = listOf(photoPost("new", 95 * day), photoPost("old", 80 * day))

        assertEquals(listOf("new"), select(posts, keepDays = 10).map { it.post.id })
    }

    @Test
    fun `zero days of retention keeps everything`() {
        val posts = listOf(photoPost("old", 1 * day))

        assertEquals(listOf("old"), select(posts, keepDays = 0).map { it.post.id })
    }

    @Test
    fun `what is on the phone is not asked for again`() {
        val posts = listOf(photoPost("a", 30 * day, photos = 2))

        val picked = select(posts, have = { _, position, _ -> position == 0 })

        assertEquals(listOf(1), picked.map { it.position })
    }

    @Test
    fun `a video with its still but no file wants the file only`() {
        val picked = select(listOf(videoPost("v", 30 * day)), have = { _, _, still -> still })

        assertEquals(listOf(false), picked.map { it.still })
    }

    @Test
    fun `a file asked for lately is left out`() {
        val post = photoPost("a", 30 * day)
        val name = OfflineMedia.name("a", "someone", 0, post.media[0])

        assertTrue(select(listOf(post), asked = { it == name }).isEmpty())
    }

    @Test
    fun `a playlist is not saved but its still is`() {
        val post = videoPost("v", 30 * day, url = "https://video.twimg.com/amplify/pl/abc.m3u8?tag=14")

        assertEquals(listOf(true), select(listOf(post)).map { it.still })
    }

    @Test
    fun `a video with no still address wants the file only`() {
        val post = videoPost("v", 30 * day, preview = "")

        assertEquals(listOf(false), select(listOf(post)).map { it.still })
    }

    @Test
    fun `posts without media give nothing`() {
        assertTrue(select(listOf(photoPost("a", 30 * day, photos = 0))).isEmpty())
    }

    @Test
    fun `thousands of posts are cut at the cap`() {
        val posts = (1..5_000).map { photoPost("p$it", it * 1_000L) }

        val picked = select(posts, max = AutoMediaDownloader.MAX_CATCH_UP_FILES)

        assertEquals(40, picked.size)
        assertEquals("p5000", picked.first().post.id)
    }

    @Test
    fun `the network rule`() {
        assertFalse(AutoMediaDownloader.allows(AutoDownload.OFF, metered = false))
        assertFalse(AutoMediaDownloader.allows(AutoDownload.OFF, metered = true))
        assertTrue(AutoMediaDownloader.allows(AutoDownload.UNMETERED, metered = false))
        assertFalse(AutoMediaDownloader.allows(AutoDownload.UNMETERED, metered = true))
        assertTrue(AutoMediaDownloader.allows(AutoDownload.ANY, metered = false))
        assertTrue(AutoMediaDownloader.allows(AutoDownload.ANY, metered = true))
    }

    @Test
    fun `an m3u8 address is a playlist, an mp4 is not`() {
        assertTrue(MediaDownloader.isHls("https://video.twimg.com/a/pl/x.m3u8?container=cmaf"))
        assertTrue(MediaDownloader.isHls("https://nitter.example/video/AB/https%3A%2F%2Fv%2Fx.M3U8%3Ftag%3D1"))
        assertFalse(MediaDownloader.isHls("https://video.twimg.com/a/vid/x.mp4?tag=12"))
    }

    private fun photoPost(id: String, millis: Long, photos: Int = 1) = Post(
        id = id,
        authorHandle = "someone",
        authorName = "Someone",
        text = "text",
        publishedAtMillis = millis,
        permalink = "https://x.com/someone/status/$id",
        media = (0 until photos).map { MediaItem("https://p/$id-$it.jpg", "https://p/$id-$it.jpg", MediaType.PHOTO) }
    )

    private fun videoPost(
        id: String,
        millis: Long,
        url: String = "https://v/$id.mp4",
        preview: String = "https://p/$id.jpg"
    ) = Post(
        id = id,
        authorHandle = "someone",
        authorName = "Someone",
        text = "text",
        publishedAtMillis = millis,
        permalink = "https://x.com/someone/status/$id",
        media = listOf(MediaItem(preview, url, MediaType.VIDEO))
    )
}
