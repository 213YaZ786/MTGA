package com.mtga.app.core.media

import com.mtga.app.core.model.MediaItem
import com.mtga.app.core.model.MediaType
import com.mtga.app.core.model.Post
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoMediaDownloaderSelectionTest {

    @Test
    fun `takes only what is newer than the watermark`() {
        val posts = listOf(post("c", 300), post("b", 200), post("a", 100))

        val picked = AutoMediaDownloader.selectFresh(posts, since = 150, max = 10)

        assertEquals(listOf("b", "c"), picked.map { it.id })
    }

    @Test
    fun `oldest first, so a capped pass leaves the newest for next time`() {
        val posts = listOf(post("d", 400), post("c", 300), post("b", 200), post("a", 100))

        val picked = AutoMediaDownloader.selectFresh(posts, since = 0, max = 2)

        assertEquals(listOf("a", "b"), picked.map { it.id })
    }

    @Test
    fun `the next pass continues where the capped one stopped`() {
        val posts = listOf(post("d", 400), post("c", 300), post("b", 200), post("a", 100))

        val first = AutoMediaDownloader.selectFresh(posts, since = 0, max = 2)
        val second = AutoMediaDownloader.selectFresh(posts, since = first.last().publishedAtMillis, max = 2)

        assertEquals(listOf("c", "d"), second.map { it.id })
    }

    @Test
    fun `skips posts with no media`() {
        val posts = listOf(post("b", 200), post("a", 100, media = false))

        assertEquals(listOf("b"), AutoMediaDownloader.selectFresh(posts, 0, 10).map { it.id })
    }

    @Test
    fun `skips a pin, however old it is`() {
        val posts = listOf(post("pin", 50, pinned = true), post("a", 100))

        assertEquals(listOf("a"), AutoMediaDownloader.selectFresh(posts, 0, 10).map { it.id })
    }

    @Test
    fun `nothing new means nothing queued`() {
        val posts = listOf(post("a", 100))

        assertTrue(AutoMediaDownloader.selectFresh(posts, since = 100, max = 10).isEmpty())
    }

    private fun post(
        id: String,
        millis: Long,
        media: Boolean = true,
        pinned: Boolean = false
    ) = Post(
        id = id,
        authorHandle = "someone",
        authorName = "Someone",
        text = "text",
        publishedAtMillis = millis,
        permalink = "https://x.com/someone/status/$id",
        isPinned = pinned,
        media = if (media) {
            listOf(MediaItem("https://p/$id.jpg", "https://p/$id.jpg", MediaType.PHOTO))
        } else {
            emptyList()
        }
    )
}
