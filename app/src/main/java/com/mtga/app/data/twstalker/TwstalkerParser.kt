package com.mtga.app.data.twstalker

import com.mtga.app.core.model.Feed
import com.mtga.app.core.model.MediaItem
import com.mtga.app.core.model.MediaType
import com.mtga.app.core.model.Post
import com.mtga.app.core.model.PostKind
import com.mtga.app.core.model.PostStats

/**
 * Parses a twstalker profile page.
 *
 * Only page one needs parsing. The page carries the numeric user id and the
 * first cursor, after which everything comes from the site's own JSON endpoint,
 * so the fragile part of this integration is a single request deep.
 */
class TwstalkerParser {

    data class ProfilePage(
        val feed: Feed,
        val userId: String?,
        val cursor: String?
    )

    fun parseProfile(html: String, handle: String): ProfilePage? {
        val tabStart = html.indexOf("id=\"tweets-tabs\"").takeIf { it >= 0 } ?: return null
        val tabEnd = html.indexOf("id=\"nav-followers\"", tabStart).takeIf { it > 0 } ?: html.length
        val body = html.substring(tabStart, tabEnd)

        val posts = body.split("class=\"activity-posts\"")
            .drop(1)
            .mapNotNull { parsePost(it) }
            // Quote blocks are nested activity-posts, so they show up as their
            // own chunk. Keeping only this profile's own posts drops them,
            // since the quoted author is by definition someone else.
            .filter { it.authorHandle.equals(handle, ignoreCase = true) }
            .distinctBy { it.id }

        if (posts.isEmpty()) return null

        return ProfilePage(
            feed = Feed(
                handle = handle,
                displayName = extractDisplayName(html) ?: handle,
                posts = posts.sortedByDescending { it.publishedAtMillis },
                fetchedFromHost = HOST,
                fetchedAtMillis = System.currentTimeMillis(),
                avatarUrl = posts.firstOrNull()?.avatarUrl,
                bio = null
            ),
            userId = html.attributeAfter("class=\"add-nw-event\"", "data-query=\""),
            cursor = html.attributeAfter("class=\"add-nw-event\"", "data-cursor=\"")
        )
    }

    private fun parsePost(chunk: String): Post? {
        val statusPath = chunk.firstStatusPath() ?: return null
        val handle = statusPath.substringBefore("/status/").trim('/')
        val id = statusPath.substringAfterLast('/')

        val descStart = chunk.indexOf("class=\"activity-descp\"").takeIf { it >= 0 } ?: return null
        val textOpen = chunk.indexOf("<p>", descStart).takeIf { it >= 0 } ?: return null
        val textClose = chunk.indexOf("</p>", textOpen).takeIf { it >= 0 } ?: return null
        val textHtml = chunk.substring(textOpen + 3, textClose)

        return Post(
            id = id,
            authorHandle = handle,
            authorName = chunk.between("<h4>", "<")?.trim()?.let(::decodeEntities)?.ifBlank { handle }
                ?: handle,
            avatarUrl = chunk.attributeAfter("main-user-dts1", "src=\""),
            text = stripTags(textHtml).let(::decodeEntities).trim(),
            links = extractLinks(textHtml),
            publishedAtMillis = snowflakeToMillis(id),
            permalink = "https://$HOST/$statusPath".replace("//$HOST//", "//$HOST/"),
            kind = if ("fa-retweet\"><span>" in chunk) PostKind.REPOST else PostKind.ORIGINAL,
            media = extractMedia(chunk),
            stats = extractStats(chunk),
            quoted = null
        )
    }

    /**
     * Twitter status ids are snowflakes: the top 41 bits are milliseconds since
     * the Twitter epoch. That gives an exact timestamp from the permalink, which
     * beats parsing "6 hours ago" and never drifts.
     */
    fun snowflakeToMillis(id: String): Long {
        val numeric = id.toLongOrNull() ?: return 0L
        return (numeric shr 22) + TWITTER_EPOCH_MS
    }

    private fun extractMedia(chunk: String): List<MediaItem> {
        val items = mutableListOf<MediaItem>()

        var cursor = 0
        while (true) {
            val at = chunk.indexOf("data-image=\"", cursor).takeIf { it >= 0 } ?: break
            val end = chunk.indexOf('"', at + 12).takeIf { it >= 0 } ?: break
            val url = decodeEntities(chunk.substring(at + 12, end))
            if (url.startsWith("http")) {
                items += MediaItem(previewUrl = url, downloadUrl = url, type = MediaType.PHOTO)
            }
            cursor = end
        }

        cursor = 0
        while (true) {
            val at = chunk.indexOf("<video", cursor).takeIf { it >= 0 } ?: break
            val block = chunk.substring(at, minOf(at + 1_200, chunk.length))
            val poster = block.attributeAfter("<video", "poster=\"")
            val isGif = "autoplay" in block || "tweet_video" in block
            val src = block.attributeAfter("<source", "src=\"")
                ?: block.attributeAfter("<video", "src=\"")
            if (src != null) {
                items += MediaItem(
                    previewUrl = poster ?: src,
                    downloadUrl = downloadHostFor(src),
                    type = if (isGif) MediaType.GIF else MediaType.VIDEO
                )
            }
            cursor = at + 6
        }

        return items.distinctBy { it.downloadUrl }
    }

    /** Video files are served for download from a different host, same path. */
    fun downloadHostFor(url: String): String =
        url.replace("//pbs.twimg.com/", "//video-s.twimg.com/")

    private fun extractStats(chunk: String): PostStats? {
        val at = chunk.indexOf("class=\"left-comments\"").takeIf { it >= 0 } ?: return null
        val block = chunk.substring(at, minOf(at + 2_500, chunk.length))
        val stats = PostStats(
            replies = block.countAfter("fa-comment"),
            reposts = block.countAfter("fa-retweet"),
            likes = block.countAfter("fa-heart"),
            views = block.countAfter("fa-chart-simple")
        )
        return stats.takeIf {
            it.replies != null || it.reposts != null || it.likes != null || it.views != null
        }
    }

    /** Counts sit as text right after an empty ins element following the icon. */
    private fun String.countAfter(iconClass: String): Int? {
        val at = indexOf(iconClass).takeIf { it >= 0 } ?: return null
        val insAt = indexOf("<ins></ins>", at).takeIf { it >= 0 } ?: return null
        val stop = indexOf('<', insAt + 11).takeIf { it >= 0 } ?: return null
        return parseCompact(substring(insAt + 11, stop).trim())
    }

    /** The site pre-formats large numbers as 4K or 26K or 1.2M. */
    fun parseCompact(raw: String): Int? {
        if (raw.isBlank()) return null
        val cleaned = raw.replace(",", "").trim()
        val multiplier = when (cleaned.lastOrNull()) {
            'K', 'k' -> 1_000
            'M', 'm' -> 1_000_000
            'B', 'b' -> 1_000_000_000
            else -> 1
        }
        val number = if (multiplier == 1) cleaned else cleaned.dropLast(1)
        return number.toDoubleOrNull()?.times(multiplier)?.toInt()
    }

    private fun extractDisplayName(html: String): String? =
        html.between("<title>", "@")?.trim()?.takeIf { it.isNotBlank() }?.let(::decodeEntities)

    // ---- small helpers ------------------------------------------------------

    private fun String.firstStatusPath(): String? {
        val at = indexOf("/status/").takeIf { it >= 0 } ?: return null
        val start = lastIndexOf("href=\"", at).takeIf { it >= 0 } ?: return null
        val end = indexOf('"', start + 6).takeIf { it >= 0 } ?: return null
        return substring(start + 6, end).trim('/').takeIf { "/status/" in it }
    }

    private fun String.attributeAfter(marker: String, attribute: String): String? {
        val markerAt = indexOf(marker).takeIf { it >= 0 } ?: return null
        val attrAt = indexOf(attribute, markerAt).takeIf { it >= 0 } ?: return null
        val end = indexOf('"', attrAt + attribute.length).takeIf { it >= 0 } ?: return null
        return decodeEntities(substring(attrAt + attribute.length, end))
    }

    private fun String.between(open: String, close: String): String? {
        val openAt = indexOf(open).takeIf { it >= 0 } ?: return null
        val closeAt = indexOf(close, openAt + open.length).takeIf { it >= 0 } ?: return null
        return substring(openAt + open.length, closeAt)
    }

    private fun extractLinks(html: String): List<String> {
        val out = mutableListOf<String>()
        var cursor = 0
        while (true) {
            val at = html.indexOf("href=\"", cursor).takeIf { it >= 0 } ?: break
            val end = html.indexOf('"', at + 6).takeIf { it >= 0 } ?: break
            val href = decodeEntities(html.substring(at + 6, end))
            if (href.startsWith("http")) out += href
            cursor = end
        }
        return out.distinct()
    }

    fun stripTags(input: String): String {
        val out = StringBuilder(input.length)
        var inTag = false
        var index = 0
        while (index < input.length) {
            val c = input[index]
            when {
                c == '<' -> {
                    val lower = input.substring(index, minOf(index + 4, input.length)).lowercase()
                    if (lower.startsWith("<br") || lower.startsWith("<p")) out.append('\n')
                    inTag = true
                }
                c == '>' -> inTag = false
                !inTag -> out.append(c)
            }
            index++
        }
        return out.toString().lines().joinToString("\n") { it.trim() }
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }

    fun decodeEntities(input: String): String = input
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&apos;", "'")
        .replace("&#160;", " ")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")

    companion object {
        const val HOST = "twstalker.com"
        private const val TWITTER_EPOCH_MS = 1_288_834_974_657L
    }
}
