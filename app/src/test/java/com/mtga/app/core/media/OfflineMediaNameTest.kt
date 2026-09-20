package com.mtga.app.core.media

import com.mtga.app.core.model.MediaItem
import com.mtga.app.core.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OfflineMediaNameTest {

    @Test
    fun `a name goes back to the post it came from`() {
        val name = OfflineMedia.name(
            postId = "2101398137248100790",
            authorHandle = "clashreport",
            index = 0,
            item = video("https://video.twimg.com/amplify_video/2101398/vid/avc1/1920x1080/Qjv.mp4?tag=29")
        )

        assertEquals("clashreport__2101398137248100790__0.mp4", name)
        assertEquals(
            OfflineMedia.Parsed("clashreport", "2101398137248100790", 0),
            OfflineMedia.parse(name)
        )
    }

    @Test
    fun `a handle with one underscore survives the round trip`() {
        val name = OfflineMedia.name("42", "cpasdeslol_X", 1, photo("https://pbs.twimg.com/media/HSl.jpg"))

        assertEquals("cpasdeslol_x__42__1.jpg", name)
        assertEquals(OfflineMedia.Parsed("cpasdeslol_x", "42", 1), OfflineMedia.parse(name))
    }

    @Test
    fun `the query string is not mistaken for an extension`() {
        val name = OfflineMedia.name("42", "a", 0, photo("https://pbs.twimg.com/media/HSl?format=webp&name=orig"))

        assertEquals("a__42__0.jpg", name)
    }

    @Test
    fun `a video keeps its own extension`() {
        assertEquals(
            "a__42__2.mp4",
            OfflineMedia.name("42", "a", 2, video("https://video.twimg.com/x/y.mp4"))
        )
    }

    @Test
    fun `a file the app did not write is ignored`() {
        assertNull(OfflineMedia.parse("holiday.jpg"))
        assertNull(OfflineMedia.parse("a__42.jpg"))
        assertNull(OfflineMedia.parse("a__42__notanumber.jpg"))
        assertNull(OfflineMedia.parse("__42__0.jpg"))
    }

    private fun photo(url: String) = MediaItem(url, url, MediaType.PHOTO)

    private fun video(url: String) = MediaItem(url, url, MediaType.VIDEO)
}
