package com.mtga.app.core.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import com.mtga.app.core.model.MediaItem

/**
 * The saved media folder, for the composables that show media.
 *
 * Null when nothing provided it, which keeps previews and tests working: the
 * helper below then simply answers with the network address, which is what
 * the app did before any of this existed.
 */
val LocalOfflineMedia = compositionLocalOf<OfflineMedia?> { null }

/**
 * Where a picture should be loaded from and where a video should be played
 * from, saved copy first.
 *
 * [preview] and [playback] differ for a video: the file on disk is the video,
 * the preview is a still that is never saved, so offline a video post plays
 * but shows no thumbnail until Coil's own picture cache is asked. For a photo
 * both point at the same saved file.
 */
data class MediaSource(val preview: String, val playback: String, val offline: Boolean)

/**
 * Reads the in memory index, so this costs a map lookup and is safe to call
 * for every attachment of every visible post.
 */
@Composable
fun rememberMediaSource(postId: String, position: Int, item: MediaItem): MediaSource {
    val store = LocalOfflineMedia.current
    return remember(store, postId, position, item.downloadUrl) {
        val local = store?.localPath(postId, position)
        if (local == null) {
            MediaSource(preview = item.previewUrl, playback = item.downloadUrl, offline = false)
        } else {
            MediaSource(
                // A saved photo is its own preview. A saved video is not, so
                // the still keeps its network address and fails quietly when
                // there is no network, exactly as it would have anyway.
                preview = if (item.isPicture()) local else item.previewUrl,
                playback = local,
                offline = true
            )
        }
    }
}

private fun MediaItem.isPicture() = type == com.mtga.app.core.model.MediaType.PHOTO
