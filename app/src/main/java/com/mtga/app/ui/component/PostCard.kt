package com.mtga.app.ui.component

import androidx.compose.foundation.combinedClickable
import com.mtga.app.ui.theme.innerZone
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import com.mtga.app.ui.theme.LocalDisplayPrefs
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ShapeDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.mtga.app.core.media.rememberMediaSource
import com.mtga.app.core.model.CommunityNote
import com.mtga.app.core.model.LinkCard
import com.mtga.app.core.model.MediaItem
import com.mtga.app.core.model.MediaType
import com.mtga.app.core.model.Poll
import com.mtga.app.core.model.Post
import com.mtga.app.core.model.PostKind
import androidx.compose.ui.graphics.vector.ImageVector
import com.mtga.app.ui.icon.MtgaIcons
import android.icu.text.CompactDecimalFormat
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * A post, laid out the way a reader expects one: who, when, what, then the
 * media, then the numbers. Media carries its own download control, since
 * saving a picture is the single most common thing people want from a client
 * like this and burying it in a long press is hostile.
 */
@Composable
fun PostCard(
    post: Post,
    onClick: () -> Unit,
    onOpenLink: (String) -> Unit,
    onDownload: (MediaItem) -> Unit,
    onOpenMedia: (index: Int) -> Unit = {},
    showStats: Boolean = true,
    /** Arrived since the last visit and not yet scrolled past. */
    unread: Boolean = false,
    modifier: Modifier = Modifier
) {
    val compact = LocalDisplayPrefs.current.compact
    // A post is an object, so it gets a container of its own with air around
    // it, the same rounded zone the settings sections use. The hairline that
    // used to separate two posts is gone with it: two zones with a gap read as
    // two things without needing a line drawn between them.
    val shape = ShapeDefaults.ExtraLarge
    val tap = rememberHaptics()
    // A tap opens the post, a long press the suite's long press menu, see PostActions.
    val actions = rememberPostActions(post, onDownload)
    // The zone is drawn twice, as SMS's ThreadScreen.kt draws its bubble (in place, and again as the held copy
    // without click handlers): [outer] places it, [clicks] is what the column answers to.
    // The copy flies no avatar to the opened post (one shared key, one element) and plays no film of its own.
    val zone: @Composable (Modifier, Modifier, Boolean) -> Unit = { outer, clicks, copy ->
    ZoneSurface(
        modifier = outer,
        shape = shape
    ) {
        Column(
            clicks.padding(horizontal = 16.dp, vertical = if (compact) 10.dp else 14.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 8.dp)
        ) {
            post.contextLine()?.let { ContextLine(it, icon = if (post.isPinned) MtgaIcons.Pin else null) }

            Row(
                horizontalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Avatar(
                    post.avatarUrl,
                    post.authorName,
                    size = if (compact) 36.dp else 44.dp,
                    sharedKey = if (copy) null else post.id
                )

                Column(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(if (compact) 3.dp else 6.dp)
                ) {
                    NameRow(post, unread)

                    if (post.text.isNotBlank()) {
                        Text(post.text, style = MaterialTheme.typography.bodyLarge)
                    }

                    if (post.media.isNotEmpty()) {
                        MediaBlock(post = post, onDownload = onDownload, onOpen = onOpenMedia)
                    }

                    // Nitter's order: poll, link card, quote, then the note.
                    post.poll?.let { PollBlock(it) }

                    post.card?.let { card ->
                        LinkCardBlock(card = card, onClick = { card.url?.let(onOpenLink) })
                    }

                    post.quoted?.let { quote ->
                        QuoteBlock(
                            handle = quote.handle,
                            name = quote.name,
                            text = quote.text,
                            note = quote.note,
                            media = quote.media,
                            unavailable = quote.unavailable,
                            onClick = { onOpenLink(quote.permalink) }
                        )
                    }

                    post.note?.let { NoteBlock(it, maxLines = 6) }

                    if (showStats) post.stats?.let { StatsRow(it) }
                }
            }
        }
    }
    }
    Box(modifier) {
        // The tracker sits on the zone itself, after the padding, so its bounds are the zone HoldMenu lifts.
        zone(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = if (compact) 3.dp else 5.dp)
                .then(actions.tracker),
            Modifier.combinedClickable(onClick = { tap.tick(); onClick() }, onLongClick = actions::open),
            false
        )
        PostActionsOverlay(actions) {
            CompositionLocalProvider(LocalInlinePlaying provides null) { zone(Modifier.fillMaxWidth(), Modifier, true) }
        }
    }
}

@Composable
internal fun Avatar(
    url: String?,
    name: String,
    size: Dp = 44.dp,
    /** Post id when this avatar should fly to the opened post, null otherwise. */
    sharedKey: String? = null
) {
    val shape = if (LocalDisplayPrefs.current.squareAvatars) RoundedCornerShape(size * 0.22f) else CircleShape
    Box(
        modifier = Modifier
            .size(size)
            .let { if (sharedKey != null) it.sharedPostElement("avatar-$sharedKey") else it }
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center
    ) {
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size)
            )
        } else {
            Text(
                name.take(1).uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun NameRow(post: Post, unread: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            post.authorName,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        Text(
            " @${post.authorHandle}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        // Name and time joined with a comma, as AOSP Dialer's CallLogEntryText.java (https://android.googlesource.com/platform/packages/apps/Dialer/+/refs/heads/main/java/com/android/dialer/calllogutils/CallLogEntryText.java).
        Text(
            ", ${getShortTime(post.publishedAtMillis)}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
        // Arrived since the last visit and not yet scrolled past. The dot sits on
        // the line it marks, centred on it, as Fossify Messages' unread_count_badge (https://github.com/FossifyOrg/Messages/blob/main/app/src/main/res/layout/item_conversation.xml).
        NewDot(unread, Modifier.padding(start = 8.dp))
    }
}

@Composable
internal fun ContextLine(text: String, icon: ImageVector? = null) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon?.let {
            Icon(
                it,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

/** Stands in for a picture or video that waits for a tap on mobile data. */
@Composable
private fun HeldMedia(type: MediaType, modifier: Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            if (type == MediaType.PHOTO) MtgaIcons.Download else MtgaIcons.Play,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            when (type) {
                MediaType.PHOTO -> "Photo, tap to load"
                MediaType.GIF -> "GIF, tap to load"
                MediaType.VIDEO -> "Video, tap to load"
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            "Mobile data, Wi-Fi only is on",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
internal fun MediaBlock(post: Post, onDownload: (MediaItem) -> Unit, onOpen: (Int) -> Unit) {
    // Compact trades some picture for a list that moves faster.
    val compact = LocalDisplayPrefs.current.compact
    val hold = rememberMediaPolicy().hold
    // Only the first video or GIF of the post chosen by the list plays inline.
    val inlineIndex = if (LocalInlinePlaying.current == post.id) {
        post.media.indexOfFirst { it.type != MediaType.PHOTO }
    } else {
        -1
    }
    // The pictures of a post apart by SMS's TileGap (PersonActions.kt), as the saved media grid.
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        post.media.forEachIndexed { index, item ->
            // Wi-Fi only on a metered network: nothing is fetched until the
            // reader taps. One tap loads the preview, the next opens it.
            var revealed by remember(item.previewUrl) { mutableStateOf(false) }
            val waiting = hold && !revealed
            // The picture's own ratio, from 3:4 tall to 2:1 wide, 16:9 when the source gave no
            // size (Nitter, RSS); compact keeps its 2:1.
            val sized = (item.width ?: 0) > 0 && (item.height ?: 0) > 0
            val ratio = when {
                compact -> 2f
                sized -> largestAspectRatio(listOf(item)).coerceIn(3f / 4f, 2f)
                else -> 16f / 9f
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(ShapeDefaults.Medium)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable { if (waiting) revealed = true else onOpen(index) }
            ) {
                if (waiting) {
                    HeldMedia(item.type, Modifier.fillMaxWidth().aspectRatio(ratio))
                } else {
                    // A saved copy wins over the network, which is the whole
                    // point of saving it.
                    val source = rememberMediaSource(post.id, index, item)
                    AsyncImage(
                        model = source.preview,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().aspectRatio(ratio)
                    )
                    if (index == inlineIndex) {
                        InlineVideo(
                            url = source.playback,
                            onClick = { onOpen(index) },
                            modifier = Modifier.matchParentSize()
                        )
                    }
                }

                if (item.type != MediaType.PHOTO) {
                    Text(
                        item.durationLabel ?: if (item.type == MediaType.GIF) "GIF" else "Video",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(8.dp)
                            .background(
                                MaterialTheme.colorScheme.surface,
                                ShapeDefaults.ExtraSmall
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                val tap = rememberHaptics()
                IconButton(
                    onClick = {
                        // The file will land somewhere the reader cannot see,
                        // so the phone says it has been asked for.
                        tap.done()
                        onDownload(item)
                    },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .background(MaterialTheme.colorScheme.surface, CircleShape)
                        .size(32.dp)
                ) {
                    Icon(
                        MtgaIcons.Download,
                        contentDescription = "Save to Downloads",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
internal fun QuoteBlock(
    handle: String,
    name: String,
    text: String,
    onClick: () -> Unit,
    note: CommunityNote? = null,
    media: List<MediaItem> = emptyList(),
    /** The tombstone's text when the quoted post is gone: nothing else is drawn. */
    unavailable: String? = null
) {
    val tap = rememberHaptics()
    // AGPL-3.0, ported from Nitter, renderQuote: a quote that is not available is drawn as
    // its unavailable text alone, with no link of its own here (nothing to open).
    // https://github.com/zedeus/nitter/blob/master/src/views/tweet.nim#renderQuote
    if (unavailable != null) {
        Surface(
            shape = ShapeDefaults.Large,
            color = MaterialTheme.colorScheme.innerZone,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                unavailable.ifBlank { "This post is unavailable." },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            )
        }
        return
    }
    Surface(
        onClick = { tap.tick(); onClick() },
        shape = ShapeDefaults.Large,
        color = MaterialTheme.colorScheme.innerZone,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                if (name.isBlank()) "@$handle" else "$name  @$handle",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (text.isNotBlank()) {
                Text(
                    text,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis
                )
            }
            // renderQuote: `if quote.media.len > 0: renderQuoteMedia`, after the text.
            // Each mediaItem is drawn as MTGA's own MediaBlock draws one of a post's, which is
            // where this code comes from (the same media policy, Coil and corners): Wi-Fi only
            // holds the thumb until a tap, then a tap opens the quoted post as the quote does.
            // Nitter's renderPhotoAttachment draws a thumb as a still-image link and
            // renderVideoAttachment its poster with the overlay-duration over it.
            // https://github.com/213YaZ786/MTGA/blob/main/app/src/main/java/com/mtga/app/ui/component/PostCard.kt#L279-L350
            // https://github.com/zedeus/nitter/blob/master/src/views/tweet.nim#renderPhotoAttachment
            // Equal widths, a lone picture at its own ratio (3:4 to 2:1, 16:9 unknown), a row's
            // ratio 4 over the count.
            // not ported: the download button, the inline video and the saved copy of
            // MediaBlock (a quote's media are not saved, its video plays in its own post),
            // named and the webp thumbs, altText, video-download of Nitter.
            val hold = rememberMediaPolicy().hold
            if (media.isNotEmpty()) renderQuoteMedia(media) { photo, count ->
                var revealed by remember(photo.previewUrl) { mutableStateOf(false) }
                val waiting = hold && !revealed
                val ratio = when {
                    count > 1 -> 4f / count
                    (photo.width ?: 0) > 0 && (photo.height ?: 0) > 0 ->
                        largestAspectRatio(listOf(photo)).coerceIn(3f / 4f, 2f)
                    else -> 16f / 9f
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(ShapeDefaults.Medium)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable { if (waiting) revealed = true else { tap.tick(); onClick() } }
                ) {
                    if (waiting && count > 1) {
                        // A tile of a row is too small for HeldMedia's three lines: its icon only.
                        Box(Modifier.fillMaxWidth().aspectRatio(ratio), contentAlignment = Alignment.Center) {
                            Icon(
                                if (photo.type == MediaType.PHOTO) MtgaIcons.Download else MtgaIcons.Play,
                                contentDescription = "Tap to load",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else if (waiting) {
                        HeldMedia(photo.type, Modifier.fillMaxWidth().aspectRatio(ratio))
                    } else {
                        AsyncImage(
                            model = photo.previewUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().aspectRatio(ratio)
                        )
                    }
                    if (photo.type != MediaType.PHOTO) {
                        Text(
                            photo.durationLabel ?: if (photo.type == MediaType.GIF) "GIF" else "Video",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(8.dp)
                                .background(
                                    MaterialTheme.colorScheme.surface,
                                    ShapeDefaults.ExtraSmall
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            note?.let { NoteBlock(it, maxLines = 3) }
        }
    }
}

/**
 * A community note, set apart from the post it qualifies. [text] replaces the
 * plain note text when the caller has made its links tappable. The block has
 * no click of its own, so a tap on it in a list still opens the post.
 */
@Composable
internal fun NoteBlock(note: CommunityNote, text: AnnotatedString? = null, maxLines: Int = Int.MAX_VALUE) {
    Surface(
        shape = ShapeDefaults.Large,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(MtgaIcons.Info, contentDescription = null, modifier = Modifier.size(16.dp))
                Text("Community note", style = MaterialTheme.typography.labelLarge)
            }
            if (text != null) {
                Text(text, style = MaterialTheme.typography.bodyMedium, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
            } else {
                Text(note.text, style = MaterialTheme.typography.bodyMedium, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/**
 * A link preview. Large cards put the picture on top, small ones beside the
 * text, as Nitter lays them out. The picture follows the media policy: on a
 * metered network with Wi-Fi only on, nothing is fetched until the reader
 * taps the placeholder, and a tap on the text still opens the link.
 */
@Composable
internal fun LinkCardBlock(card: LinkCard, onClick: () -> Unit) {
    val hold = rememberMediaPolicy().hold
    var revealed by remember(card.imageUrl) { mutableStateOf(false) }
    val image = card.imageUrl
    val waiting = hold && !revealed

    val tap = rememberHaptics()
    Surface(
        onClick = { tap.tick(); onClick() },
        shape = ShapeDefaults.Large,
        color = MaterialTheme.colorScheme.innerZone,
        modifier = Modifier.fillMaxWidth()
    ) {
        if (card.large || image == null) {
            Column {
                if (image != null) {
                    val ratio = if (LocalDisplayPrefs.current.compact) 2.4f else 1.91f
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(ratio)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    ) {
                        if (waiting) {
                            HeldCardImage(Modifier.matchParentSize().clickable { revealed = true })
                        } else {
                            AsyncImage(
                                model = image,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.matchParentSize()
                            )
                        }
                        if (card.isArticle) ArticleBadge(Modifier.align(Alignment.BottomStart).padding(8.dp))
                    }
                }
                LinkCardText(card, Modifier.padding(12.dp))
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(84.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                ) {
                    if (waiting) {
                        HeldCardImage(Modifier.matchParentSize().clickable { revealed = true }, short = true)
                    } else {
                        AsyncImage(
                            model = image,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.matchParentSize()
                        )
                    }
                }
                LinkCardText(card, Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 8.dp))
            }
        }
    }
}

@Composable
private fun LinkCardText(card: LinkCard, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        card.destination?.let {
            Text(
                it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (card.isArticle && card.imageUrl == null) {
            ArticleBadge(Modifier)
        }
        Text(
            card.title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        card.description?.takeIf { it.isNotBlank() }?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun HeldCardImage(modifier: Modifier, short: Boolean = false) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            MtgaIcons.Download,
            contentDescription = "Load the preview image",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        if (!short) {
            Text(
                "Preview, tap to load",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ArticleBadge(modifier: Modifier) {
    Text(
        "Article",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = modifier
            .background(MaterialTheme.colorScheme.secondaryContainer, ShapeDefaults.ExtraSmall)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

/**
 * A poll, read only. Each choice is a bar filled to its share, the leading
 * one in the accent colour. Voting needs an account, so there is nothing to tap.
 */
@Composable
internal fun PollBlock(poll: Poll) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        poll.options.forEach { option ->
            val fill = if (option.leader) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHighest
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(ShapeDefaults.Small)
                    .background(MaterialTheme.colorScheme.surfaceContainer)
            ) {
                // The fill sits in its own box: under matchParentSize the width
                // is fixed, and a fraction would be clamped back to full width.
                Box(Modifier.matchParentSize()) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(option.percent / 100f)
                            .background(fill)
                    )
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        option.label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (option.leader) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (option.leader) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "${option.percent}%",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (option.leader) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (option.leader) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(start = 12.dp)
                    )
                }
            }
        }
        pollInfo(poll)?.let {
            Text(
                it,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

internal fun pollInfo(poll: Poll): String? {
    val votes = poll.votes?.let { if (it == 1L) "1 vote" else "%,d votes".format(it) }
    // Joined with a comma, as AOSP Dialer's CallLogEntryText.java joinSecondaryTextComponents (https://android.googlesource.com/platform/packages/apps/Dialer/+/refs/heads/main/java/com/android/dialer/calllogutils/CallLogEntryText.java).
    return listOfNotNull(votes, poll.status).joinToString(", ").ifBlank { null }
}

@Composable
internal fun StatsRow(stats: com.mtga.app.core.model.PostStats) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        modifier = Modifier.padding(top = 2.dp)
    ) {
        stats.replies?.let { Stat(MtgaIcons.Comment, it) }
        stats.reposts?.let { Stat(MtgaIcons.Repost, it) }
        stats.likes?.let { Stat(MtgaIcons.Heart, it) }
    }
}

@Composable
private fun Stat(icon: androidx.compose.ui.graphics.vector.ImageVector, value: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            compactCount(value),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// AGPL-3.0, ported from Nitter, renderTweet: the "Replying to" line is drawn only when
// `tweet.reply.len > 1 or tweet.reply[0] != tweet.user.username or pinned`, so a thread
// of the author's own carries no line. The post holds one reply name (relatedHandle,
// the first that is not the author's when there are several), and "Pinned" wins above
// the reply branch.
// https://github.com/zedeus/nitter/blob/master/src/views/tweet.nim#renderTweet
// not ported: index == 0 / afterTweet (thread position, MTGA draws each post alone),
// renderHeader's retweet and pinned header (the repost and pin lines below), the
// rest of renderTweet (PostCard's body draws text, card, media, poll, quote, note, stats).
internal fun Post.contextLine(): String? {
    val tweet = this
    val reply = tweet.relatedHandle
    val username = tweet.authorHandle
    val pinned = tweet.isPinned
    return when {
        pinned -> "Pinned"
        tweet.kind == PostKind.REPOST -> reply?.let { "Reposted by $it" } ?: "Repost"
        tweet.kind == PostKind.REPLY && reply != null && reply.equals(username, ignoreCase = true) -> null
        tweet.kind == PostKind.REPLY -> reply?.let { "Replying to @$it" } ?: "Reply"
        else -> null
    }
}

// Counts as the system writes them short: 1.2K, 12K, 1M, in the device's language.
// Squawker (MIT) holds `numberFormat = NumberFormat.compact(locale: ...)` and calls
// numberFormat.format(count) for replies, reposts and likes; Android's
// CompactDecimalFormat with CompactStyle.SHORT is its counterpart. Under 1000 the count
// is written as it is and the formatter is not touched; [numberFormat] is a parameter so
// a JVM test can stand in for ICU, which the JVM does not have.
// https://github.com/j-fbriere/squawker/blob/master/lib/tweet/tweet.dart#L384
// not ported: numberLocaleFor and Intl.getCurrentLocale (Dart intl locale fix-ups,
// Locale.getDefault() needs none).
internal fun compactCount(
    value: Int,
    numberFormat: (Long) -> String = { count ->
        CompactDecimalFormat.getInstance(Locale.getDefault(), CompactDecimalFormat.CompactStyle.SHORT)
            .format(count)
    }
): String = if (value < 1_000) value.toString() else numberFormat(value.toLong())

// The time on a post's name line, as Nitter writes it: another year "6 Nov 2025", at
// least a day "Nov 6", then "5h", "12m", "30s" and "now". Month names come from the
// device locale (DateTimeFormatter's default format locale).
// AGPL-3.0, ported from Nitter:
// https://github.com/zedeus/nitter/blob/master/src/formatters.nim#getShortTime
// getShortTime(tweet), its one line wrapper, is the Long overload below.
internal fun getShortTime(time: ZonedDateTime, now: ZonedDateTime = ZonedDateTime.now()): String {
    if (time.year == 0) return ""
    val since = Duration.between(time, now)
    val result: String

    if (now.year != time.year) {
        result = time.format(DateTimeFormatter.ofPattern("d MMM yyyy"))
    } else if (since.toDays() >= 1) {
        result = time.format(DateTimeFormatter.ofPattern("MMM d"))
    } else if (since.toHours() >= 1) {
        result = "${since.toHours()}h"
    } else if (since.toMinutes() >= 1) {
        result = "${since.toMinutes()}m"
    } else if (since.seconds > 1) {
        result = "${since.seconds}s"
    } else {
        result = "now"
    }
    return result
}

// AGPL-3.0, ported from Nitter: getShortTime(tweet) hands the post's own time to
// getShortTime, here its epoch millis. https://github.com/zedeus/nitter/blob/master/src/formatters.nim#getShortTime
internal fun getShortTime(millis: Long): String =
    if (millis <= 0L) "" else getShortTime(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

/** Compact relative time. Absolute weeks take over past a week. */
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

// Squawker (MIT), TweetMedia: largestAspectRatio is the media's sizes.large.w over
// sizes.large.h, the largest of them (reduce(math.max)), `?? 1` for a missing side. Only
// called for a media whose size is known: MediaBlock keeps its 16:9 for the others.
// https://github.com/j-fbriere/squawker/blob/master/lib/tweet/_media.dart#L143
internal fun largestAspectRatio(media: List<MediaItem>): Float =
    media.map { e -> (e.width ?: 1).toFloat() / (e.height ?: 1) }.reduce { a, b -> Math.max(a, b) }

// AGPL-3.0, ported from Nitter, renderQuoteMedia: `tdiv(class="quote-media-container"):
// renderMedia(quote.media, prefs, path)`, the quote's media in a container of their own.
// https://github.com/zedeus/nitter/blob/master/src/views/tweet.nim#renderQuoteMedia
// not ported: prefs and path (the media policy is read where each mediaItem is drawn, by the
// caller, in QuoteBlock).
@Composable
internal fun renderQuoteMedia(media: List<MediaItem>, renderItem: @Composable RowScope.(MediaItem, Int) -> Unit) {
    Box { renderMedia(media, renderItem) }
}

// AGPL-3.0, ported from Nitter, renderMedia: nothing for an empty list, else the media in
// groups (a row each, gallery-row) and every mediaItem drawn by its kind. Here one row of up
// to four, equal widths, ten dp apart (the visual the user validated).
// https://github.com/zedeus/nitter/blob/master/src/views/tweet.nim#renderMedia
// not ported: the groups of two for three or more media (distribute(2)), the margin between
// groups and mixed-row (a single row), the single video or gif drawn by renderVideo and
// renderGif (a tile like the others), bigThumb, prefs and path.
@Composable
private fun renderMedia(media: List<MediaItem>, renderItem: @Composable RowScope.(MediaItem, Int) -> Unit) {
    if (media.isEmpty()) return
    val groups = if (media.size < 3) listOf(media) else listOf(media.take(4))
    for (mediaGroup in groups) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            for (mediaItem in mediaGroup) renderItem(mediaItem, media.size)
        }
    }
}
