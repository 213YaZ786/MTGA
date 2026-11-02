package com.mtga.app.core.media

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The video address shapes MTGA meets, each told HLS or not by [MediaDownloader.isHls].
 * Same layout as Media3's inferContentType tests, one address and its content type:
 * https://github.com/androidx/media/blob/release/libraries/common/src/test/java/androidx/media3/common/util/UtilTest.java#inferContentType_handlesHlsIsmUris
 */
class MediaDownloaderIsHlsTest {

    @Test
    fun `inferContentType of an x video playlist with its tag is HLS`() {
        assertTrue(MediaDownloader.isHls("https://video.twimg.com/amplify_video/2101398/pl/abc.m3u8?tag=14"))
        assertTrue(MediaDownloader.isHls("https://video.twimg.com/ext_tw_video/1/pl/x.m3u8?container=cmaf"))
        assertTrue(MediaDownloader.isHls("https://video.twimg.com/v/pl/list.M3U8"))
    }

    @Test
    fun `inferContentType of a nitter proxied playlist is HLS even with a query inside the encoded part`() {
        assertTrue(
            MediaDownloader.isHls(
                "https://nitter.example/video/AB12/https%3A%2F%2Fvideo.twimg.com%2Famplify_video%2F9%2Fpl%2Fabc.m3u8%3Ftag%3D14%26v%3D1a2"
            )
        )
        // The dot itself encoded.
        assertTrue(MediaDownloader.isHls("https://nitter.example/video/AB/https%3A%2F%2Fv%2Fx%2Em3u8"))
        assertTrue(MediaDownloader.isHls("https://nitter.example/video/AB/https%3A%2F%2Fv%2Fx.M3U8%3Ftag%3D1"))
    }

    @Test
    fun `inferContentType of mp4 files is not HLS`() {
        // FxTwitter and x.com variants.
        assertFalse(MediaDownloader.isHls("https://video.twimg.com/ext_tw_video/1/vid/avc1/1280x720/Qjv.mp4?tag=12"))
        assertFalse(MediaDownloader.isHls("https://video.twimg.com/tweet_video/ABC.mp4"))
        // Nitter proxied mp4.
        assertFalse(
            MediaDownloader.isHls(
                "https://nitter.example/video/AB/https%3A%2F%2Fvideo.twimg.com%2Fext_tw_video%2F1%2Fvid%2Fx.mp4%3Ftag%3D12"
            )
        )
    }

    @Test
    fun `inferContentType ignores a query or a folder named m3u8`() {
        assertFalse(MediaDownloader.isHls("https://video.twimg.com/v/x.mp4?name=a.m3u8"))
        assertFalse(MediaDownloader.isHls("https://example.org/m3u8/clip.mp4"))
    }

    @Test
    fun `inferContentType of a broken address is not HLS and does not throw`() {
        assertFalse(MediaDownloader.isHls(""))
        assertFalse(MediaDownloader.isHls("https://example.org/a b/%zz"))
        assertFalse(MediaDownloader.isHls("https://example.org/"))
    }
}
