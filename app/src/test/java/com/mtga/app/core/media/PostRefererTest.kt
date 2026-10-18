package com.mtga.app.core.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The cases follow Signal-Android's tests of the same kind of check
 * (https://github.com/signalapp/Signal-Android,
 * app/src/test/java/org/thoughtcrime/securesms/util/LinkUtilTest_isValidPreviewUrl.kt
 * and LinkUtilTest_isLegal.java): one list of good addresses, one of bad
 * ones (other hosts, other schemes, hiding places, empty), with the header
 * rules of DownloadManager.Request.addRequestHeader added
 * (https://developer.android.com/reference/android/app/DownloadManager.Request#addRequestHeader(java.lang.String,%20java.lang.String)).
 */
class PostRefererTest {

    private val own = "https://x.com/someone/status/123"

    @Test
    fun `an own https post address is sent as written`() {
        assertEquals(own, PostReferer.forPermalink(own))
        listOf("www.x.com", "mobile.x.com", "twitter.com", "www.twitter.com", "mobile.twitter.com", "m.twitter.com")
            .forEach { host ->
                val url = "https://$host/someone/status/123"
                assertEquals(url, PostReferer.forPermalink(url))
            }
    }

    @Test
    fun `an upper case scheme and host are accepted`() {
        assertEquals("https://X.COM/someone/status/123", PostReferer.forPermalink("HTTPS://X.COM/someone/status/123"))
    }

    @Test
    fun `tracking parameters and the fragment are dropped`() {
        assertEquals(own, PostReferer.forPermalink("$own?utm_source=share&utm_medium=android#top"))
    }

    @Test
    fun `another host sets nothing`() {
        assertNull(PostReferer.forPermalink("https://nitter.net/someone/status/123"))
        assertNull(PostReferer.forPermalink("https://example.org/a"))
        assertNull(PostReferer.forPermalink("https://x.com.example.org/a"))
        assertNull(PostReferer.forPermalink("https://notx.com/a"))
        assertNull(PostReferer.forPermalink("https://api.x.com/a"))
    }

    @Test
    fun `an own host hidden in the user part or the path sets nothing`() {
        assertNull(PostReferer.forPermalink("https://x.com@example.org/a"))
        assertNull(PostReferer.forPermalink("https://example.org/x.com"))
        assertNull(PostReferer.forPermalink("https://example.org\\@x.com/a"))
    }

    @Test
    fun `plain http sets nothing`() {
        assertNull(PostReferer.forPermalink("http://x.com/someone/status/123"))
    }

    @Test
    fun `other schemes set nothing`() {
        assertNull(PostReferer.forPermalink("javascript:alert(1)"))
        assertNull(PostReferer.forPermalink("intent://x.com/#Intent;end"))
        assertNull(PostReferer.forPermalink("file:///data/data/x"))
        assertNull(PostReferer.forPermalink("content://x.com/a"))
    }

    @Test
    fun `empty, blank or missing sets nothing`() {
        assertNull(PostReferer.forPermalink(null))
        assertNull(PostReferer.forPermalink(""))
        assertNull(PostReferer.forPermalink("   "))
        assertNull(PostReferer.forPermalink("https://"))
    }

    @Test
    fun `a line break or a space in the address sets nothing`() {
        assertNull(PostReferer.forPermalink("$own\r\nX-Evil: 1"))
        assertNull(PostReferer.forPermalink("$own x"))
    }

    @Test
    fun `a very long address sets nothing`() {
        assertNull(PostReferer.forPermalink("$own/" + "a".repeat(PostReferer.MAX_LENGTH)))
        assertNull(PostReferer.forPermalink("$own?q=" + "a".repeat(100_000)))
    }

    @Test
    fun `an address just under the cap is kept`() {
        val path = "a".repeat(PostReferer.MAX_LENGTH - "$own/".length)
        assertEquals("$own/$path", PostReferer.forPermalink("$own/$path"))
    }

    @Test
    fun `a redirect wrapper to another host sets nothing`() {
        assertNull(PostReferer.forPermalink("https://www.google.com/url?q=https%3A%2F%2Fexample.org%2Fa"))
    }

    // forPost: the canonical address for a permalink off X's hosts. The good
    // and bad lists follow the same protocol as the cases above (Signal-Android,
    // https://github.com/signalapp/Signal-Android, LinkUtilTest_isValidPreviewUrl.kt),
    // with the forms of XLink.parse's status path (core/link/XLink.kt L34-46).
    private val canonical = "https://x.com/someone/status/123"

    @Test
    fun `a nitter or twstalker permalink gives the canonical x address`() {
        assertEquals(canonical, PostReferer.forPost("https://nitter.net/someone/status/123#m", "someone", "123"))
        assertEquals(canonical, PostReferer.forPost("https://twstalker.com/someone/status/123", "someone", "123"))
    }

    @Test
    fun `a missing, empty or hostile permalink still gives the canonical address`() {
        assertEquals(canonical, PostReferer.forPost(null, "someone", "123"))
        assertEquals(canonical, PostReferer.forPost("", "someone", "123"))
        assertEquals(canonical, PostReferer.forPost("javascript:alert(1)", "someone", "123"))
        assertEquals(canonical, PostReferer.forPost("http://nitter.net/someone/status/123", "someone", "123"))
    }

    @Test
    fun `an own permalink is kept over the canonical one`() {
        val mobile = "https://mobile.twitter.com/someone/status/123"
        assertEquals(mobile, PostReferer.forPost(mobile, "someone", "123"))
    }

    @Test
    fun `a handle not of X form sets nothing`() {
        listOf(null, "", "a".repeat(16), "some one", "some/one", "so.me", "some\r\none", "@someone", "café").forEach {
            assertNull(PostReferer.forPost("https://nitter.net/x/status/123", it, "123"))
        }
        assertEquals("https://x.com/a_1/status/123", PostReferer.forPost(null, "a_1", "123"))
        assertEquals("https://x.com/${"a".repeat(15)}/status/123", PostReferer.forPost(null, "a".repeat(15), "123"))
    }

    @Test
    fun `an id not of X form sets nothing`() {
        listOf(null, "", "12a", "-1", "1.5", "1 2", "123/../x", "1".repeat(21), "١٢٣").forEach {
            assertNull(PostReferer.forPost("https://nitter.net/someone/status/123", "someone", it))
        }
        assertEquals("https://x.com/someone/status/${"9".repeat(20)}", PostReferer.forPost(null, "someone", "9".repeat(20)))
    }

    @Test
    fun `a redirect wrapper to an own post is unwrapped`() {
        assertEquals(own, PostReferer.forPermalink("https://www.google.com/url?q=https%3A%2F%2Fx.com%2Fsomeone%2Fstatus%2F123"))
    }
}
