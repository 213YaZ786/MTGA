package com.mtga.app.feature.feed

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * originalAvatar and bannerFull on URLs shaped as the sources give them; the rules are those of X's page "user profile
 * images and banners" (the variant omitted, the banner sizes up to /1500x500).
 * https://developer.x.com/en/docs/x-api/v1/accounts-and-users/user-profile-images-and-banners
 * not ported: the page shell's scripts (see ProfileHeader.kt), the page being no code.
 */
class ProfileImageUrlsTest {
    @Test
    fun avatarNormalLosesItsVariant() {
        assertEquals(
            "https://pbs.twimg.com/profile_images/1850000000000000011/AbCdEfGh.jpg",
            originalAvatar("https://pbs.twimg.com/profile_images/1850000000000000011/AbCdEfGh_normal.jpg")
        )
    }

    @Test
    fun avatar400x400LosesItsVariant() {
        assertEquals(
            "https://pbs.twimg.com/profile_images/12345/photo.png",
            originalAvatar("https://pbs.twimg.com/profile_images/12345/photo_400x400.png")
        )
    }

    @Test
    fun avatarOtherVariantsLoseThem() {
        listOf("_bigger", "_mini", "_200x200").forEach {
            assertEquals(
                "https://pbs.twimg.com/profile_images/12345/photo.jpg",
                originalAvatar("https://pbs.twimg.com/profile_images/12345/photo$it.jpg")
            )
        }
    }

    @Test
    fun avatarInsideANitterProxyLosesItsVariant() {
        assertEquals(
            "https://nitter.net/pic/pbs.twimg.com%2Fprofile_images%2F12345%2Fphoto.jpg",
            originalAvatar("https://nitter.net/pic/pbs.twimg.com%2Fprofile_images%2F12345%2Fphoto_normal.jpg")
        )
    }

    @Test
    fun avatarOfAnUnknownHostIsUnchanged() {
        val url = "https://example.org/avatars/me_normal.jpg"
        assertEquals(url, originalAvatar(url))
    }

    @Test
    fun avatarWithoutVariantIsUnchanged() {
        val url = "https://pbs.twimg.com/profile_images/12345/photo.jpg"
        assertEquals(url, originalAvatar(url))
    }

    @Test
    fun bannerWithoutSizeGetsTheLargest() {
        assertEquals(
            "https://pbs.twimg.com/profile_banners/783214/1500000000/1500x500",
            bannerFull("https://pbs.twimg.com/profile_banners/783214/1500000000")
        )
    }

    @Test
    fun bannerWithASizeIsSetToTheLargest() {
        listOf("/600x200", "/300x100", "/web", "/web_retina", "/ipad", "/mobile", "/1500x500").forEach {
            assertEquals(
                "https://pbs.twimg.com/profile_banners/783214/1500000000/1500x500",
                bannerFull("https://pbs.twimg.com/profile_banners/783214/1500000000$it")
            )
        }
    }

    @Test
    fun bannerInsideANitterProxyIsUnchanged() {
        val url = "https://nitter.net/pic/pbs.twimg.com%2Fprofile_banners%2F783214%2F1500000000%2F600x200"
        assertEquals(url, bannerFull(url))
    }

    @Test
    fun bannerOfAnUnknownHostIsUnchanged() {
        val url = "https://example.org/banners/me.jpg"
        assertEquals(url, bannerFull(url))
    }
}
