package com.mtga.app.core.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// Test protocol: JUnit 4 (https://github.com/junit-team/junit4/wiki/Getting-started), the style of
// the parser tests. A cache written before the fields existed has to read, and a tombstone must
// never replace a quote that was read.
class QuotedPostMediaTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `a stored quote and media without the new fields still read`() {
        val quote = json.decodeFromString<QuotedPost>("""{"handle":"h","name":"n","text":"t","permalink":"p"}""")
        val media = json.decodeFromString<MediaItem>("""{"previewUrl":"a","downloadUrl":"b","type":"PHOTO"}""")

        assertEquals(emptyList<MediaItem>(), quote.media)
        assertNull(quote.unavailable)
        assertNull(media.width)
        assertNull(media.height)
    }

    private val read = QuotedPost("h", "n", "text", "https://x.com/h/status/9")
    private val gone = QuotedPost("", "", "", "", unavailable = "This post is deleted")

    @Test
    fun `a tombstone does not replace a quote that was read`() {
        assertEquals(read, read.mergedWith(gone))
    }

    @Test
    fun `a quote read replaces a tombstone`() {
        assertEquals(read, gone.mergedWith(read))
    }

    @Test
    fun `media are taken from the fresh quote when the stored one has none`() {
        val photo = MediaItem("a", "b", MediaType.PHOTO, width = 4, height = 3)

        assertEquals(listOf(photo), read.mergedWith(read.copy(media = listOf(photo))).media)
    }

    @Test
    fun `a stored post takes the sizes of the same media seen from a richer source`() {
        val stored = Post(
            id = "1", authorHandle = "a", authorName = "A", text = "t", publishedAtMillis = 1L, permalink = "p",
            media = listOf(MediaItem("a", "b", MediaType.PHOTO))
        )
        val fresh = stored.copy(media = listOf(MediaItem("a", "b", MediaType.PHOTO, width = 1600, height = 2000)))

        val merged = stored.mergedWith(fresh).media.single()
        assertEquals(1600 to 2000, merged.width to merged.height)
    }
}
