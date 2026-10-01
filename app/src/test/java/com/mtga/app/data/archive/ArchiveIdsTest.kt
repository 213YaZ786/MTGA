package com.mtga.app.data.archive

import com.mtga.app.core.model.ArchiveCursor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArchiveIdsTest {

    @Test
    fun `ids come out of the archive's rows and search links, this account only`() {
        val cdx = """[["original"],
            ["https://twitter.com/cpasdeslol_X/status/2104586602668331420"],
            ["http://x.com/CPASDESLOL_X/status/2104307307441967513?s=20"],
            ["https://x.com/someone_else/status/2104000000000000000"],
            ["https://x.com/cpasdeslol_X/status/2103999002752504141/photo/1"]]"""
        assertEquals(
            setOf(2104586602668331420, 2104307307441967513, 2103999002752504141),
            ArchiveIds.fromCdx(cdx, "cpasdeslol_X")
        )
        val ddg = """<a class="result__url" href="https://x.com/cpasdeslol_X/status/2102000000000000000">x.com/cpasdeslol_X/status/2102000000000000000</a>"""
        assertEquals(setOf(2102000000000000000), ArchiveIds.fromLinks(ddg, "cpasdeslol_X"))
    }

    @Test
    fun `links written for an address bar count, other accounts and short numbers do not`() {
        val ddg = """<a href="//duckduckgo.com/l/?uddg=https%3A%2F%2Fx.com%2Fclashreport%2Fstatus%2F2105626693670961348&amp;rut=1">""" +
            """x.com/notclashreport/status/2105000000000000001 x.com/clashreport/statuses/2104000000000000000 x.com/clashreport/status/12345"""
        assertEquals(setOf(2105626693670961348, 2104000000000000000), ArchiveIds.fromLinks(ddg, "ClashReport"))
    }

    @Test
    fun `impossible ids are dropped and the rest is newest first`() {
        val now = 1_790_700_000_000L // late September 2026
        val ids = setOf(2104586602668331420, 9_000_000_000_000_000_000L, 2103999002752504141, 20L)
        assertEquals(listOf(2104586602668331420, 2103999002752504141, 20L), ArchiveIds.plausible(ids, now))
    }

    @Test
    fun `the archive cursor reads back`() {
        assertEquals(2103999002752504141, ArchiveCursor.parse(ArchiveCursor.of(2103999002752504141)))
        assertNull(ArchiveCursor.parse("search|1|"))
        assertNull(ArchiveCursor.parse("DAABCgAB"))
    }
}
