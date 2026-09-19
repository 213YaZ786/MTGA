package com.mtga.app.core.media

import com.mtga.app.core.model.Post
import com.mtga.app.core.network.ConnectivityMonitor
import com.mtga.app.data.settings.AutoDownload
import com.mtga.app.data.settings.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Saves the media of posts that have just arrived, without being asked.
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
    private val settings: SettingsStore,
    private val connectivity: ConnectivityMonitor
) {

    /**
     * Returns how many files were queued, for the log. [posts] is the whole
     * timeline, newest first, exactly as the repository returns it.
     */
    suspend fun consider(posts: List<Post>): Int = withContext(Dispatchers.Main) {
        val current = settings.current
        when (current.autoDownloadMedia) {
            AutoDownload.OFF -> return@withContext 0
            AutoDownload.UNMETERED -> if (connectivity.metered.value) return@withContext 0
            AutoDownload.ANY -> Unit
        }

        val since = current.autoDownloadedUntilMillis
        if (since == 0L) {
            // Should have been set when the option was turned on. Set it now
            // rather than treating every stored post as new.
            settings.update { it.copy(autoDownloadedUntilMillis = System.currentTimeMillis()) }
            return@withContext 0
        }

        val fresh = selectFresh(posts, since, MAX_POSTS_PER_PASS)
        if (fresh.isEmpty()) return@withContext 0

        var queued = 0
        fresh.forEach { post ->
            post.media.forEach { item ->
                downloader.download(item, post.authorHandle, silent = true)
                queued++
            }
        }
        settings.update { it.copy(autoDownloadedUntilMillis = fresh.last().publishedAtMillis) }
        queued
    }

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
         */
        internal fun selectFresh(posts: List<Post>, since: Long, max: Int): List<Post> =
            posts
                .filter { !it.isPinned && it.media.isNotEmpty() && it.publishedAtMillis > since }
                .sortedBy { it.publishedAtMillis }
                .take(max)
    }
}
