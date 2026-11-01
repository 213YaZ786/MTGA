package com.mtga.app.core.media

import android.content.Context
import com.mtga.app.core.model.MediaItem
import com.mtga.app.core.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Where saved media lives, so a post can be watched with no network.
 *
 * Two kinds of copy. The ones saved automatically sit under the app's own
 * external files directory, not Downloads. Downloads is the reader's own
 * drawer and forty pictures a day in it is vandalism. Here the app owns the
 * folder: no permission to ask for, nothing offered to the gallery, and the
 * whole thing disappears when the app is uninstalled, which is the correct
 * fate for a cache. The ones the reader saved by hand sit in Downloads/MTGA and
 * are found again through [ManualSaves].
 *
 * The index of the automatic ones is the file name and there is no database. A
 * name carries the handle, the post id and the position of the media within
 * that post, which is everything needed to go from a post on screen to a file
 * on disk and back. A database for that would be a second copy of the truth,
 * free to disagree with the folder.
 *
 * Every addition below (stills, the version, the grouped deletes) extends this
 * file's own scheme, https://github.com/213YaZ786/MTGA/blob/main/app/src/main/java/com/mtga/app/core/media/OfflineMedia.kt
 */
class OfflineMedia(context: Context) {

    /**
     * getExternalFilesDir can be null when external storage is not mounted.
     * filesDir then, which is smaller but always there: a reader with no SD
     * card should still get offline playback.
     */
    private val directory =
        File(context.getExternalFilesDir(null) ?: context.filesDir, "media").apply { mkdirs() }

    /** The saves made by hand, read back by post and position. */
    val manual: ManualSaves = ManualSaves.of(context)

    private val _version = MutableStateFlow(0)

    /**
     * Counts every change of what is saved, a file landing or going, so a
     * screen that is open reads the index again at once instead of at its next
     * resume. Compose keys on it, see rememberMediaSource.
     */
    val version: StateFlow<Int> = _version.asStateFlow()

    init {
        manual.onChange = { _version.update { it + 1 } }
        // A file saved automatically lands in its own time: the folder is read
        // again as each download finishes, on the receiver's background thread.
        manual.onDownloadDone = { kotlinx.coroutines.runBlocking { refresh() } }
    }

    /** Where [item] of [postId] is, or would be. Never null, may not exist. */
    fun fileFor(postId: String, authorHandle: String, index: Int, item: MediaItem): File =
        File(directory, name(postId, authorHandle, index, item))

    /** Where the still of a video of [postId] is, or would be. Same scheme as [fileFor]. */
    fun stillFor(postId: String, authorHandle: String, index: Int, item: MediaItem): File =
        File(directory, stillName(postId, authorHandle, index, item))

    /**
     * postId and position to absolute path, for everything on disk.
     *
     * Held in memory because the lookup happens once per picture per frame of
     * a scrolling list, and listing a directory there would cost more than
     * the picture. Rebuilt by [refresh], never guessed.
     */
    @Volatile
    private var index: Map<String, String> = emptyMap()

    /** The same for the stills of videos. */
    @Volatile
    private var stills: Map<String, String> = emptyMap()

    /**
     * Re-reads the folder, one listing, and the saves made by hand. Bumps
     * [version] only when something moved, so a pass that found nothing new
     * recomposes nothing.
     */
    suspend fun refresh() = withContext(Dispatchers.IO) {
        manual.load()
        val main = HashMap<String, String>()
        val still = HashMap<String, String>()
        directory.listFiles().orEmpty().forEach { file ->
            val parsed = parse(file.name) ?: return@forEach
            (if (parsed.still) still else main)[key(parsed.postId, parsed.index)] = file.absolutePath
        }
        if (main != index || still != stills) {
            index = main
            stills = still
            _version.update { it + 1 }
        }
    }

    /**
     * Where this media is on disk, or null when it was never saved: the copy
     * saved automatically (a path) or the one saved by hand (a content
     * address, whose file the reader may have deleted since, see
     * [ManualSaves.rescan]).
     *
     * Not suspending on purpose: it is read during composition, and a photo
     * that is already on the phone should never wait on a coroutine to be
     * shown.
     */
    fun localPath(postId: String, position: Int): String? =
        index[key(postId, position)] ?: manual.uriFor(postId, position)

    /** The saved still of a video, or null. Same index as [localPath]. */
    fun localStill(postId: String, position: Int): String? = stills[key(postId, position)]

    private fun key(postId: String, position: Int) = "$postId/$position"

    /** The automatic copies on disk, stills left out, in no order: the screens sort what they need. */
    suspend fun entries(): List<Entry> = withContext(Dispatchers.IO) {
        directory.listFiles()
            .orEmpty()
            .mapNotNull { file ->
                parse(file.name)?.takeIf { !it.still }?.let { Entry(file, it.handle, it.postId, it.index) }
            }
    }

    /** The saved still of each video of the folder, by post id and position. Same listing as [refresh]. */
    suspend fun stillPaths(): Map<String, String> = withContext(Dispatchers.IO) {
        refresh()
        stills
    }

    suspend fun sizeBytes(): Long = withContext(Dispatchers.IO) {
        directory.listFiles().orEmpty().sumOf { it.length() }
    }

    suspend fun delete(file: File): Boolean {
        val gone = withContext(Dispatchers.IO) { runCatching { file.delete() }.getOrDefault(false) }
        if (gone) refresh()
        return gone
    }

    /**
     * Deletes every file of the folder, stills too, that [matches] says so for,
     * with one listing and one refresh: a long list is never listed again for
     * each file it removes. Same walk as [keepOnly].
     */
    suspend fun deleteWhere(matches: (Parsed) -> Boolean): Int = withContext(Dispatchers.IO) {
        var removed = 0
        directory.listFiles().orEmpty().forEach { file ->
            val parsed = parse(file.name)
            if (parsed != null && matches(parsed) && runCatching { file.delete() }.getOrDefault(false)) removed++
        }
        if (removed > 0) refresh()
        removed
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

    /** What a file name says about the media it holds. A [still] is the picture of a saved video. */
    data class Parsed(val handle: String, val postId: String, val index: Int, val still: Boolean = false)

    companion object {
        /** The sub directory of the app's files folder, shared with the downloader. */
        const val FOLDER = "media"

        private const val STILL = "still"

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

        /** The name of the still of a video: [name] with a fourth part and a picture extension. */
        internal fun stillName(postId: String, authorHandle: String, index: Int, item: MediaItem): String =
            "${authorHandle.lowercase()}__${postId}__${index}__$STILL.${extensionOf(item.previewUrl, "jpg")}"

        internal fun parse(fileName: String): Parsed? {
            val parts = fileName.substringBeforeLast('.').split("__")
            if (parts.size != 3 && !(parts.size == 4 && parts[3] == STILL)) return null
            val index = parts[2].toIntOrNull() ?: return null
            if (parts[0].isEmpty() || parts[1].isEmpty()) return null
            return Parsed(parts[0], parts[1], index, still = parts.size == 4)
        }

        private fun extensionOf(url: String, fallback: String): String {
            val tail = url.substringBefore('?').substringAfterLast('.', "")
            return if (tail.length in 2..4 && tail.all { it.isLetterOrDigit() }) tail else fallback
        }
    }
}
