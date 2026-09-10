package com.mtga.app.core.model

/**
 * One post, normalised.
 *
 * Every source produces this exact type, which is what lets a source be swapped
 * without the UI knowing. Fields a given source cannot supply stay null rather
 * than being faked, so the UI can hide what it does not have instead of showing
 * a plausible lie.
 */
data class Post(
    val id: String,
    val authorHandle: String,
    val authorName: String,
    val avatarUrl: String? = null,
    val text: String,
    val links: List<String> = emptyList(),
    val publishedAtMillis: Long,
    val permalink: String,
    val kind: PostKind = PostKind.ORIGINAL,
    val relatedHandle: String? = null,
    val isPinned: Boolean = false,
    val media: List<MediaItem> = emptyList(),
    val quoted: QuotedPost? = null,
    val card: LinkCard? = null,
    val stats: PostStats? = null
)

enum class PostKind { ORIGINAL, REPOST, REPLY, QUOTE }

enum class MediaType { PHOTO, VIDEO, GIF }

/**
 * [previewUrl] is what gets shown, [downloadUrl] is the full resolution
 * original. Nitter serves both, and conflating them means either a blurry
 * gallery or a very slow timeline.
 */
data class MediaItem(
    val previewUrl: String,
    val downloadUrl: String,
    val type: MediaType,
    val durationLabel: String? = null
)

data class QuotedPost(
    val handle: String,
    val name: String,
    val text: String,
    val permalink: String
)

data class LinkCard(
    val title: String,
    val description: String?,
    val destination: String?,
    val imageUrl: String?,
    val url: String?
)

data class PostStats(
    val replies: Int? = null,
    val reposts: Int? = null,
    val likes: Int? = null,
    val views: Int? = null
)

/** A single account's feed as fetched from one instance. */
data class Feed(
    val handle: String,
    val displayName: String,
    val posts: List<Post>,
    val fetchedFromHost: String,
    val fetchedAtMillis: Long,
    val avatarUrl: String? = null,
    val bio: String? = null,
    val nextCursor: String? = null
)
