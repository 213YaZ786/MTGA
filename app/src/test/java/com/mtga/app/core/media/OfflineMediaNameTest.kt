package com.mtga.app.core.media

import com.mtga.app.core.model.MediaItem
import com.mtga.app.core.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The names of the saved files, in the shape of AntennaPod's generator test:
 * https://github.com/AntennaPod/AntennaPod/blob/develop/net/download/service-interface/src/test/java/de/danoeh/antennapod/net/download/serviceinterface/FilenameGeneratorTest.java
 */
class OfflineMediaNameTest {

    @Test
    fun `a name goes back to the post it came from`() {
        val name = OfflineMedia.name(
            postId = "2101398137248100790",
            authorHandle = "clashreport",
            index = 0,
            item = video("https://video.twimg.com/amplify_video/2101398/vid/avc1/1920x1080/Qjv.mp4?tag=29")
        )

        assertEquals("2101398137248100790.0.mp4", name)
        assertEquals(
            OfflineMedia.Parsed("clashreport", "2101398137248100790", 0),
            OfflineMedia.parse(name, folder = OfflineMedia.getMediafilePath("clashreport"))
        )
    }

    @Test
    fun `one folder per account whatever the case of the handle`() {
        assertEquals("cpasdeslol_x", OfflineMedia.getMediafilePath("cpasdeslol_X"))
        assertEquals(OfflineMedia.getMediafilePath("SomeOne"), OfflineMedia.getMediafilePath("someone"))
    }

    @Test
    fun `a handle with one underscore survives the round trip`() {
        val folder = OfflineMedia.getMediafilePath("cpasdeslol_X")
        val name = OfflineMedia.name("42", "cpasdeslol_X", 1, photo("https://pbs.twimg.com/media/HSl.jpg"))

        assertEquals("42.1.jpg", name)
        assertEquals(OfflineMedia.Parsed("cpasdeslol_x", "42", 1), OfflineMedia.parse(name, folder))
    }

    @Test
    fun `the query string is not mistaken for an extension`() {
        val name = OfflineMedia.name("42", "a", 0, photo("https://pbs.twimg.com/media/HSl?format=webp&name=orig"))

        assertEquals("42.0.jpg", name)
    }

    @Test
    fun `a video keeps its own extension`() {
        assertEquals(
            "42.2.mp4",
            OfflineMedia.name("42", "a", 2, video("https://video.twimg.com/x/y.mp4"))
        )
    }

    @Test
    fun `a file the app did not write is ignored`() {
        assertNull(OfflineMedia.parse("holiday.jpg", "a"))
        assertNull(OfflineMedia.parse("42.jpg", "a"))
        assertNull(OfflineMedia.parse("42.notanumber.jpg", "a"))
        assertNull(OfflineMedia.parse(".0.jpg", "a"))
        assertNull(OfflineMedia.parse("42.0.other.jpg", "a"))
        assertNull(OfflineMedia.parse("holiday.jpg"))
        assertNull(OfflineMedia.parse("a__42.jpg"))
        assertNull(OfflineMedia.parse("a__42__notanumber.jpg"))
        assertNull(OfflineMedia.parse("__42__0.jpg"))
    }

    @Test
    fun `a name written before the account folders still parses`() {
        assertEquals(
            OfflineMedia.Parsed("clashreport", "2101398137248100790", 0),
            OfflineMedia.parse("clashreport__2101398137248100790__0.mp4")
        )
        assertEquals(
            OfflineMedia.Parsed("cpasdeslol_x", "42", 1),
            OfflineMedia.parse("cpasdeslol_x__42__1.jpg")
        )
    }

    private fun photo(url: String) = MediaItem(url, url, MediaType.PHOTO)

    private fun video(url: String) = MediaItem(url, url, MediaType.VIDEO)
}
