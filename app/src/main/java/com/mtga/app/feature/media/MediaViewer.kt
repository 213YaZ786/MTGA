package com.mtga.app.feature.media

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import com.mtga.app.core.media.rememberMediaSource
import com.mtga.app.core.model.MediaItem
import com.mtga.app.core.model.MediaType
import com.mtga.app.ui.component.BoldButton
import com.mtga.app.ui.component.BoldIconButton
import com.mtga.app.ui.component.FloatingRoundButton
import com.mtga.app.ui.component.GlassVideo
import com.mtga.app.ui.component.MediaViewerFrame
import com.mtga.app.ui.component.SafePlayer
import com.mtga.app.ui.component.ZoomableImage
import com.mtga.app.ui.component.rememberMediaPolicy
import com.mtga.app.ui.icon.MtgaIcons
import androidx.media3.common.MediaItem as PlayableItem

/**
 * Full screen viewer for the media of one post.
 *
 * Swipe sideways between attachments, pinch or double tap to zoom a photo,
 * swipe down (or back) to close, tap to hide the buttons and the player
 * controls. Videos play through Media3 ExoPlayer, GIFs loop muted like on X.
 * The download button saves the current attachment.
 *
 * The frame (dialog, pager, close, tap to hide) is the reader kit's
 * MediaViewerFrame; the pages and the download are this app's.
 */
@Composable
fun MediaViewer(
    /** The post the media belongs to, so a saved copy can be found. */
    postId: String,
    media: List<MediaItem>,
    startIndex: Int,
    onDownload: (MediaItem) -> Unit,
    onDismiss: () -> Unit
) {
    if (media.isEmpty()) return

    MediaViewerFrame(
        count = media.size,
        startIndex = startIndex,
        onDismiss = onDismiss,
        actions = { current ->
            FloatingRoundButton(icon = MtgaIcons.Download, label = "Save to Downloads", onClick = { onDownload(media[current]) })
        }
    ) { index, active, onZoomChanged ->
        val item = media[index]
        // The saved copy when there is one, so a post read on the
        // train opens without touching the network.
        val source = rememberMediaSource(postId, index, item)
        when (item.type) {
            MediaType.PHOTO -> ZoomableImage(model = source.playback, onZoomChanged = onZoomChanged)
            MediaType.VIDEO, MediaType.GIF -> VideoPage(
                item = item,
                url = source.playback,
                active = active
            )
        }
    }
}

/**
 * One player per page, created when the page is composed and released when
 * it leaves. Plays only while its page is current and the app is in front.
 */
@Composable
private fun VideoPage(item: MediaItem, url: String, active: Boolean) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val policy = rememberMediaPolicy()
    val isGif = item.type == MediaType.GIF
    var failed by remember(url) { mutableStateOf(false) }

    // On mobile data with Wi-Fi only on, nothing is fetched until the reader
    // taps play. Otherwise the player prepares as soon as the page exists.
    // A saved file costs no data, so "Wi-Fi only" never holds it back.
    var started by remember(url) { mutableStateOf(!policy.hold || url.startsWith("/")) }
    var tappedPlay by remember(url) { mutableStateOf(false) }
    // GIFs have no sound. Videos follow the setting, and the button below.
    var muted by remember(url) { mutableStateOf(isGif || policy.startMuted) }

    val exo = remember(url) {
        SafePlayer.build(context).apply {
            setMediaItem(PlayableItem.fromUri(url))
            if (isGif) repeatMode = Player.REPEAT_MODE_ALL
        }
    }

    DisposableEffect(exo) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                failed = true
            }
        }
        exo.addListener(listener)
        onDispose {
            exo.removeListener(listener)
            exo.release()
        }
    }

    LaunchedEffect(exo, started) {
        if (started && exo.playbackState == Player.STATE_IDLE) exo.prepare()
    }

    LaunchedEffect(exo, muted) {
        exo.volume = if (muted) 0f else 1f
    }

    // GIFs always loop. Videos start alone only when autoplay is on, or when
    // the reader just tapped play. With autoplay off the controls' own play
    // button does the rest.
    val playsAlone = isGif || policy.autoplay || tappedPlay
    LifecycleResumeEffect(exo, active, started, playsAlone) {
        if (started && exo.playbackState == Player.STATE_IDLE) exo.prepare()
        if (active && started && playsAlone) exo.play()
        onPauseOrDispose { exo.pause() }
    }
    // Out of sight it stops loading too (a paused player goes on downloading); it goes on from the same place.
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_STOP) { exo.stop() }

    if (failed) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("This video could not be played here.", color = Color.White)
            BoldButton(onClick = { uriHandler.openUri(item.downloadUrl) }, filled = true) {
                Text("Open in browser")
            }
        }
        return
    }

    Box(Modifier.fillMaxSize()) {
        GlassVideo(
            player = exo,
            muted = muted,
            onMutedChange = { muted = it },
            controls = item.type == MediaType.VIDEO && started
        )

        if (!started) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BoldIconButton(
                    onClick = {
                        started = true
                        tappedPlay = true
                    },
                    modifier = Modifier.size(72.dp)
                ) {
                    Icon(
                        MtgaIcons.Play,
                        contentDescription = "Play",
                        tint = Color.White,
                        modifier = Modifier.size(40.dp)
                    )
                }
                Text(
                    "Mobile data, Wi-Fi only is on",
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }

    }
}
