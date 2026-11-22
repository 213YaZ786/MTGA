package com.mtga.app.data.cache

import com.mtga.app.core.model.Feed
import com.mtga.app.core.model.ProfileStats
import com.mtga.app.core.model.VerifiedType
import org.junit.Assert.assertEquals
import org.junit.Test

// Test protocol: JUnit 4 (https://github.com/junit-team/junit4).
/** The profile card's fields in [FeedMerge]: the freshest non-null, else the stored one. */
class FeedMergeProfileTest {

    private fun feed(stats: ProfileStats? = null, joined: String? = null, verified: VerifiedType? = null, website: String? = null) =
        Feed(
            handle = "nasa", displayName = "NASA", posts = emptyList(), fetchedFromHost = "h", fetchedAtMillis = 1,
            stats = stats, joined = joined, verified = verified, website = website
        )

    @Test
    fun `stored stats stay when a source sends none`() {
        val stored = feed(ProfileStats(posts = 74572, followers = 92168693), "Joined December 2007", VerifiedType.GOVERNMENT, "https://nasa.gov")

        val merged = FeedMerge.combine(stored, feed(), isPagedFetch = false).feed

        assertEquals(ProfileStats(posts = 74572, followers = 92168693), merged.stats)
        assertEquals("Joined December 2007", merged.joined)
        assertEquals(VerifiedType.GOVERNMENT, merged.verified)
        assertEquals("https://nasa.gov", merged.website)
    }

    @Test
    fun `fresh stats replace the stored ones`() {
        val stored = feed(ProfileStats(followers = 1))

        val merged = FeedMerge.combine(stored, feed(ProfileStats(followers = 2)), isPagedFetch = false).feed

        assertEquals(2L, merged.stats!!.followers)
    }
}
