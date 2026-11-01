package com.mtga.app.core.media

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.os.Bundle
import android.provider.MediaStore
import com.mtga.app.core.common.writeTextAtomically
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * The saves the reader made by hand (Downloads/MTGA), found again from the
 * post that shows them, so a video saved by hand plays with no network.
 *
 * Until now a manual save only carried its post in the media store row
 * (REFERER_URI, see [SaveOrigin]) and nothing read it back. This is the read
 * back: post id and position of the media in the post, to the address of the
 * file in the media store. A small JSON file of the app, written in one step
 * (writeTextAtomically) like the rest of the app's storage. Held in memory once
 * loaded, because the lookup runs for every picture of a scrolling list.
 *
 * Files saved before this version are found by one paged pass over the app's
 * own rows of MediaStore.Downloads, see [rescan]. A file the reader deleted
 * stops being listed by the same pass, and a post whose copy is gone falls back
 * to the network.
 */
class ManualSaves(private val file: File) {

    /** One saved file: [uri] is its media store address, [position] its place in the post. */
    @Serializable
    data class Save(
        val post: String,
        val handle: String,
        val position: Int,
        val uri: String,
        val video: Boolean = false
    )

    /**
     * A download asked for and not yet found in the media store, with the media
     * it is. Serialized as kotlinx.serialization documents a plain data class
     * (https://github.com/Kotlin/kotlinx.serialization/blob/master/docs/basic-serialization.md).
     */
    @Serializable
    data class Waiting(
        val id: Long,
        val post: String,
        val handle: String,
        val position: Int,
        val video: Boolean,
        val at: Long
    )

    @Serializable
    private data class Doc(
        val saves: List<Save> = emptyList(),
        val rebuilt: Boolean = false,
        val waiting: List<Waiting> = emptyList()
    )

    /** One row of the media store as the rebuild reads it. */
    internal data class Row(val uri: String, val referer: String?, val video: Boolean)

    private val lock = Any()

    @Volatile
    private var byKey: Map<String, Save> = emptyMap()

    @Volatile
    private var loaded = false
    private var rebuilt = false
    private var waiting: List<Waiting> = emptyList()

    /** Told after each change, so the screens showing media read the index again. */
    @Volatile
    var onChange: (() -> Unit)? = null

    /** Told after each finished download of the app, whatever it was. */
    @Volatile
    var onDownloadDone: (() -> Unit)? = null

    /** Reads the file once. Disk, so never from the main thread. */
    fun load() {
        if (loaded) return
        synchronized(lock) {
            if (loaded) return
            val doc = runCatching { JSON.decodeFromString<Doc>(file.readText()) }.getOrNull() ?: Doc()
            rebuilt = doc.rebuilt
            waiting = doc.waiting
            byKey = index(doc.saves)
            loaded = true
        }
    }

    /** The file of this media of this post, or null. A map lookup, safe in composition. */
    fun uriFor(postId: String, position: Int): String? = byKey[key(postId, position)]?.uri

    /** Every file, in no order. A copy. */
    fun all(): List<Save> = byKey.values.toList()

    /** Notes a finished save. The same place in the same post is replaced. */
    fun record(save: Save) {
        load()
        synchronized(lock) {
            byKey = byKey + (key(save.post, save.position) to save)
            persist()
        }
        onChange?.invoke()
    }

    /**
     * Forgets the saves of [postId] in the index. The files in Downloads/MTGA
     * are not touched, only the way back from the post to them. Same write as
     * [record]: https://github.com/213YaZ786/MTGA/blob/main/app/src/main/java/com/mtga/app/core/media/OfflineMedia.kt
     * Blocking, off the main thread only.
     */
    fun removePost(postId: String) {
        load()
        val changed = synchronized(lock) {
            val next = byKey.filterValues { it.post != postId }
            if (next.size == byKey.size) {
                false
            } else {
                byKey = next
                persist()
                true
            }
        }
        if (changed) onChange?.invoke()
    }

    /**
     * One pass over the app's rows of Downloads/MTGA: keeps what still exists,
     * and until it has been done once, adds the files saved before this version
     * from their REFERER_URI. A failed read changes nothing, so a missing
     * permission or a busy provider cannot empty the index. Blocking, off the
     * main thread only.
     */
    fun rescan(context: Context) {
        load()
        val before = byKey.values.toSet()
        val rows = readRows(context) ?: return
        val changed = synchronized(lock) {
            val existing = rows.mapTo(HashSet()) { it.uri }
            // A save noted while the pages were read is newer than the pages: kept.
            val kept = byKey.values.filter { it.uri in existing || it !in before }
            val added = if (rebuilt) emptyList() else plan(rows, kept)
            val next = index(kept + added)
            val different = next != byKey || !rebuilt
            byKey = next
            rebuilt = true
            if (different) persist()
            different
        }
        if (changed) onChange?.invoke()
    }

    private fun persist() {
        runCatching { file.writeTextAtomically(JSON.encodeToString(Doc(byKey.values.toList(), rebuilt, waiting))) }
    }

    /**
     * Notes that download [downloadId] is the media at [position] of [post], to
     * be found in the media store when it is done. Returns at once, written on
     * a thread of its own.
     */
    fun expect(downloadId: Long, post: String, handle: String, position: Int, video: Boolean) {
        if (downloadId < 0 || position < 0) return
        io.execute {
            load()
            synchronized(lock) {
                val kept = waiting.filter { it.id != downloadId && System.currentTimeMillis() - it.at in 0..MAX_WAIT_MILLIS }
                waiting = (kept + Waiting(downloadId, post, handle, position, video, System.currentTimeMillis()))
                    .takeLast(MAX_WAITING)
                persist()
            }
        }
    }

    /**
     * Finds the media store row of every finished download noted by [expect]
     * and records it. A running one stays noted, a failed or vanished one is
     * forgotten, a finished one whose row the media scanner has not made yet
     * stays noted: [retries] more looks, [pauseMillis] apart. Blocking, off the
     * main thread only.
     *
     * The download is read as SaveOrigin.settle does it (DownloadManager.query,
     * https://developer.android.com/reference/android/app/DownloadManager#query(android.app.DownloadManager.Query))
     * and the row is found by its path and owner (MediaStore query by selection,
     * https://developer.android.com/training/data-storage/shared/media#query-collection).
     */
    fun settle(context: Context, retries: Int = 0, pauseMillis: Long = 1_500) {
        load()
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as? android.app.DownloadManager ?: return
        val downloads = android.os.Environment
            .getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS).path
        val base = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL)
        var attempt = 0
        while (true) {
            val now = synchronized(lock) { waiting }
            if (now.isEmpty()) return
            val finished = HashSet<Long>()
            var unsettled = 0
            for (note in now) {
                when (val state = statusOf(manager, note.id)) {
                    null -> finished += note.id
                    "" -> Unit
                    else -> {
                        val path = SaveOrigin.localPath(state, downloads)
                        if (path == null) {
                            finished += note.id
                        } else {
                            val row = rowOf(context, base, path)
                            if (row == null) {
                                unsettled++
                            } else {
                                finished += note.id
                                record(Save(note.post, note.handle, note.position, row, note.video))
                            }
                        }
                    }
                }
            }
            if (finished.isNotEmpty()) {
                synchronized(lock) {
                    waiting = waiting.filter { it.id !in finished }
                    persist()
                }
            }
            if (unsettled == 0 || attempt >= retries) return
            attempt++
            Thread.sleep(pauseMillis)
        }
    }

    /** The local address of a finished download, "" while it runs, null when failed or gone. */
    private fun statusOf(manager: android.app.DownloadManager, id: Long): String? {
        val cursor = runCatching { manager.query(android.app.DownloadManager.Query().setFilterById(id)) }.getOrNull()
            ?: return ""
        cursor.use {
            if (!it.moveToFirst()) return null
            return when (it.getInt(it.getColumnIndexOrThrow(android.app.DownloadManager.COLUMN_STATUS))) {
                android.app.DownloadManager.STATUS_SUCCESSFUL ->
                    it.getString(it.getColumnIndexOrThrow(android.app.DownloadManager.COLUMN_LOCAL_URI)).orEmpty().ifEmpty { null }
                android.app.DownloadManager.STATUS_FAILED -> null
                else -> ""
            }
        }
    }

    /** The address of the app's own row of the file at [path], or null. */
    private fun rowOf(context: Context, base: android.net.Uri, path: String): String? {
        val filter = SaveOrigin.rowFilter(path, context.packageName)
        return runCatching {
            context.contentResolver.query(
                base, arrayOf(MediaStore.MediaColumns._ID), filter.first, filter.second, null
            )?.use { if (it.moveToFirst()) ContentUris.withAppendedId(base, it.getLong(0)).toString() else null }
        }.getOrNull()
    }

    /**
     * Pages of [PAGE] rows after the last id seen, so thousands of saved files
     * never come in one cursor (https://developer.android.com/training/data-storage/shared/media#query-collection,
     * ContentResolver.QUERY_ARG_LIMIT). Null when the provider refuses.
     */
    private fun readRows(context: Context): List<Row>? = runCatching {
        val base = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val rows = ArrayList<Row>()
        var last = 0L
        while (true) {
            val args = Bundle().apply {
                putString(
                    ContentResolver.QUERY_ARG_SQL_SELECTION,
                    "${MediaStore.MediaColumns._ID} > ? AND ${MediaStore.MediaColumns.OWNER_PACKAGE_NAME} = ? AND " +
                        "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"
                )
                putStringArray(
                    ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS,
                    arrayOf(last.toString(), context.packageName, "$RELATIVE_PATH%")
                )
                putStringArray(ContentResolver.QUERY_ARG_SORT_COLUMNS, arrayOf(MediaStore.MediaColumns._ID))
                putInt(ContentResolver.QUERY_ARG_SORT_DIRECTION, ContentResolver.QUERY_SORT_DIRECTION_ASCENDING)
                putInt(ContentResolver.QUERY_ARG_LIMIT, PAGE)
            }
            var count = 0
            val projection = arrayOf(
                MediaStore.MediaColumns._ID,
                MediaStore.DownloadColumns.REFERER_URI,
                MediaStore.MediaColumns.MIME_TYPE
            )
            val cursor = context.contentResolver.query(base, projection, args, null) ?: return null
            cursor.use {
                while (it.moveToNext()) {
                    val id = it.getLong(0)
                    last = id
                    count++
                    rows += Row(
                        uri = ContentUris.withAppendedId(base, id).toString(),
                        referer = it.getString(1),
                        video = it.getString(2)?.startsWith("video/") == true
                    )
                }
            }
            if (count < PAGE) break
        }
        rows
    }.getOrNull()

    /**
     * Hears DownloadManager's ACTION_DOWNLOAD_COMPLETE for as long as the
     * process lives, as SaveOriginReceiver does from the manifest (that one is
     * a file shared by the four readers, so the hook for this index lives here)
     * and settles the saves of [expect]. goAsync keeps the receiver alive
     * (https://developer.android.com/reference/android/content/BroadcastReceiver#goAsync()).
     */
    private class Listener : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context, intent: android.content.Intent) {
            val id = intent.getLongExtra(android.app.DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
            if (!SaveOriginReceiver.accepts(intent.action, id)) return
            val pending = goAsync()
            val app = context.applicationContext
            kotlin.concurrent.thread(name = "manual-saves") {
                try {
                    val saves = of(app)
                    saves.settle(app, retries = 2)
                    // Any download of the app, an automatic one too: the folder is read again.
                    saves.onDownloadDone?.invoke()
                } finally {
                    pending.finish()
                }
            }
        }
    }

    companion object {
        private const val PAGE = 500
        private const val MAX_WAITING = 500
        private const val MAX_WAIT_MILLIS = 7L * 24 * 60 * 60 * 1000

        private val io = java.util.concurrent.Executors.newSingleThreadExecutor { task ->
            Thread(task, "manual-saves-io").apply { isDaemon = true }
        }

        /**
         * Starts listening, from Application.onCreate. The receiver is exported
         * because the sender is the download provider, another uid: a receiver
         * not exported would never hear it. The permission keeps it safe, as in
         * SaveOriginReceiver's manifest entry: only the system's download
         * provider holds SEND_DOWNLOAD_COMPLETED_INTENTS, so no other app can
         * send to it (AOSP DownloadProvider sends the broadcast under it,
         * https://developer.android.com/reference/android/content/Context#registerReceiver(android.content.BroadcastReceiver,%20android.content.IntentFilter,%20java.lang.String,%20android.os.Handler,%20int);
         * ported from ContextCompat.registerReceiver with a permission,
         * https://github.com/androidx/androidx/blob/androidx-main/core/core/src/main/java/androidx/core/content/ContextCompat.java).
         */
        fun listen(context: Context) {
            val app = context.applicationContext
            androidx.core.content.ContextCompat.registerReceiver(
                app,
                Listener(),
                android.content.IntentFilter(android.app.DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                "android.permission.SEND_DOWNLOAD_COMPLETED_INTENTS",
                null,
                androidx.core.content.ContextCompat.RECEIVER_EXPORTED
            )
        }

        /** DownloadManager's Download/MTGA/, see MediaDownloader. */
        private const val RELATIVE_PATH = "Download/MTGA"

        private val JSON = Json { ignoreUnknownKeys = true }

        @Volatile
        private var shared: ManualSaves? = null

        /** One per process: the receiver, the start of the app and the screens share it. */
        fun of(context: Context): ManualSaves =
            shared ?: synchronized(this) {
                shared ?: ManualSaves(File(context.applicationContext.filesDir, "manual-saves.json")).also { shared = it }
            }

        private fun key(postId: String, position: Int) = "$postId/$position"

        private fun index(saves: List<Save>): Map<String, Save> =
            saves.associateBy { key(it.post, it.position) }

        /**
         * The files to add for rows [rows] (oldest first) that [kept] does not
         * hold yet. The post is the one in the REFERER_URI (a row without a
         * post address is not a save of a post and is skipped) and the position
         * is the order of that post's saves, 0 for the first: the media store
         * never kept which picture of the post it was. A place [kept] already
         * holds is not overwritten.
         */
        internal fun plan(rows: List<Row>, kept: List<Save>): List<Save> {
            val known = kept.mapTo(HashSet()) { it.uri }
            val taken = kept.mapTo(HashSet()) { key(it.post, it.position) }
            val seen = HashMap<String, Int>()
            val added = ArrayList<Save>()
            for (row in rows) {
                val status = PostReferer.status(row.referer) ?: continue
                val position = seen.merge(status.second, 1, Int::plus)!! - 1
                if (row.uri in known || key(status.second, position) in taken) continue
                added += Save(status.second, status.first, position, row.uri, row.video)
            }
            return added
        }
    }
}
