package com.mtga.app.core.media

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import com.mtga.app.core.link.SafeLinks
import com.mtga.app.core.model.MediaItem
import com.mtga.app.core.model.MediaType
import com.mtga.app.core.model.Post
import java.io.File

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
    fun cache(target: File, item: MediaItem): Long? {
        if (target.exists()) return null
        val manager = manager() ?: return null
        // Same check as download.
        val url = SafeLinks.openable(item.downloadUrl) ?: return null
        val request = baseRequest(url, target.name)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_HIDDEN)
            .setDestinationInExternalFilesDir(context, OfflineMedia.FOLDER, target.name)
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
        val candidates = listOf("jpg", "jpeg", "png", "webp", "gif", "mp4", "m3u8")
        val lower = url.lowercase()
        return candidates.firstOrNull { lower.contains(".$it") || lower.contains("%2e$it") }
            ?.takeIf { it != "m3u8" }
            ?: fallback
    }

    private fun toast(message: String) =
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()

    private companion object {
        /** Deliberate saves get their own drawer inside Downloads. */
        const val PUBLIC_FOLDER = "MTGA"
    }
}
