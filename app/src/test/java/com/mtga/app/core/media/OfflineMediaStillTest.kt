package com.mtga.app.core.media

import com.mtga.app.core.model.MediaItem
import com.mtga.app.core.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The still's name, in the shape of this app's own name test:
 * https://github.com/213YaZ786/MTGA/blob/main/app/src/test/java/com/mtga/app/core/media/OfflineMediaNameTest.kt
 */
class OfflineMediaStillTest {

    private val video = MediaItem("https://pbs.twimg.com/ext_tw_video_thumb/1/pu/img/abc.jpg", "https://v/x.mp4", MediaType.VIDEO)

    @Test
    fun `a still goes back to its post and position`() {
        val name = OfflineMedia.stillName("42", "SomeOne", 1, video)

        assertEquals("42.1.still.jpg", name)
        assertEquals(
            OfflineMedia.Parsed("someone", "42", 1, still = true),
            OfflineMedia.parse(name, OfflineMedia.getMediafilePath("SomeOne"))
        )
    }

    @Test
    fun `the still and the video of one media have different names`() {
        assertEquals("42.1.mp4", OfflineMedia.name("42", "someone", 1, video))
        assertEquals(false, OfflineMedia.parse("42.1.mp4", "someone")?.still)
    }

    @Test
    fun `a third part that is not still is no file of ours`() {
        assertNull(OfflineMedia.parse("42.1.other.jpg", "someone"))
        assertNull(OfflineMedia.parse("42.1.still.extra.jpg", "someone"))
    }

    @Test
    fun `a still written before the account folders still parses`() {
        assertEquals(
            OfflineMedia.Parsed("someone", "42", 1, still = true),
            OfflineMedia.parse("someone__42__1__still.jpg")
        )
        assertNull(OfflineMedia.parse("someone__42__1__other.jpg"))
        assertNull(OfflineMedia.parse("a__b__c__d__still.jpg"))
    }
}
