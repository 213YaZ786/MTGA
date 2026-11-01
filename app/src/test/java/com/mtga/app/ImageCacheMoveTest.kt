package com.mtga.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Folders under JUnit's TemporaryFolder rule
 * (https://junit.org/junit4/javadoc/latest/org/junit/rules/TemporaryFolder.html).
 */
class ImageCacheMoveTest {

    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `the pictures already seen move to the new folder`() {
        val cacheDir = folder.newFolder("cache")
        File(cacheDir, "coil3_disk_cache").mkdirs()
        File(cacheDir, "coil3_disk_cache/entry.1").writeText("picture")
        val target = File(folder.root, "files/coil3_disk_cache").also { it.parentFile.mkdirs() }

        MtgaApplication.moveImageCache(cacheDir, target)

        assertEquals("picture", File(target, "entry.1").readText())
        assertFalse(File(cacheDir, "coil3_disk_cache").exists())
    }

    @Test
    fun `an existing new folder is never replaced`() {
        val cacheDir = folder.newFolder("cache")
        File(cacheDir, "coil3_disk_cache").mkdirs()
        val target = folder.newFolder("files", "coil3_disk_cache")
        File(target, "kept").writeText("new")

        MtgaApplication.moveImageCache(cacheDir, target)

        assertTrue(File(target, "kept").exists())
        assertTrue(File(cacheDir, "coil3_disk_cache").exists())
    }

    @Test
    fun `no old folder means nothing to do`() {
        val target = File(folder.root, "files/coil3_disk_cache")

        MtgaApplication.moveImageCache(folder.newFolder("cache"), target)

        assertFalse(target.exists())
    }
}
