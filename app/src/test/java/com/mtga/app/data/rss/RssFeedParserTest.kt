package com.mtga.app.data.rss

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The description as Nitter writes it (AGPL-3.0, zedeus/nitter, renderRssTweet): the
 * post's text with each newline replaced by "<br>" and a newline, inside a paragraph.
 * Test shape: JUnit 4 (https://github.com/junit-team/junit4/wiki/Getting-started).
 * https://github.com/zedeus/nitter/blob/master/src/views/rss.nimf#renderRssTweet
 */
class RssFeedParserTest {

    private val parser = RssFeedParser()

    @Test
    fun `br and its layout newline read as one line break`() {
        val description = "<p>first line<br>\nsecond line<br>\nthird line</p>"

        assertEquals("first line\nsecond line\nthird line", parser.htmlToPlainText(description))
    }

    @Test
    fun `an empty line in the post stays one empty line`() {
        val description = "<p>one<br>\n<br>\ntwo</p>"

        assertEquals("one\n\ntwo", parser.htmlToPlainText(description))
    }

    @Test
    fun `entities and the picture block are handled as before`() {
        val description = "<p>a &amp; b</p>\n<img src=\"https://nitter.example/pic/x.jpg\" style=\"max-width:250px;\" />"

        assertEquals("a & b", parser.htmlToPlainText(description))
    }
}
