package com.mtga.app.core.media

import com.mtga.app.core.debug.RequestLog
import com.mtga.app.core.model.MediaItem
import com.mtga.app.core.model.MediaType
import com.mtga.app.core.model.Post
import com.mtga.app.core.network.ConnectivityMonitor
import com.mtga.app.data.cache.FeedCache
import com.mtga.app.data.settings.AutoDownload
import com.mtga.app.data.settings.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Saves the media of posts, without being asked, so they can be read with no
 * network: first the posts that have just arrived, then the stored posts whose
 * media is still missing.
 *
 * Into the app's own folder through [OfflineMedia], never into Downloads: the
 * point is offline reading, not filling the reader's drawer. A video is saved
 * with its still, so the post shows its picture offline too.
 *
 * Driven by a refresh of Home and by nothing else. There is no service, no
 * scheduled job and no work manager task behind this: with the app off screen
 * nothing downloads, which is the app's own rule and is stated here so the
 * next reader of this file does not go looking for the missing worker. The
 * catch-up pass is the same: it runs inside the refresh's call, on the app's
 * scope, ends with it, and the files it did not reach wait for the next
 * opening.
 *
 * What it saves is bounded three ways. A watermark, so only what is newer
 * than the moment the option was switched on is ever considered for the first
 * pass. A cap per pass, so one refresh after a week away does not queue a
 * thousand files at once, the rest following on later refreshes. And the
 * network rule the reader chose, for both passes.
 *
 * Every addition in this file (the catch-up, the stills, the network rule)
 * extends this file's own passes, which it adapts:
 * https://github.com/213YaZ786/MTGA/blob/main/app/src/main/java/com/mtga/app/core/media/AutoMediaDownloader.kt
 */
class AutoMediaDownloader(
    private val downloader: MediaDownloader,
    private val store: OfflineMedia,
    private val settings: SettingsStore,
    private val connectivity: ConnectivityMonitor,
    private val log: RequestLog,
    private val notice: MediaSavingNotice,
    private val cache: FeedCache,
    private val attempts: SaveAttempts,
    private val clock: () -> Long = System::currentTimeMillis
) {

    /**
     * Returns how many files were queued, for the log. [posts] is the whole
     * timeline, newest first, exactly as the repository returns it.
     */
    suspend fun consider(posts: List<Post>): Int = withContext(Dispatchers.Main) {
        val current = settings.current
        val mode = current.autoDownloadMedia
        if (mode == AutoDownload.OFF) return@withContext 0
        // DownloadManager finishes in its own time, so the files the last
        // pass queued are only on disk now. Re-read before deciding anything.
        store.refresh()
        if (!allows(mode, connectivity.metered.value)) {
            note("skipped", "Wi-Fi only and this network is metered")
            return@withContext 0
        }

        val ids = mutableListOf<Long>()
        val names = mutableListOf<String>()
        saveFresh(posts, current.autoDownloadedUntilMillis, mode, ids, names)
        val fresh = ids.size
        catchUp(current.keepPostsDays, ids, names)
        if (ids.isNotEmpty()) {
            attempts.add(names)
            // Returns at once: the batch outlives this call, and the timeline
            // must not wait for the files to land before it shows what it read.
            notice.track(ids)
        }
        note("queued ${ids.size} files", "new posts: $fresh | catch-up: ${ids.size - fresh}")
        ids.size
    }

    /** The first pass, for the posts newer than the watermark. Same pass as before, now with stills. */
    private fun saveFresh(
        posts: List<Post>,
        since: Long,
        mode: AutoDownload,
        ids: MutableList<Long>,
        names: MutableList<String>
    ) {
        val fresh = selectFresh(posts, since, MAX_POSTS_PER_PASS)
        if (fresh.isEmpty()) {
            note(
                "nothing new to save",
                "mode: $mode | posts seen: ${posts.size}" +
                    " | with media: ${posts.count { it.media.isNotEmpty() }}" +
                    " | watermark: ${if (since == 0L) "first pass" else since.toString()}"
            )
            return
        }
        fresh.forEach { post ->
            post.media.forEachIndexed { index, item -> queue(post, index, item, ids, names) }
        }
        val watermark = fresh.maxOf { it.publishedAtMillis }
        // The watermark moves with the posts handled, files queued or not.
        settings.update { it.copy(autoDownloadedUntilMillis = watermark) }
    }

    /** The still of a video, then the video itself, each when it is not on disk yet. */
    private fun queue(post: Post, index: Int, item: MediaItem, ids: MutableList<Long>, names: MutableList<String>) {
        if (item.type != MediaType.PHOTO && item.previewUrl.isNotBlank()) {
            val still = store.stillFor(post.id, post.authorHandle, index, item)
            downloader.cache(still, item.previewUrl)?.let { ids += it; names += still.name }
        }
        val target = store.fileFor(post.id, post.authorHandle, index, item)
        downloader.cache(target, item)?.let { ids += it; names += target.name }
    }

    /**
     * The second pass: up to [MAX_CATCH_UP_FILES] files of the posts the
     * retention keeps, newest first, whose media is not on the phone.
     */
    private suspend fun catchUp(keepDays: Int, ids: MutableList<Long>, names: MutableList<String>) {
        val wanted = withContext(Dispatchers.Default) {
            selectCatchUp(
                posts = cache.allPosts(),
                nowMillis = clock(),
                keepDays = keepDays,
                max = MAX_CATCH_UP_FILES,
                have = { postId, position, still ->
                    if (still) store.localStill(postId, position) != null else store.localPath(postId, position) != null
                },
                // Also what the first pass just queued, not yet in the notes.
                asked = { name -> attempts.recently(name) || name in names }
            )
        }
        wanted.forEach { want ->
            val target = if (want.still) {
                store.stillFor(want.post.id, want.post.authorHandle, want.position, want.item)
            } else {
                store.fileFor(want.post.id, want.post.authorHandle, want.position, want.item)
            }
            val address = if (want.still) want.item.previewUrl else want.item.downloadUrl
            downloader.cache(target, address)?.let { ids += it; names += target.name }
        }
    }

    /**
     * Every pass says what it did, including the passes that did nothing.
     * "It does not download" is otherwise unanswerable from the device, and
     * the reasons it can do nothing are all invisible: wrong network, nothing
     * newer than the watermark, no media on what arrived.
     */
    private fun note(outcome: String, detail: String) = log.record(
        kind = RequestLog.Kind.MEDIA,
        url = "auto-download",
        outcome = outcome,
        detail = detail
    )

    /** One file the catch-up wants: [still] is the picture of a video. */
    internal data class Want(val post: Post, val position: Int, val item: MediaItem, val still: Boolean)

    companion object {
        /** One refresh after a long absence should not queue the whole gap. */
        const val MAX_POSTS_PER_PASS = 25

        /** Files one catch-up pass queues at most, the rest waits for the next pass. */
        const val MAX_CATCH_UP_FILES = 40

        private const val DAY_MILLIS = 24L * 60 * 60 * 1000

        /**
         * The reader's "Save media automatically" rule for the network in use:
         * off saves nothing, Wi-Fi only (unmetered) saves unless the network
         * is metered, any saves always. The condition the first pass already
         * checked, now one function for both passes.
         */
        internal fun allows(mode: AutoDownload, metered: Boolean): Boolean = when (mode) {
            AutoDownload.OFF -> false
            AutoDownload.UNMETERED -> !metered
            AutoDownload.ANY -> true
        }

        /**
         * The files of [posts] still missing, newest post first, at most [max]
         * of them: the gaps of media saving, taken from the stored posts.
         *
         * [have] says whether a media (or a video's still) is on the phone,
         * [asked] whether a file name was queued less than a week ago, so a
         * file still coming or one that always fails is not queued at every
         * opening. A post older than the retention ([keepDays], zero keeps
         * everything) is not kept and so not saved. A video's still comes
         * before the video, small and what the post shows first. A playlist
         * (HLS) is no file and is skipped.
         *
         * Newest first, unlike [selectFresh]: there is no watermark to move,
         * what is on the phone answers for what was done, and the newest posts
         * are the ones read again.
         */
        internal fun selectCatchUp(
            posts: List<Post>,
            nowMillis: Long,
            keepDays: Int,
            max: Int,
            have: (postId: String, position: Int, still: Boolean) -> Boolean,
            asked: (fileName: String) -> Boolean
        ): List<Want> {
            val oldest = if (keepDays > 0) nowMillis - keepDays * DAY_MILLIS else Long.MIN_VALUE
            val picked = ArrayList<Want>()
            for (post in posts.sortedByDescending { it.publishedAtMillis }) {
                if (post.publishedAtMillis < oldest) continue
                for ((position, item) in post.media.withIndex()) {
                    if (item.type != MediaType.PHOTO && item.previewUrl.isNotBlank() &&
                        !have(post.id, position, true) &&
                        !asked(OfflineMedia.stillName(post.id, post.authorHandle, position, item))
                    ) {
                        if (picked.size >= max) return picked
                        picked += Want(post, position, item, still = true)
                    }
                    if (!MediaDownloader.isHls(item.downloadUrl) &&
                        !have(post.id, position, false) &&
                        !asked(OfflineMedia.name(post.id, post.authorHandle, position, item))
                    ) {
                        if (picked.size >= max) return picked
                        picked += Want(post, position, item, still = false)
                    }
                }
            }
            return picked
        }

        /**
         * The posts of [posts] whose media is still to save, oldest first and
         * at most [max] of them.
         *
         * Oldest first so that a capped pass leaves the newest for the next
         * one and the watermark can move to exactly what was handled. Newest
         * first would strand the middle of the gap forever. A pin rides at the
         * top of a timeline whatever its age, so it is never something that
         * arrived and is skipped.
         *
         * [since] of zero is the first pass after the option was switched on.
         * It takes the newest [max] instead of the oldest, so switching the
         * option on saves what is on screen now rather than nothing at all
         * until the next post happens to be published. Stamping the watermark
         * with the current instant, which is what this used to do, meant the
         * reader turned the option on, pulled to refresh, and correctly got
         * nothing, which reads exactly like a broken feature.
         *
         * A watermark newer than every post is read as a first pass too. A
         * real pass can only leave the watermark on a post it handled, so it
         * is never ahead of the feed. Ahead means it came from the clock,
         * which is the stamp the version before this one wrote, and a reader
         * updating from it would otherwise keep getting nothing with no way
         * to tell why short of switching the option off and on again.
         */
        internal fun selectFresh(posts: List<Post>, since: Long, max: Int): List<Post> {
            val candidates = posts.filter { !it.isPinned && it.media.isNotEmpty() }
            val newest = candidates.maxOfOrNull { it.publishedAtMillis } ?: return emptyList()
            if (since == 0L || since > newest) {
                return candidates.sortedByDescending { it.publishedAtMillis }
                    .take(max)
                    .sortedBy { it.publishedAtMillis }
            }
            return candidates
                .filter { it.publishedAtMillis > since }
                .sortedBy { it.publishedAtMillis }
                .take(max)
        }
    }
}
