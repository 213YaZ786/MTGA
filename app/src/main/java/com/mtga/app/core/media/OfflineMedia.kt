package com.mtga.app.core.media

import android.content.Context
import com.mtga.app.core.model.MediaItem
import com.mtga.app.core.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Where automatically saved media lives, so a post can be watched with no
 * network.
 *
 * Under the app's own external files directory, not Downloads. Downloads is
 * the reader's own drawer and forty pictures a day in it is vandalism. Here
 * the app owns the folder: no permission to ask for, nothing offered to the
 * gallery, and the whole thing disappears when the app is uninstalled, which
 * is the correct fate for a cache.
 *
 * The index is the file name and there is no database. A name carries the
 * handle, the post id and the position of the media within that post, which
 * is everything needed to go from a post on screen to a file on disk and
 * back. A database for that would be a second copy of the truth, free to
 * disagree with the folder.
 */
class OfflineMedia(context: Context) {

    /**
     * getExternalFilesDir can be null when external storage is not mounted.
     * filesDir then, which is smaller but always there: a reader with no SD
     * card should still get offline playback.
     */
    private val directory =
        File(context.getExternalFilesDir(null) ?: context.filesDir, "media").apply { mkdirs() }

    /** Where [item] of [postId] is, or would be. Never null, may not exist. */
    fun fileFor(postId: String, authorHandle: String, index: Int, item: MediaItem): File =
        File(directory, name(postId, authorHandle, index, item))

    /**
     * postId and position to absolute path, for everything on disk.
     *
     * Held in memory because the lookup happens once per picture per frame of
     * a scrolling list, and listing a directory there would cost more than
     * the picture. Rebuilt by [refresh], never guessed.
     */
    @Volatile
    private var index: Map<String, String> = emptyMap()

    /** Re-reads the folder. Cheap, one listing. */
    suspend fun refresh() = withContext(Dispatchers.IO) {
        index = directory.listFiles().orEmpty().mapNotNull { file ->
            parse(file.name)?.let { key(it.postId, it.index) to file.absolutePath }
        }.toMap()
    }

    /**
     * Where this media is on disk, or null when it was never saved.
     *
     * Not suspending on purpose: it is read during composition, and a photo
     * that is already on the phone should never wait on a coroutine to be
     * shown.
     */
    fun localPath(postId: String, position: Int): String? = index[key(postId, position)]

    private fun key(postId: String, position: Int) = "$postId/$position"

    /** Everything on disk, newest file first. */
    suspend fun entries(): List<Entry> = withContext(Dispatchers.IO) {
        directory.listFiles()
            .orEmpty()
            .mapNotNull { file -> parse(file.name)?.let { Entry(file, it.handle, it.postId, it.index) } }
            .sortedByDescending { it.file.lastModified() }
    }

    suspend fun sizeBytes(): Long = withContext(Dispatchers.IO) {
        directory.listFiles().orEmpty().sumOf { it.length() }
    }

    suspend fun delete(file: File): Boolean {
        val gone = withContext(Dispatchers.IO) { runCatching { file.delete() }.getOrDefault(false) }
        if (gone) refresh()
        return gone
    }

    suspend fun clear() {
        withContext(Dispatchers.IO) {
            directory.listFiles().orEmpty().forEach { runCatching { it.delete() } }
        }
        refresh()
    }

    /**
     * Deletes the media of posts the cache no longer holds.
     *
     * Retention is set once, in "Keep posts", and it governs this too: a
     * media file outlives nothing. A second size budget to tune would be a
     * second thing to get wrong.
     */
    suspend fun keepOnly(postIds: Set<String>): Int = withContext(Dispatchers.IO) {
        var removed = 0
        directory.listFiles().orEmpty().forEach { file ->
            val parsed = parse(file.name)
            if (parsed == null || parsed.postId !in postIds) {
                if (runCatching { file.delete() }.getOrDefault(false)) removed++
            }
        }
        if (removed > 0) refresh()
        removed
    }

    /** One saved file, with the post it came from. */
    data class Entry(val file: File, val handle: String, val postId: String, val index: Int)

    /** What a file name says about the media it holds. */
    internal data class Parsed(val handle: String, val postId: String, val index: Int)

    companion object {
        /** The sub directory of the app's files folder, shared with the downloader. */
        const val FOLDER = "media"

        /**
         * handle, then post id, then position within the post, then
         * extension. The separator is a double underscore because a handle
         * can contain a single one.
         *
         * This name is the whole index. Pure, so the round trip with [parse]
         * can be tested off Android, which is the only thing standing between
         * a saved file and the post that owns it.
         */
        internal fun name(postId: String, authorHandle: String, index: Int, item: MediaItem): String {
            val extension = when (item.type) {
                MediaType.PHOTO -> extensionOf(item.downloadUrl, "jpg")
                else -> extensionOf(item.downloadUrl, "mp4")
            }
            return "${authorHandle.lowercase()}__${postId}__$index.$extension"
        }

        internal fun parse(fileName: String): Parsed? {
            val parts = fileName.substringBeforeLast('.').split("__")
            if (parts.size != 3) return null
            val index = parts[2].toIntOrNull() ?: return null
            if (parts[0].isEmpty() || parts[1].isEmpty()) return null
            return Parsed(parts[0], parts[1], index)
        }

        private fun extensionOf(url: String, fallback: String): String {
            val tail = url.substringBefore('?').substringAfterLast('.', "")
            return if (tail.length in 2..4 && tail.all { it.isLetterOrDigit() }) tail else fallback
        }
    }
}
