package com.mtga.app.data.fxtwitter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// Test protocol: JUnit 4 (https://github.com/junit-team/junit4). The answer is
// the shape of GET /2/status/{id} in FxEmbed's specification
// (https://github.com/FxEmbed/FxEmbed/blob/main/docs/specs/fxtwitter-openapi.json).
/** The text of a `/2/status/<id>` answer. */
class FxTwitterStatusTextTest {

    @Test
    fun `reads the whole text of a note tweet`() {
        val body = """{"code":200,"message":"OK","status":{"id":"1","text":"the whole long post","is_note_tweet":true}}"""

        assertEquals("the whole long post", FxTwitterParser.statusText(body))
    }

    @Test
    fun `an answer without a status or text gives none`() {
        assertNull(FxTwitterParser.statusText("""{"code":404,"message":"NOT_FOUND"}"""))
        assertNull(FxTwitterParser.statusText("""{"code":200,"status":{"id":"1","text":"  "}}"""))
        assertNull(FxTwitterParser.statusText("not json"))
    }
}
