package com.mtga.app.data.html

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The strings are what Nitter's own code writes (AGPL-3.0, zedeus/nitter): getSuspended
 * inside the error panel, quotes escaped by its HTML builder, and renderProtected for the
 * protected header. Test shape: JUnit 4 (https://github.com/junit-team/junit4/wiki/Getting-started).
 * https://github.com/zedeus/nitter/blob/master/src/formatters.nim#getSuspended
 * https://github.com/zedeus/nitter/blob/master/src/views/profile.nim#renderProtected
 */
class HtmlSourceTest {

    private val suspended =
        "<div class=\"error-panel\"><span>User &quot;Jack&quot; has been suspended</span></div>"

    private val protected =
        "<div class=\"timeline-container\"><div class=\"timeline-header timeline-protected\">" +
            "<h2>This account's tweets are protected.</h2>" +
            "<p>Only confirmed followers have access to @jack's tweets.</p></div></div>"

    @Test
    fun `getSuspended writes Nitter's sentence`() {
        assertEquals("User \"jack\" has been suspended", HtmlSource.getSuspended("jack"))
    }

    @Test
    fun `a suspended account is told apart, whatever the case of the handle`() {
        assertEquals("X has suspended this account.", HtmlSource.unavailableReason(suspended, "jack"))
        assertEquals("X has suspended this account.", HtmlSource.unavailableReason(suspended, "JACK"))
    }

    @Test
    fun `the old wording alone no longer matches`() {
        val old = "<div class=\"error-panel\"><span>The user has been suspended</span></div>"
        assertNull(HtmlSource.unavailableReason(old, "jack"))
    }

    @Test
    fun `another account's name does not flag this one`() {
        assertNull(HtmlSource.unavailableReason(suspended, "elon"))
    }

    @Test
    fun `renderProtected header marks a protected account`() {
        assertEquals("This account is protected.", HtmlSource.unavailableReason(protected, "jack"))
    }

    @Test
    fun `a post saying tweets are protected does not flag a healthy account`() {
        val page = "<div class=\"timeline-item \"><div class=\"tweet-content media-body\">" +
            "Why my tweets are protected and yours are not</div></div>"
        assertNull(HtmlSource.unavailableReason(page, "jack"))
    }

    @Test
    fun `the error panel can say protected too`() {
        val panel = "<div class=\"error-panel\"><span>These tweets are protected</span></div>"
        assertEquals("This account is protected.", HtmlSource.unavailableReason(panel, "jack"))
    }

    @Test
    fun `a suspended sentence in a post is not an error`() {
        val page = "<div class=\"timeline-item \"><div class=\"tweet-content media-body\">" +
            "User &quot;jack&quot; has been suspended, they say</div></div>"
        assertNull(HtmlSource.unavailableReason(page, "jack"))
    }
}
