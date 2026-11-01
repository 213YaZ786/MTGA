package com.mtga.app.feature.media

import com.mtga.app.core.media.ManualSaves
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The grid's rows, in the shape of this app's tests of pure parts:
 * https://github.com/213YaZ786/MTGA/blob/main/app/src/test/java/com/mtga/app/core/media/OfflineMediaNameTest.kt
 */
class SavedMediaRowsTest {

    private fun auto(handle: String, post: String, position: Int, path: String, bytes: Long = 10) =
        SavedMediaViewModel.Auto(handle, post, position, path, bytes)

    @Test
    fun `a photo is its own thumbnail, a video uses its still`() {
        val state = SavedMediaViewModel.build(
            auto = listOf(auto("a", "1", 0, "/m/a__1__0.jpg"), auto("a", "2", 0, "/m/a__2__0.mp4")),
            stills = mapOf("2/0" to "/m/a__2__0__still.jpg"),
            manual = emptyList()
        )

        val cells = state.rows.filterIsInstance<SavedCell>().associateBy { it.postId }
        assertEquals("/m/a__1__0.jpg", cells.getValue("1").thumb)
        assertEquals("/m/a__2__0__still.jpg", cells.getValue("2").thumb)
        assertTrue(cells.getValue("2").video)
    }

    @Test
    fun `a video with no still has no thumbnail, no frame is decoded for it`() {
        val state = SavedMediaViewModel.build(
            auto = listOf(auto("a", "2", 0, "/m/a__2__0.mp4")),
            stills = emptyMap(),
            manual = listOf(ManualSaves.Save("3", "a", 0, "content://d/1", video = true))
        )

        assertTrue(state.rows.filterIsInstance<SavedCell>().all { it.thumb == null && it.video })
    }

    @Test
    fun `a photo saved by hand shows from its content address`() {
        val state = SavedMediaViewModel.build(
            auto = emptyList(),
            stills = emptyMap(),
            manual = listOf(ManualSaves.Save("3", "a", 0, "content://d/1", video = false))
        )

        assertEquals("content://d/1", state.rows.filterIsInstance<SavedCell>().single().thumb)
    }

    @Test
    fun `a media in both lists shows once, as the copy of the app`() {
        val state = SavedMediaViewModel.build(
            auto = listOf(auto("a", "1", 0, "/m/a__1__0.jpg")),
            stills = emptyMap(),
            manual = listOf(ManualSaves.Save("1", "a", 0, "content://d/1"))
        )

        assertEquals(1, state.fileCount)
        assertEquals("/m/a__1__0.jpg", state.rows.filterIsInstance<SavedCell>().single().thumb)
        assertEquals(1, state.rows.filterIsInstance<SavedHeader>().single().appFiles)
    }

    @Test
    fun `accounts with the most files first, posts newest first`() {
        val state = SavedMediaViewModel.build(
            auto = listOf(
                auto("few", "9", 0, "/m/few__9__0.jpg"),
                auto("many", "100", 0, "/m/many__100__0.jpg"),
                auto("many", "99", 0, "/m/many__99__0.jpg"),
                auto("many", "1000", 0, "/m/many__1000__0.jpg")
            ),
            stills = emptyMap(),
            manual = emptyList()
        )

        assertEquals(
            listOf("h/many", "p/1000", "c/1000/0", "p/100", "c/100/0", "p/99", "c/99/0", "h/few", "p/9", "c/9/0"),
            state.rows.map { it.key }
        )
    }

    @Test
    fun `a heading counts all files but sizes the copies of the app only`() {
        val state = SavedMediaViewModel.build(
            auto = listOf(auto("a", "1", 0, "/m/a__1__0.jpg", bytes = 500)),
            stills = emptyMap(),
            manual = listOf(ManualSaves.Save("2", "a", 0, "content://d/1"))
        )

        val header = state.rows.filterIsInstance<SavedHeader>().single()
        assertEquals(2, header.files)
        assertEquals(1, header.appFiles)
        assertEquals(500L, header.appBytes)
        assertEquals(500L, state.appBytes)
    }

    // Same shape as the tests above, https://github.com/213YaZ786/MTGA/blob/main/app/src/test/java/com/mtga/app/core/media/OfflineMediaNameTest.kt
    @Test
    fun `a post line counts the files of the post and sizes the app copies`() {
        val state = SavedMediaViewModel.build(
            auto = listOf(auto("a", "1", 0, "/m/a__1__0.jpg", bytes = 300)),
            stills = emptyMap(),
            manual = listOf(ManualSaves.Save("1", "a", 1, "content://d/1"))
        )

        val bar = state.rows.filterIsInstance<SavedPostBar>().single()
        assertEquals(2, bar.files)
        assertEquals(300L, bar.appBytes)
    }

    @Test
    fun `nothing saved gives no rows`() {
        val state = SavedMediaViewModel.build(emptyList(), emptyMap(), emptyList())

        assertTrue(state.rows.isEmpty())
        assertEquals(0, state.fileCount)
    }

    @Test
    fun `thousands of saved videos are one list of small rows`() {
        val manual = (1..10_000).map { ManualSaves.Save("$it", "a", 0, "content://d/$it", video = true) }

        val state = SavedMediaViewModel.build(emptyList(), emptyMap(), manual)

        assertEquals(20_001, state.rows.size)
        assertEquals("p/10000", state.rows[1].key)
        assertEquals("c/10000/0", state.rows[2].key)
    }
}
