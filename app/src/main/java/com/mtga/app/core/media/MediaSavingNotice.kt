package com.mtga.app.core.media

import android.app.DownloadManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.mtga.app.R
import kotlinx.coroutines.delay

/**
 * One notification for a batch of automatic saves, instead of one per file.
 *
 * The files themselves are hidden from the shade (see
 * [MediaDownloader.cache]), since twenty pictures would be twenty lines. This
 * line says how many are left, so nobody has to guess whether it is safe to
 * leave the app, and then what was saved.
 *
 * Ported from LinkedOut, where it was tuned on the device: the default
 * importance, the "saved" line that stays a minute, the timeout that clears a
 * bar the process was killed before finishing.
 */
class MediaSavingNotice(private val context: Context) {

    /**
     * Watches the batch until it is done. Polling rather than a broadcast
     * receiver: a receiver would have to be declared in the manifest and be
     * woken with the app closed, which MTGA does not do.
     */
    suspend fun follow(ids: List<Long>) {
        if (ids.isEmpty()) return
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager ?: return
        val total = ids.size
        notify(left = total, total = total, done = false)
        var left = total
        var rounds = 0
        while (left > 0 && rounds < MAX_ROUNDS) {
            delay(POLL_MILLIS)
            rounds++
            left = runCatching { stillRunning(manager, ids) }.getOrDefault(0)
            if (left > 0) notify(left = left, total = total, done = false)
        }
        // Not a bare cancel. A handful of pictures lands in about a second,
        // and a bar that exists for one poll is a bar nobody sees. What stays
        // is a line saying what was saved, which Android clears by itself.
        notify(left = 0, total = total, done = true)
    }

    private fun stillRunning(manager: DownloadManager, ids: List<Long>): Int {
        val query = DownloadManager.Query().setFilterById(*ids.toLongArray())
        manager.query(query)?.use { cursor ->
            val column = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
            if (column < 0) return 0
            var running = 0
            while (cursor.moveToNext()) {
                val status = cursor.getInt(column)
                if (status == DownloadManager.STATUS_PENDING || status == DownloadManager.STATUS_RUNNING) {
                    running++
                }
            }
            return running
        }
        return 0
    }

    private fun notify(left: Int, total: Int, done: Boolean) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        ensureChannel()
        val finished = total - left
        val builder = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_mtga)
            .setSilent(true)
            .setOngoing(false)
            // If the process dies mid batch nobody is left to clear this, so
            // Android is told to do it rather than leaving a stuck bar.
            .setTimeoutAfter(if (done) DONE_TIMEOUT_MILLIS else TIMEOUT_MILLIS)
        if (done) {
            builder
                .setContentTitle("Media saved for offline reading")
                .setContentText(if (total == 1) "1 file" else "$total files")
                .setAutoCancel(true)
        } else {
            builder
                .setContentTitle("Saving media for offline reading")
                .setContentText("$finished of $total")
                .setProgress(total, finished, false)
        }
        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
        }
    }

    private fun ensureChannel() {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL) != null) return
        manager.createNotificationChannel(
            // Default rather than low. At low importance Android folds the
            // line to the bottom of the shade, and a batch of a few seconds
            // is then never seen. Silence comes from setSilent, not from here.
            NotificationChannel(CHANNEL, "Saving media", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Progress while pictures and videos are saved for offline reading."
                setShowBadge(false)
            }
        )
    }

    private companion object {
        const val CHANNEL = "media-saving"
        const val NOTIFICATION_ID = 4201
        const val POLL_MILLIS = 1_000L

        /** Ten minutes of polling, then the batch is left to the system. */
        const val MAX_ROUNDS = 600
        const val TIMEOUT_MILLIS = 10 * 60 * 1000L

        /** How long the "saved" line stays before Android takes it away. */
        const val DONE_TIMEOUT_MILLIS = 60 * 1000L
    }
}
