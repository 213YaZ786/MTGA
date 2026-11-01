package com.mtga.app.core.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Files under JUnit's TemporaryFolder rule, as SaveOriginTest does
 * (https://junit.org/junit4/javadoc/latest/org/junit/rules/TemporaryFolder.html).
 * The media store itself is not reached: the rebuild is its pure plan.
 */
class ManualSavesTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun saves(file: File = File(folder.root, "manual-saves.json")) = ManualSaves(file)

    private fun save(post: String, position: Int, uri: String, video: Boolean = false) =
        ManualSaves.Save(post, "someone", position, uri, video)

    @Test
    fun `a recorded save is found by its post and position`() {
        val saves = saves()
        saves.record(save("42", 1, "content://media/external/downloads/7", video = true))

        assertEquals("content://media/external/downloads/7", saves.uriFor("42", 1))
        assertNull(saves.uriFor("42", 0))
        assertNull(saves.uriFor("43", 1))
    }

    @Test
    fun `the index survives a restart`() {
        saves().record(save("42", 0, "content://media/external/downloads/7"))

        val again = saves()
        // Nothing is read until load, which is never on the main thread.
        assertNull(again.uriFor("42", 0))
        again.load()

        assertEquals("content://media/external/downloads/7", again.uriFor("42", 0))
    }

    @Test
    fun `the same place in the same post is replaced`() {
        val saves = saves()
        saves.record(save("42", 0, "content://media/external/downloads/7"))
        saves.record(save("42", 0, "content://media/external/downloads/9"))

        assertEquals("content://media/external/downloads/9", saves.uriFor("42", 0))
        assertEquals(1, saves.all().size)
    }

    @Test
    fun `a damaged file gives an empty index, not a crash`() {
        val file = File(folder.root, "manual-saves.json").apply { writeText("{ not json") }
        val saves = saves(file)
        saves.load()

        assertTrue(saves.all().isEmpty())
        assertNull(saves.uriFor("42", 0))
    }

    @Test
    fun `no file means no index`() {
        val saves = saves()
        saves.load()

        assertNull(saves.uriFor("1", 0))
    }

    // Files under JUnit's TemporaryFolder rule, https://junit.org/junit4/javadoc/latest/org/junit/rules/TemporaryFolder.html
    @Test
    fun `deleting a post drops its saves from the index and keeps the others`() {
        val saves = saves()
        saves.record(save("42", 0, "content://x/1"))
        saves.record(save("42", 1, "content://x/2"))
        saves.record(save("43", 0, "content://x/3"))

        saves.removePost("42")

        assertNull(saves.uriFor("42", 0))
        assertNull(saves.uriFor("42", 1))
        assertEquals("content://x/3", saves.uriFor("43", 0))
        val again = saves()
        again.load()
        assertNull(again.uriFor("42", 0))
    }

    @Test
    fun `a change is told to the screens`() {
        val saves = saves()
        var told = 0
        saves.onChange = { told++ }
        saves.record(save("42", 0, "content://x/1"))

        assertEquals(1, told)
    }

    @Test
    fun `the rebuild reads the post from the referer and numbers its saves in order`() {
        val rows = listOf(
            ManualSaves.Row("content://d/1", "https://x.com/someone/status/42", video = false),
            ManualSaves.Row("content://d/2", "https://x.com/someone/status/42", video = true),
            ManualSaves.Row("content://d/3", "https://x.com/other/status/50", video = false)
        )

        val added = ManualSaves.plan(rows, emptyList())

        assertEquals(
            listOf(
                ManualSaves.Save("42", "someone", 0, "content://d/1", false),
                ManualSaves.Save("42", "someone", 1, "content://d/2", true),
                ManualSaves.Save("50", "other", 0, "content://d/3", false)
            ),
            added
        )
    }

    @Test
    fun `a row with no post address is not a save of a post`() {
        val rows = listOf(
            ManualSaves.Row("content://d/1", null, video = false),
            ManualSaves.Row("content://d/2", "https://example.com/someone/status/42", video = false),
            ManualSaves.Row("content://d/3", "https://x.com/someone", video = false)
        )

        assertTrue(ManualSaves.plan(rows, emptyList()).isEmpty())
    }

    @Test
    fun `the rebuild keeps what is already known and does not overwrite a place`() {
        val kept = listOf(save("42", 0, "content://d/1"))
        val rows = listOf(
            ManualSaves.Row("content://d/1", "https://x.com/someone/status/42", video = false),
            ManualSaves.Row("content://d/2", "https://x.com/someone/status/42", video = false)
        )

        val added = ManualSaves.plan(rows, kept)

        assertEquals(listOf(save("42", 1, "content://d/2")), added)
    }

    @Test
    fun `a place already taken by another file is left alone`() {
        val kept = listOf(save("42", 0, "content://d/9"))
        val rows = listOf(ManualSaves.Row("content://d/1", "https://x.com/someone/status/42", video = false))

        assertTrue(ManualSaves.plan(rows, kept).isEmpty())
    }

    @Test
    fun `a referer with tracking and a fragment still names the post`() {
        assertEquals(
            "someone" to "42",
            PostReferer.status("https://twitter.com/someone/status/42?s=20#top")
        )
    }

    @Test
    fun `a referer that is not a status address names no post`() {
        assertNull(PostReferer.status(null))
        assertNull(PostReferer.status(""))
        assertNull(PostReferer.status("http://x.com/someone/status/42"))
        assertNull(PostReferer.status("https://evil.example/someone/status/42"))
        assertNull(PostReferer.status("https://x.com/someone/status/abc"))
        assertNull(PostReferer.status("https://x.com/someone/likes"))
    }
}
