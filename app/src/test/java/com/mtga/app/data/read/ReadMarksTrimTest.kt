package com.mtga.app.data.read

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadMarksTrimTest {

    @Test
    fun `a set under the limit is left alone`() {
        val ids = setOf("3", "1", "2")

        assertEquals(ids, ReadMarks.trim(ids, max = 10))
    }

    @Test
    fun `the oldest ids go first`() {
        // Post ids are snowflakes, so the lowest is the oldest.
        val ids = setOf("100", "300", "200", "400")

        assertEquals(setOf("400", "300"), ReadMarks.trim(ids, max = 2))
    }

    @Test
    fun `trimming to the exact size changes nothing`() {
        val ids = setOf("1", "2")

        assertEquals(ids, ReadMarks.trim(ids, max = 2))
    }

    @Test
    fun `the newest id always survives`() {
        val ids = (1..50).map { it.toString().padStart(3, '0') }.toSet()

        assertTrue("050" in ReadMarks.trim(ids, max = 5))
    }
}
