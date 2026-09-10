package com.mtga.app.feature.post

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mtga.app.core.media.MediaDownloader
import com.mtga.app.data.settings.SettingsStore
import com.mtga.app.core.model.Post
import com.mtga.app.core.model.PostStats
import com.mtga.app.feature.media.MediaViewer
import com.mtga.app.ui.component.Avatar
import com.mtga.app.ui.component.ContextLine
import com.mtga.app.ui.component.LinkCardBlock
import com.mtga.app.ui.component.MediaBlock
import com.mtga.app.ui.component.QuoteBlock
import com.mtga.app.ui.component.compactCount
import com.mtga.app.ui.component.contextLine
import com.mtga.app.ui.icon.MtgaIcons
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * One post, read in full. Everything comes from the local cache, so opening a
 * post is instant and costs no request. Text is selectable, mentions open the
 * profile in the app, links open the real URL, and sharing uses the x.com
 * address so the recipient needs nothing special to open it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostDetailScreen(
    id: String,
    from: String?,
    onBack: () -> Unit,
    onOpenProfile: (String) -> Unit,
    viewModel: PostDetailViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(id) { viewModel.load(id, from) }

    val post = (state as? PostDetailState.Ready)?.post

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Post") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(MtgaIcons.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (post != null) {
                        IconButton(onClick = { share(context, xUrl(post)) }) {
                            Icon(MtgaIcons.Share, contentDescription = "Share")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val current = state) {
                PostDetailState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                PostDetailState.Missing -> Text(
                    "This post is no longer in the local history. Refresh the account it came from.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(32.dp)
                )
                is PostDetailState.Ready -> PostBody(current.post, onOpenProfile)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PostBody(post: Post, onOpenProfile: (String) -> Unit) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val downloader: MediaDownloader = koinInject()
    val settingsStore: SettingsStore = koinInject()
    val settings by settingsStore.settings.collectAsState()
    var viewing by remember { mutableStateOf<Int?>(null) }

    viewing?.let { index ->
        MediaViewer(
            media = post.media,
            startIndex = index,
            onDownload = { downloader.download(it, post.authorHandle) },
            onDismiss = { viewing = null }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        post.contextLine()?.let { ContextLine(it) }

        Surface(
            onClick = { onOpenProfile(post.authorHandle) },
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Row(
                modifier = Modifier.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Avatar(url = post.avatarUrl, name = post.authorName, size = 48.dp)
                Column {
                    Text(
                        post.authorName.ifBlank { "@${post.authorHandle}" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "@${post.authorHandle}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (post.text.isNotBlank()) {
            val linkColor = MaterialTheme.colorScheme.primary
            val annotated = remember(post.id, linkColor) {
                linkify(post.text, post.links, linkColor, onOpenProfile)
            }
            SelectionContainer {
                Text(
                    annotated,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp, lineHeight = 27.sp)
                )
            }
        }

        if (post.media.isNotEmpty()) {
            MediaBlock(
                post = post,
                onDownload = { downloader.download(it, post.authorHandle) },
                onOpen = { viewing = it }
            )
        }

        post.quoted?.let { quote ->
            QuoteBlock(
                handle = quote.handle,
                name = quote.name,
                text = quote.text,
                onClick = { uriHandler.openUri(quote.permalink) }
            )
        }

        post.card?.let { card ->
            LinkCardBlock(
                title = card.title,
                description = card.description,
                destination = card.destination,
                onClick = { card.url?.let(uriHandler::openUri) }
            )
        }

        fullDate(post.publishedAtMillis)?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        post.stats?.takeIf { settings.showCounts }?.let { stats ->
            HorizontalDivider()
            StatsLine(stats)
            HorizontalDivider()
        }

        val url = xUrl(post)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilledTonalButton(onClick = { uriHandler.openUri(url) }) { Text("Open on X") }
            OutlinedButton(onClick = { share(context, url) }) { Text("Share") }
            OutlinedButton(onClick = { copy(context, url) }) { Text("Copy link") }
        }
    }
}

/** Every count the source gave, spelled out. Counts it did not give are not guessed. */
@Composable
private fun StatsLine(stats: PostStats) {
    val parts = listOfNotNull(
        stats.replies?.let { "${compactCount(it)} replies" },
        stats.reposts?.let { "${compactCount(it)} reposts" },
        stats.likes?.let { "${compactCount(it)} likes" },
        stats.views?.let { "${compactCount(it)} views" }
    )
    if (parts.isEmpty()) return
    Text(
        parts.joinToString("   "),
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
}

/** The post on x.com, which anyone can open. Falls back to the source permalink. */
private fun xUrl(post: Post): String =
    if (post.id.isNotEmpty() && post.id.all(Char::isDigit)) {
        "https://x.com/${post.authorHandle}/status/${post.id}"
    } else {
        post.permalink
    }

private fun fullDate(millis: Long): String? {
    if (millis <= 0L) return null
    return DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochMilli(millis))
}

private fun share(context: Context, url: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, url)
    }
    runCatching { context.startActivity(Intent.createChooser(send, null)) }
}

/** Android 13 and later confirm the copy themselves, so no toast is added. */
private fun copy(context: Context, url: String) {
    context.getSystemService(ClipboardManager::class.java)
        ?.setPrimaryClip(ClipData.newPlainText("Post link", url))
}
