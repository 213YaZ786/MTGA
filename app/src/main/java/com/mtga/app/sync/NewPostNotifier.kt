package com.mtga.app.sync

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.mtga.app.MainActivity
import com.mtga.app.R
import com.mtga.app.core.model.Post

/**
 * Posts one notification per background check that found something.
 *
 * A single post shows its author and text, and a tap opens that post in MTGA
 * through the same x.com link path as a shared link. Several posts become one
 * summary, and a tap opens Home. The notification is replaced by the next
 * one, never stacked, so the shade holds at most one MTGA entry.
 *
 * Nothing leaves the device here: the text is what the check already saved.
 */
class NewPostNotifier(private val context: Context) {

    /** False when Android would drop the notification anyway. */
    fun canNotify(): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        }
        return true
    }

    fun show(posts: List<Post>) {
        if (posts.isEmpty() || !canNotify()) return
        ensureChannel()

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_mtga)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setWhen(posts.first().publishedAtMillis)
            .setShowWhen(true)
            .setNumber(posts.size)

        if (posts.size == 1) {
            val post = posts.first()
            val text = post.text.ifBlank { if (post.media.isNotEmpty()) "Posted media" else "New post" }
            builder
                .setContentTitle("${post.authorName}  @${post.authorHandle}")
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(openPost(post))
        } else {
            val handles = posts.map { "@${it.authorHandle}" }.distinct()
            val from = when {
                handles.size == 1 -> "From ${handles[0]}"
                handles.size == 2 -> "From ${handles[0]} and ${handles[1]}"
                else -> "From ${handles[0]}, ${handles[1]} and ${handles.size - 2} more"
            }
            val inbox = NotificationCompat.InboxStyle()
            posts.take(INBOX_LINES).forEach { post ->
                val line = post.text.lineSequence().firstOrNull { it.isNotBlank() } ?: "Posted media"
                inbox.addLine("@${post.authorHandle}  $line")
            }
            if (posts.size > INBOX_LINES) inbox.setSummaryText("${posts.size - INBOX_LINES} more")
            builder
                .setContentTitle("${posts.size} new posts")
                .setContentText(from)
                .setStyle(inbox)
                .setContentIntent(openHome())
        }

        runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build()) }
    }

    private fun ensureChannel() {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "New posts", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Posts found by background checks from the accounts you follow"
            }
        )
    }

    /** An x.com link, handled by LinkRouter like any shared link, so it opens the post here. */
    private fun openPost(post: Post): PendingIntent {
        val url = "https://x.com/${post.authorHandle}/status/${post.id}"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url), context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            context, REQUEST_POST, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun openHome(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            context, REQUEST_HOME, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        const val CHANNEL_ID = "new-posts"
        private const val NOTIFICATION_ID = 1
        private const val REQUEST_POST = 1
        private const val REQUEST_HOME = 2
        private const val INBOX_LINES = 5
    }
}
