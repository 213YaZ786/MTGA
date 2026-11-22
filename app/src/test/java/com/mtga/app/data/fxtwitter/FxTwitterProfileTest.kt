package com.mtga.app.data.fxtwitter

import com.mtga.app.core.model.ProfileStats
import com.mtga.app.core.model.VerifiedType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// Test protocol: JUnit 4 (https://github.com/junit-team/junit4). The answers keep the shape of
// api.fxtwitter.com/2/profile/NASA of 22 November 2026, as FxEmbed's spec gives it
// (https://github.com/FxEmbed/FxEmbed/blob/main/packages/atmosphere/src/types/api-schemas.ts#APIUserSchema).
/** A profile's card read from the author block of /statuses and from /2/profile. */
class FxTwitterProfileTest {

    private val user = """
        "type": "profile", "id": "11348282", "name": "NASA", "screen_name": "NASA",
        "avatar_url": "https://pbs.twimg.com/profile_images/1/a.jpg", "banner_url": "https://pbs.twimg.com/profile_banners/11348282/1",
        "description": "Exploring the universe", "location": "Pale Blue Dot", "url": "https://nasa.gov",
        "protected": false, "followers": 92168693, "following": 115, "statuses": 74572, "media_count": 1, "likes": 9,
        "joined": "Wed Dec 19 20:20:32 +0000 2007",
        "website": {"url": "https://www.nasa.gov/", "display_url": "nasa.gov"},
        "verification": {"verified": true, "type": "government"}
    """

    @Test
    fun `reads the card from the author of an own post`() {
        val body = """{"results": [{"id": "1", "author": {$user}}], "cursor": {"bottom": "x"}}"""

        val card = FxTwitterParser.profile(body, "nasa")!!

        assertEquals(ProfileStats(posts = 74572, following = 115, followers = 92168693, likes = 9), card.stats)
        assertEquals("Joined December 2007", card.joined)
        assertEquals("https://www.nasa.gov/", card.website)
        assertEquals("Pale Blue Dot", card.location)
        assertEquals(VerifiedType.GOVERNMENT, card.verified)
    }

    @Test
    fun `reads the card from a profile answer`() {
        val body = """{"code": 200, "message": "OK", "user": {$user}}"""

        val card = FxTwitterParser.profile(body, "nasa")!!

        assertEquals(92168693L, card.stats!!.followers)
        assertEquals("Joined December 2007", card.joined)
        assertEquals("NASA", card.name)
    }

    @Test
    fun `an organization is a business mark and an unverified account none`() {
        val business = """{"user": {"name": "A", "verification": {"verified": true, "type": "organization"}}}"""
        val none = """{"user": {"name": "A", "verification": {"verified": false, "type": null}}}"""

        assertEquals(VerifiedType.BUSINESS, FxTwitterParser.profile(business, "a")!!.verified)
        assertEquals(VerifiedType.NONE, FxTwitterParser.profile(none, "a")!!.verified)
    }

    @Test
    fun `a post author stub gives no numbers`() {
        val body = """{"user": {"name": "A", "followers": 0, "joined": "Wed Dec 19 20:20:32 +0000 2007", "profile_embed": true}}"""

        val card = FxTwitterParser.profile(body, "a")!!

        assertNull(card.stats)
        assertNull(card.joined)
    }

    @Test
    fun `an answer without the fields leaves them null`() {
        val card = FxTwitterParser.profile("""{"user": {"name": "A"}}""", "a")!!

        assertNull(card.stats)
        assertNull(card.joined)
        assertNull(card.website)
        assertNull(card.verified)
    }
}
