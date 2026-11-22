package com.mtga.app.ui.component

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The banner's choice of label and age (point 22), from widths: the one line of a 411 dp phone, 267 dp left between the
 * two action slots, the pill 96 dp of icons and padding plus 8 dp a character, the age 7 dp a character, both doubled
 * at 200 % text. The forms and the cuts are those of FeedBanner.kt, ported from AOSP's DateUtils
 * https://github.com/aosp-mirror/platform_frameworks_base/blob/main/core/java/android/text/format/DateUtils.java#getRelativeTimeSpanString
 */
class FeedBannerTest {

    private val room = 267
    private val gap = 12

    private fun choose(label: String, age: String, scale: Int) = chooseBanner(
        label = label,
        subtitle = ageForms(age),
        maxWidth = room,
        gap = gap,
        centreWidth = { 96 + 8 * scale * it.length },
        subtitleWidth = { 7 * scale * it.length }
    )

    @Test
    fun `all and 3d at normal text keep the longest age`() {
        assertEquals(BannerChoice("All", "Updated 3d ago"), choose("All", "3d", scale = 1))
    }

    @Test
    fun `all and 3d at 200 percent shorten the age first`() {
        assertEquals(BannerChoice("All", "3d ago"), choose("All", "3d", scale = 2))
    }

    @Test
    fun `a long label is kept to 12 characters and an ellipsis while the age shrinks`() {
        val choice = chooseBanner(
            label = "Journalists and newsrooms",
            subtitle = ageForms("3d"),
            maxWidth = 230,
            gap = gap,
            centreWidth = { 96 + 8 * it.length },
            subtitleWidth = { 7 * it.length }
        )
        assertEquals(BannerChoice("Journalists…", "3d"), choice)
    }

    @Test
    fun `the label is cut only when the shortest age is not enough`() {
        assertEquals(BannerChoice("Journal…", "3d"), choose("Journalists and newsrooms", "3d", scale = 2))
    }

    @Test
    fun `nothing fits keeps the shortest age and the label at 3 characters`() {
        val choice = chooseBanner("Journalists", ageForms("3d"), 10, gap, { it.length }, { it.length })
        assertEquals(BannerChoice("Jou…", "3d"), choice)
    }

    @Test
    fun `no age leaves the label alone`() {
        assertEquals(BannerChoice("main", null), chooseBanner("main", emptyList(), room, gap, { 100 }, { 0 }))
    }

    @Test
    fun `the age in forms of one meaning`() {
        assertEquals(listOf("Updated 3d ago", "3d ago", "3d"), ageForms("3d"))
        assertEquals(listOf("Updated just now", "just now", "now"), ageForms("now"))
        assertEquals(listOf("Updated just now", "just now", "now"), ageForms(""))
    }

    @Test
    fun `a label of 3 to 12 characters is kept, a longer one ends with an ellipsis`() {
        assertEquals(listOf("All"), labelCuts("All"))
        assertEquals(listOf("main", "mai…"), labelCuts("main"))
        assertEquals("Journalists…", labelCuts("Journalists and newsrooms").first())
        assertEquals("Jou…", labelCuts("Journalists and newsrooms").last())
    }
}
