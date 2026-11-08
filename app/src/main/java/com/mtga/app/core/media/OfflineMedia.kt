package com.mtga.app.core.media

import android.app.DownloadManager
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.util.Log
import android.webkit.URLUtil
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
 * The index of the automatic ones is the file name and there is no database.
 * A folder per account (AntennaPod's folder per feed) holds files named
 * `<post id>.<position>.<extension>`: the folder, the post id and the position
 * of the media within that post are everything needed to go from a post on
 * screen to a file on disk and back. A database for that would be a second
 * copy of the truth, free to disagree with the folder. The files saved before
 * this scheme sit flat in the media folder as `handle__id__position.ext` and
 * stay readable.
 *
 * Every addition below (stills, the version, the grouped deletes) extends this
 * file's own scheme, https://github.com/213YaZ786/MTGA/blob/main/app/src/main/java/com/mtga/app/core/media/OfflineMedia.kt
 */
class OfflineMedia(context: Context) {

    /** Kept for DownloadManager, which [refresh] asks which transfers are still going. */
    private val appContext: Context = context.applicationContext ?: context

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
        File(File(directory, getMediafilePath(authorHandle)), name(postId, authorHandle, index, item))

    /** Where the still of a video of [postId] is, or would be. Same scheme as [fileFor]. */
    fun stillFor(postId: String, authorHandle: String, index: Int, item: MediaItem): File =
        File(File(directory, getMediafilePath(authorHandle)), stillName(postId, authorHandle, index, item))

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
     *
     * A file DownloadManager is still writing is no copy yet, and the part
     * file of a transfer that failed is removed, otherwise the downloader
     * finds it there and never tries again.
     */
    suspend fun refresh() = withContext(Dispatchers.IO) {
        manual.load()
        val main = HashMap<String, String>()
        val still = HashMap<String, String>()
        val transfers = isDownloadActive()
        val listed = files()
        val active = Companion.isDownloadActive(listed, transfers.unfinished, transfers.failed.keys, cancel = false)
        val cancelled = Companion.isDownloadActive(listed, transfers.unfinished, transfers.failed.keys, cancel = true)
        val manager = appContext.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
        cancelled.forEach { file ->
            // remove() also deletes the file, the delete covers a row already gone.
            transfers.failed[file.absolutePath]?.let { id -> runCatching { manager?.remove(id) } }
            runCatching { file.delete() }
        }
        (listed - active.toSet() - cancelled.toSet()).forEach { file ->
            val parsed = parseFile(file) ?: return@forEach
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
        files().mapNotNull { file ->
            parseFile(file)?.takeIf { !it.still }?.let { Entry(file, it.handle, it.postId, it.index) }
        }
    }

    /** The saved still of each video of the folder, by post id and position. Same listing as [refresh]. */
    suspend fun stillPaths(): Map<String, String> = withContext(Dispatchers.IO) {
        refresh()
        stills
    }

    suspend fun sizeBytes(): Long = withContext(Dispatchers.IO) {
        files().sumOf { it.length() }
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
        files().forEach { file ->
            val parsed = parseFile(file)
            if (parsed != null && matches(parsed) && runCatching { file.delete() }.getOrDefault(false)) removed++
        }
        if (removed > 0) refresh()
        removed
    }

    /**
     * Every file of the media folder in one listing: the flat ones saved
     * before the account folders, then those of each account folder. The
     * listing of this file's previous version, one level deeper:
     * https://github.com/213YaZ786/MTGA/blob/main/app/src/main/java/com/mtga/app/core/media/OfflineMedia.kt#refresh
     */
    private fun files(): List<File> = directory.listFiles().orEmpty().flatMap { child ->
        if (child.isDirectory) child.listFiles().orEmpty().filter { it.isFile } else listOf(child)
    }

    /**
     * What [file] says about its media: by its account folder, or by its flat
     * name when it sits in [directory]. The parse of this file's previous
     * version, https://github.com/213YaZ786/MTGA/blob/main/app/src/main/java/com/mtga/app/core/media/OfflineMedia.kt#parse
     */
    private fun parseFile(file: File): Parsed? =
        if (file.parentFile == directory) parse(file.name) else parse(file.name, file.parentFile?.name)

    suspend fun clear() {
        withContext(Dispatchers.IO) {
            directory.listFiles().orEmpty().forEach { runCatching { it.deleteRecursively() } }
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
        files().forEach { file ->
            val parsed = parseFile(file)
            if (parsed == null || parsed.postId !in postIds) {
                if (runCatching { file.delete() }.getOrDefault(false)) removed++
            }
        }
        if (removed > 0) refresh()
        removed
    }

    /**
     * The transfers of this folder that DownloadManager has not finished: the
     * paths of those paused, pending or running, and the id of each failed one
     * by its path. The flat files and those of the account folders all sit
     * under [directory], so one prefix covers both.
     * https://github.com/rdoeffinger/Dictionary/blob/main/src/com/hughes/android/dictionary/DictionaryManagerActivity.kt#isDownloadActive
     * Dictionary asks the same question (the same query, filtered on status)
     * for one address. Here the answer is every unfinished file of the folder,
     * read from the destination, since the names are ours and the addresses
     * are not. Dictionary's cancel flag and its alert dialogs are not ported:
     * nothing is cancelled here and a reader that cannot ask simply knows
     * nothing, see [downloadManagerQuery].
     * not ported: the match on COLUMN_URI and COLUMN_TITLE (it looks for one
     * address, here every file of the folder is wanted)
     * not ported: finishedDownloadIds (Dictionary's own bookkeeping of ids it
     * cancelled, nothing here reads it)
     */
    private fun isDownloadActive(): Transfers {
        val downloadManager = appContext.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
            ?: return Transfers()
        val query = DownloadManager.Query()
        query.setFilterByStatus(
            DownloadManager.STATUS_PAUSED or DownloadManager.STATUS_PENDING or
                DownloadManager.STATUS_RUNNING or DownloadManager.STATUS_FAILED
        )
        val cursor = downloadManagerQuery(downloadManager, query) ?: return Transfers()

        val unfinished = HashSet<String>()
        val failed = HashMap<String, Long>()
        val prefix = directory.absolutePath + File.separator
        cursor.use {
            val id = it.getColumnIndex(DownloadManager.COLUMN_ID)
            val status = it.getColumnIndex(DownloadManager.COLUMN_STATUS)
            val localUri = it.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
            if (id < 0 || status < 0 || localUri < 0) return Transfers()
            while (it.moveToNext()) {
                // LOCAL_URI is a file:// address, and null until the transfer has a destination.
                val path = it.getString(localUri)?.let { uri -> Uri.parse(uri).path } ?: continue
                if (!path.startsWith(prefix)) continue
                if (it.getInt(status) == DownloadManager.STATUS_FAILED) failed[path] = it.getLong(id)
                else unfinished.add(path)
            }
        }
        return Transfers(unfinished, failed)
    }

    /**
     * Runs the query. A SecurityException or a null cursor, which is what the
     * manager gives when it is disabled (a bug Dictionary notes), mean that
     * nothing is known: the caller carries on as if no transfer were running.
     * https://github.com/rdoeffinger/Dictionary/blob/main/src/com/hughes/android/dictionary/DictionaryManagerActivity.kt#downloadManagerQuery
     * not ported: showDownloadFailedAlert (this is a quiet pass at each
     * refresh, an alert for it would only get in the way)
     */
    private fun downloadManagerQuery(
        downloadManager: DownloadManager,
        query: DownloadManager.Query?
    ): Cursor? {
        val cursor: Cursor?
        try {
            cursor = downloadManager.query(query)
        } catch (se: SecurityException) {
            Log.w(LOG, "Failed to query download manager", se)
            return null
        }
        return cursor
    }

    /** What DownloadManager has not finished: the paths still being written, the id of each failed one by path. */
    private data class Transfers(
        val unfinished: Set<String> = emptySet(),
        val failed: Map<String, Long> = emptyMap()
    )

    /** One saved file, with the post it came from. */
    data class Entry(val file: File, val handle: String, val postId: String, val index: Int)

    /** What a file name says about the media it holds. A [still] is the picture of a saved video. */
    data class Parsed(val handle: String, val postId: String, val index: Int, val still: Boolean = false)

    companion object {
        /** The sub directory of the app's files folder, shared with the downloader. */
        const val FOLDER = "media"

        private const val STILL = "still"

        private const val LOG = "OfflineMedia"

        /**
         * Dictionary's isDownloadActive without Android, so a JVM test can run
         * it. Without [cancel] it answers which listed files a transfer is
         * still writing (paused, pending or running): they stay on disk and
         * out of the index. With [cancel] it answers which listed files belong
         * to a failed transfer: those are to be removed, their part file would
         * block any retry.
         * https://github.com/rdoeffinger/Dictionary/blob/main/src/com/hughes/android/dictionary/DictionaryManagerActivity.kt#isDownloadActive
         */
        internal fun isDownloadActive(files: List<File>, active: Set<String>, failed: Set<String>, cancel: Boolean): List<File> {
            val downloads = if (cancel) failed else active
            return files.filter { destFile -> destFile.absolutePath in downloads }
        }

        /**
         * The account's folder under the media folder, one for every account
         * (X handles are case blind, hence the lower case).
         * https://github.com/AntennaPod/AntennaPod/blob/develop/net/download/service-interface/src/main/java/de/danoeh/antennapod/net/download/serviceinterface/DownloadRequestCreator.java#getMediafilePath
         */
        internal fun getMediafilePath(authorHandle: String): String =
            FileNameGenerator.generateFileName(authorHandle.lowercase())

        /**
         * post id, then position within the post, then extension, the
         * extension taken as AntennaPod does: URLUtil's guess from the address
         * and the mime type, the part after its last dot. The handle is the
         * folder's business ([getMediafilePath]), not the name's.
         *
         * The folder and this name are the whole index, the only thing
         * standing between a saved file and the post that owns it.
         * https://github.com/AntennaPod/AntennaPod/blob/develop/net/download/service-interface/src/main/java/de/danoeh/antennapod/net/download/serviceinterface/DownloadRequestCreator.java#getMediafilename
         */
        @Suppress("UNUSED_PARAMETER")
        internal fun name(postId: String, authorHandle: String, index: Int, item: MediaItem): String {
            val mimeType = if (item.type == MediaType.PHOTO) "image/jpeg" else "video/mp4"
            val urlBaseFilename = guessFileName(item.downloadUrl, mimeType)
            var baseFilename = FileNameGenerator.generateFileName(postId)
            val filenameMaxLength = 220
            if (baseFilename.length > filenameMaxLength) {
                baseFilename = baseFilename.substring(0, filenameMaxLength)
            }
            return baseFilename + "." + index + "." + getExtension(urlBaseFilename)
        }

        /**
         * The name of the still of a video: [name]'s base with a "still" part
         * and a picture extension from the preview address.
         * https://github.com/AntennaPod/AntennaPod/blob/develop/net/download/service-interface/src/main/java/de/danoeh/antennapod/net/download/serviceinterface/DownloadRequestCreator.java#getMediafilename
         */
        @Suppress("UNUSED_PARAMETER")
        internal fun stillName(postId: String, authorHandle: String, index: Int, item: MediaItem): String {
            val mimeType = "image/jpeg"
            val urlBaseFilename = guessFileName(item.previewUrl, mimeType)
            var baseFilename = FileNameGenerator.generateFileName(postId)
            val filenameMaxLength = 220
            if (baseFilename.length > filenameMaxLength) {
                baseFilename = baseFilename.substring(0, filenameMaxLength)
            }
            return baseFilename + "." + index + "." + STILL + "." + getExtension(urlBaseFilename)
        }

        /**
         * What a file name says about its media. [folder] is the account
         * folder the file sits in; null for a file saved before the folders,
         * flat in the media folder as `handle__postId__index.ext` (or
         * `...__still.ext`), read as it always was.
         */
        internal fun parse(fileName: String, folder: String? = null): Parsed? {
            if (folder == null) return parseLegacy(fileName)
            val parts = fileName.substringBeforeLast('.').split('.')
            if (parts.size != 2 && !(parts.size == 3 && parts[2] == STILL)) return null
            val index = parts[1].toIntOrNull() ?: return null
            if (folder.isEmpty() || parts[0].isEmpty()) return null
            return Parsed(folder, parts[0], index, still = parts.size == 3)
        }

        /** The parse of this file's previous version, the names written before the account folders. */
        private fun parseLegacy(fileName: String): Parsed? {
            val parts = fileName.substringBeforeLast('.').split("__")
            if (parts.size != 3 && !(parts.size == 4 && parts[3] == STILL)) return null
            val index = parts[2].toIntOrNull() ?: return null
            if (parts[0].isEmpty() || parts[1].isEmpty()) return null
            return Parsed(parts[0], parts[1], index, still = parts.size == 4)
        }

        private const val EXTENSION_SEPARATOR = '.'
        private const val NOT_FOUND = -1
        private const val EMPTY_STRING = ""

        /**
         * What follows the last dot of [fileName], empty for none.
         * https://github.com/apache/commons-io/blob/master/src/main/java/org/apache/commons/io/FilenameUtils.java#getExtension
         */
        private fun getExtension(fileName: String): String {
            val index = indexOfExtension(fileName)
            if (index == NOT_FOUND) {
                return EMPTY_STRING
            }
            return fileName.substring(index + 1)
        }

        /** The position of the last dot, or [NOT_FOUND]. FilenameUtils's indexOfExtension, without its Windows and path parts: [fileName] is a file name. */
        private fun indexOfExtension(fileName: String): Int = fileName.lastIndexOf(EXTENSION_SEPARATOR)

        /**
         * URLUtil.guessFileName(url, null, mimeType), as AntennaPod calls it.
         * A unit test on the JVM has no URLUtil (the stub jar throws "not
         * mocked"): the tail of the address stands in there, which names the
         * same for an address that carries its extension.
         * https://github.com/aosp-mirror/platform_frameworks_base/blob/main/core/java/android/webkit/URLUtil.java#guessFileName
         */
        private fun guessFileName(url: String, mimeType: String): String =
            runCatching { URLUtil.guessFileName(url, null, mimeType) }.getOrElse {
                val extension = url.substringBefore('?').substringAfterLast(EXTENSION_SEPARATOR, EMPTY_STRING)
                val known = extension.length in 2..4 && extension.all { it.isLetterOrDigit() }
                "downloadfile." + if (known) extension else if (mimeType.startsWith("image/")) "jpg" else "mp4"
            }
    }
}
