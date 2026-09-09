package com.mtga.app.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mtga.app.core.model.Post
import com.mtga.app.core.model.PostKind
import java.util.concurrent.TimeUnit

@Composable
fun PostCard(
    post: Post,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {

            post.contextLine()?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "@${post.authorHandle}",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(
                    relativeTime(post.publishedAtMillis),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(post.text, style = MaterialTheme.typography.bodyLarge)

            if (post.mediaUrls.isNotEmpty()) {
                Text(
                    mediaLabel(post.mediaUrls.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun Post.contextLine(): String? = when (kind) {
    PostKind.REPOST -> relatedHandle?.let { "Reposted by @$it" } ?: "Repost"
    PostKind.REPLY -> relatedHandle?.let { "Replying to @$it" } ?: "Reply"
    PostKind.QUOTE -> "Quote"
    PostKind.ORIGINAL -> null
}

private fun mediaLabel(count: Int): String =
    if (count == 1) "1 attachment, viewer lands in step 6" else "$count attachments, viewer lands in step 6"

/** Compact relative time. Absolute dates take over past a week. */
internal fun relativeTime(millis: Long): String {
    if (millis <= 0L) return ""
    val delta = System.currentTimeMillis() - millis
    if (delta < 0) return "now"

    val minutes = TimeUnit.MILLISECONDS.toMinutes(delta)
    val hours = TimeUnit.MILLISECONDS.toHours(delta)
    val days = TimeUnit.MILLISECONDS.toDays(delta)

    return when {
        minutes < 1 -> "now"
        minutes < 60 -> "${minutes}m"
        hours < 24 -> "${hours}h"
        days < 7 -> "${days}d"
        else -> "${days / 7}w"
    }
}
