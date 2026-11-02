package com.mtga.app.core.media

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.media3.common.C
import androidx.media3.common.util.Util
import com.mtga.app.core.link.SafeLinks
import com.mtga.app.core.model.MediaItem
import com.mtga.app.core.model.MediaType
import com.mtga.app.core.model.Post
import java.io.File
import java.net.URI
import java.net.URLDecoder

/**
 * Fetches media to disk, to one of two places.
 *
 * Uses the system DownloadManager rather than fetching bytes ourselves: it
 * survives the app being backgrounded, shows progress in the notification
 * shade, and needs no storage permission on modern Android.
 *
 * A file the reader asked for goes to Downloads/MTGA, theirs to keep and to
 * move. A file saved automatically goes to the app's own folder, where the
 * post it belongs to can find it again, see [OfflineMedia]. They used to
 * share the public Downloads folder, which buried a deliberate save under
 * forty automatic ones.
 */
class MediaDownloader(private val context: Context) {

    /**
     * A file the reader asked for. Lands in Downloads/MTGA, with a toast.
     * [post] is the post it comes from (null for an avatar), its address kept
     * by the media store as the file's REFERER_URI, see [SaveOrigin].
     */
    fun download(item: MediaItem, authorHandle: String, post: Post?) {
        // DownloadManager.Request throws on anything but http and https
        // (AOSP, frameworks/base/core/java/android/app/DownloadManager.java),
        // and the address comes from a post: checked as every link is.
        val url = SafeLinks.openable(item.downloadUrl)
        if (url == null) {
            toast("Could not start the download")
            return
        }
        // A playlist saved as a file is a few lines of text named .mp4.
        if (isHls(url)) {
            toast("This video cannot be saved")
            return
        }
        val fileName = buildFileName(item, authorHandle, url)
        val request = baseRequest(url, fileName)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            // A sub folder of a public collection, still no permission needed.
            .setDestinationInExternalPublicDir(
                Environment.DIRECTORY_DOWNLOADS,
                "$PUBLIC_FOLDER/$fileName"
            )
        val origin = PostReferer.forPost(post?.permalink, post?.authorHandle, post?.id)

        val manager = manager()
        if (manager == null) {
            toast("Downloads are unavailable on this device")
            return
        }
        runCatching { manager.enqueue(request) }
            // The post's address goes into the file's media store row when the
            // download is done, see [SaveOrigin].
            .onSuccess { id ->
                SaveOrigin.remember(context, id, origin)
                // Which media of which post this file is, so it plays offline
                // once it is done, see [ManualSaves].
                val position = post?.media?.indexOfFirst { it.downloadUrl == item.downloadUrl } ?: -1
                if (post != null && position >= 0) {
                    ManualSaves.of(context).expect(
                        id, post.id, post.authorHandle.lowercase(), position, item.type != MediaType.PHOTO
                    )
                }
                toast("Saving to Downloads/$PUBLIC_FOLDER")
            }
            .onFailure { toast("Could not start the download") }
    }

    /**
     * A file saved without being asked, for offline reading.
     *
     * No toast and no line of its own in the shade: a reader who turned on
     * automatic saving does not want forty lines for one refresh. The batch
     * gets one line instead, see [MediaSavingNotice]. Hiding the per file line
     * needs DOWNLOAD_WITHOUT_NOTIFICATION in the manifest, without it the
     * enqueue itself throws.
     *
     * Returns the download id to follow, or null when the file was already
     * there or could not even be queued, so the caller can say so in the log
     * rather than guess.
     */
    fun cache(target: File, item: MediaItem): Long? = cache(target, item.downloadUrl)

    /** The same for any address: the file of a video, or its still. */
    fun cache(target: File, address: String): Long? {
        if (target.exists()) return null
        val manager = manager() ?: return null
        // Same check as download.
        val url = SafeLinks.openable(address) ?: return null
        // HLS is a list of pieces, not a file: nothing to keep as one.
        if (isHls(url)) return null
        // The account's folder, made before the download lands in it.
        target.parentFile?.mkdirs()
        val request = baseRequest(url, target.name)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_HIDDEN)
            .setDestinationInExternalFilesDir(context, OfflineMedia.FOLDER, "${target.parentFile?.name}/${target.name}")
        return runCatching { manager.enqueue(request) }.getOrNull()
    }

    private fun manager() = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager

    private fun baseRequest(url: String, title: String) =
        DownloadManager.Request(Uri.parse(url))
            .setTitle(title)
            .setDescription("Saving from MTGA")
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

    /**
     * Nitter proxies media through URL encoded paths, so the tail is rarely a
     * usable file name. Build a predictable one instead.
     */
    private fun buildFileName(item: MediaItem, authorHandle: String, url: String): String {
        val extension = when {
            item.type == MediaType.PHOTO -> guessExtension(url, "jpg")
            item.type == MediaType.GIF -> "mp4"
            else -> guessExtension(url, "mp4")
        }
        val stamp = System.currentTimeMillis()
        return "mtga_${authorHandle}_$stamp.$extension"
    }

    private fun guessExtension(url: String, fallback: String): String {
        val candidates = listOf("jpg", "jpeg", "png", "webp", "gif", "mp4")
        val lower = url.lowercase()
        return candidates.firstOrNull { lower.contains(".$it") || lower.contains("%2e$it") }
            ?: fallback
    }

    private fun toast(message: String) =
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()

    companion object {
        /** Deliberate saves get their own drawer inside Downloads. */
        private const val PUBLIC_FOLDER = "MTGA"

        /**
         * Whether [url] is an HLS playlist, told by Media3's own rule: the
         * extension of the path's last segment, after the last dot, through
         * `Util.inferContentTypeForExtension` (m3u8 is HLS), see
         * https://github.com/androidx/media/blob/release/libraries/common/src/main/java/androidx/media3/common/util/Util.java#inferContentType
         *
         * `inferContentType(Uri)` itself needs android.net.Uri, which unit tests
         * on the JVM cannot run (no Robolectric here), so the last segment is
         * cut with java.net.URI the same way `Uri.getLastPathSegment` does and
         * only the extension goes to Media3.
         *
         * One shape Media3 does not cover: Nitter proxies a file as
         * `/video/<sig>/<whole address, percent encoded>`, so the last segment
         * decodes to a full address with its own `?tag=` query and the
         * extension then reads `m3u8?tag=1`. That segment is judged again as
         * an address of its own.
         */
        internal fun isHls(url: String): Boolean {
            val segment = runCatching {
                // Uri.getLastPathSegment: the encoded path cut at its last slash, then decoded.
                val last = URI(url).rawPath.orEmpty().substringAfterLast('/')
                URLDecoder.decode(last.replace("+", "%2B"), Charsets.UTF_8)
            }.getOrNull() ?: return false
            val extension = segment.substringAfterLast('.', "")
            if (Util.inferContentTypeForExtension(extension) == C.CONTENT_TYPE_HLS) return true
            // Nitter's proxied shape, see above. The decoded segment is shorter
            // than the address it came from, so this ends.
            return segment.contains("://") && isHls(segment)
        }
    }
}
