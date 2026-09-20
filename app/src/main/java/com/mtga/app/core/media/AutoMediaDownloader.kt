package com.mtga.app.core.media

import com.mtga.app.core.debug.RequestLog
import com.mtga.app.core.model.Post
import com.mtga.app.core.network.ConnectivityMonitor
import com.mtga.app.data.settings.AutoDownload
import com.mtga.app.data.settings.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Saves the media of posts that have just arrived, without being asked, so
 * they can be read with no network.
 *
 * Into the app's own folder through [OfflineMedia], never into Downloads: the
 * point is offline reading, not filling the reader's drawer.
 *
 * Driven by a refresh of Home and by nothing else. There is no service, no
 * scheduled job and no work manager task behind this: with the app off screen
 * nothing downloads, which is the app's own rule and is stated here so the
 * next reader of this file does not go looking for the missing worker.
 *
 * What it saves is bounded three ways. A watermark, so only what is newer
 * than the moment the option was switched on is ever considered and the
 * stored backlog is never dumped into Downloads. A cap per pass, so one
 * refresh after a week away does not queue a thousand files at once, the rest
 * following on later refreshes. And the network rule the reader chose.
 */
class AutoMediaDownloader(
    private val downloader: MediaDownloader,
    private val store: OfflineMedia,
    private val settings: SettingsStore,
    private val connectivity: ConnectivityMonitor,
    private val log: RequestLog
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
        if (mode == AutoDownload.UNMETERED && connectivity.metered.value) {
            note("skipped", "Wi-Fi only and this network is metered")
            return@withContext 0
        }

        val since = current.autoDownloadedUntilMillis
        val fresh = selectFresh(posts, since, MAX_POSTS_PER_PASS)
        if (fresh.isEmpty()) {
            note(
                "nothing to save",
                "mode: $mode | posts seen: ${posts.size}" +
                    " | with media: ${posts.count { it.media.isNotEmpty() }}" +
                    " | watermark: ${if (since == 0L) "first pass" else since.toString()}"
            )
            return@withContext 0
        }

        var queued = 0
        fresh.forEach { post ->
            post.media.forEachIndexed { index, item ->
                val target = store.fileFor(post.id, post.authorHandle, index, item)
                if (downloader.cache(target, item)) queued++
            }
        }
        val watermark = fresh.maxOf { it.publishedAtMillis }
        settings.update { it.copy(autoDownloadedUntilMillis = watermark) }
        note(
            "queued $queued files",
            "mode: $mode | posts: ${fresh.size}" +
                " | oldest: ${fresh.first().id} | newest: ${fresh.last().id}" +
                " | watermark now: $watermark"
        )
        queued
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

    companion object {
        /** One refresh after a long absence should not queue the whole gap. */
        const val MAX_POSTS_PER_PASS = 25

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
