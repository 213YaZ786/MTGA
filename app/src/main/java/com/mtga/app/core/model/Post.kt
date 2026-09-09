package com.mtga.app.core.model

/**
 * One post, normalised.
 *
 * Both the RSS source and the HTML source in step 5 produce this exact type,
 * which is what lets a new source be added without the UI knowing about it.
 * Fields the RSS feed cannot supply are nullable rather than faked.
 */
data class Post(
    val id: String,
    val authorHandle: String,
    val authorName: String,
    val text: String,
    val publishedAtMillis: Long,
    val permalink: String,
    val kind: PostKind = PostKind.ORIGINAL,
    val relatedHandle: String? = null,
    val mediaUrls: List<String> = emptyList(),
    /** Null from RSS. Filled in by the HTML source later. */
    val stats: PostStats? = null
)

enum class PostKind { ORIGINAL, REPOST, REPLY, QUOTE }

data class PostStats(
    val replies: Int? = null,
    val reposts: Int? = null,
    val likes: Int? = null
)

/** A single account's feed as fetched from one instance. */
data class Feed(
    val handle: String,
    val displayName: String,
    val posts: List<Post>,
    val fetchedFromHost: String,
    val fetchedAtMillis: Long
)
