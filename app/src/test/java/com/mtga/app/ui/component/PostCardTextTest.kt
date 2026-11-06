package com.mtga.app.ui.component

import com.mtga.app.core.model.Post
import com.mtga.app.core.model.PostKind
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * contextLine follows Nitter's renderTweet condition, getShortTime its formatters.nim
 * patterns (AGPL-3.0, zedeus/nitter). compactCount's ICU formatter does not exist on the
 * JVM, so only what holds there is asserted: under 1000 written as it is, and the number
 * format used from 1000. Test shape: JUnit 4
 * (https://github.com/junit-team/junit4/wiki/Getting-started).
 * https://github.com/zedeus/nitter/blob/master/src/views/tweet.nim#renderTweet
 * https://github.com/zedeus/nitter/blob/master/src/formatters.nim#getShortTime
 */
class PostCardTextTest {

    private lateinit var saved: Locale

    @Before
    fun english() {
        saved = Locale.getDefault()
        Locale.setDefault(Locale.ENGLISH)
    }

    @After
    fun restore() = Locale.setDefault(saved)

    private fun post(kind: PostKind, related: String?, pinned: Boolean = false) = Post(
        id = "1", authorHandle = "Alice", authorName = "Alice", text = "t",
        publishedAtMillis = 1L, permalink = "https://x.com/Alice/status/1",
        kind = kind, relatedHandle = related, isPinned = pinned
    )

    @Test
    fun `a reply to another account says so`() {
        assertEquals("Replying to @bob", post(PostKind.REPLY, "bob").contextLine())
    }

    @Test
    fun `a reply to the author's own post shows no line, case blind`() {
        assertNull(post(PostKind.REPLY, "alice").contextLine())
        assertNull(post(PostKind.REPLY, "ALICE").contextLine())
    }

    @Test
    fun `a reply with no name, a repost and a pin keep their lines`() {
        assertEquals("Reply", post(PostKind.REPLY, null).contextLine())
        assertEquals("Reposted by bob", post(PostKind.REPOST, "bob").contextLine())
        assertEquals("Pinned", post(PostKind.REPLY, "alice", pinned = true).contextLine())
    }

    private val now = ZonedDateTime.of(2026, 11, 6, 12, 0, 0, 0, ZoneOffset.UTC)

    @Test
    fun `an old post of another year gives day month year`() {
        assertEquals("5 Mar 2025", getShortTime(ZonedDateTime.of(2025, 3, 5, 9, 0, 0, 0, ZoneOffset.UTC), now))
    }

    @Test
    fun `a post of this year a day or more old gives month day`() {
        assertEquals("Aug 3", getShortTime(ZonedDateTime.of(2026, 8, 3, 9, 0, 0, 0, ZoneOffset.UTC), now))
        assertEquals("Nov 5", getShortTime(now.minusHours(24), now))
    }

    @Test
    fun `under a day it counts hours, minutes, seconds, then now`() {
        assertEquals("23h", getShortTime(now.minusHours(23).minusMinutes(59), now))
        assertEquals("1h", getShortTime(now.minusMinutes(60), now))
        assertEquals("59m", getShortTime(now.minusMinutes(59).minusSeconds(59), now))
        assertEquals("1m", getShortTime(now.minusSeconds(60), now))
        assertEquals("45s", getShortTime(now.minusSeconds(45), now))
        assertEquals("now", getShortTime(now.minusSeconds(1), now))
        assertEquals("now", getShortTime(now.plusSeconds(30), now))
    }

    @Test
    fun `no time gives no text`() {
        assertEquals("", getShortTime(0L))
    }

    @Test
    fun `counts under a thousand are written as they are`() {
        assertEquals("0", compactCount(0) { error("not used") })
        assertEquals("999", compactCount(999) { error("not used") })
    }

    @Test
    fun `from a thousand the number format decides`() {
        assertEquals("<1000>", compactCount(1_000) { "<$it>" })
        assertEquals("<2500000>", compactCount(2_500_000) { "<$it>" })
    }
}
