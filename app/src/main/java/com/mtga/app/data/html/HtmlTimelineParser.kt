package com.mtga.app.data.html

import com.mtga.app.core.model.Feed
import com.mtga.app.core.model.Post
import com.mtga.app.core.model.PostKind
import com.mtga.app.core.model.PostStats
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Parses a Nitter profile page into the domain model.
 *
 * Hand written rather than jsoup on purpose. Nitter's markup is small and
 * predictable, this needs no external artifact, and every helper here is
 * tolerant: a missing field yields null rather than throwing, so one markup
 * change costs a field instead of the whole page.
 *
 * Bump SELECTOR_SET_VERSION whenever these expectations change. It travels in
 * ParseFailure so a user can tell us which parser generation broke.
 */
class HtmlTimelineParser {

    fun parse(html: String, handle: String, host: String): Feed? {
        val chunks = html.split("timeline-item")
        if (chunks.size <= 1) return null

        val posts = chunks.drop(1).mapNotNull { chunk -> parseItem(chunk, handle, host) }
        if (posts.isEmpty()) return null

        return Feed(
            handle = handle,
            displayName = extractProfileName(html) ?: handle,
            posts = posts,
            fetchedFromHost = host,
            fetchedAtMillis = System.currentTimeMillis()
        )
    }

    /** The cursor for the next page, when the page offers one. */
    fun extractCursor(html: String): String? =
        html.substringAfterKeyOrNull("show-more")
            ?.substringAfterKeyOrNull("href=\"")
            ?.substringBefore('"')
            ?.substringAfter("cursor=", "")
            ?.takeIf { it.isNotBlank() }
            ?.let(::decodeEntities)

    // ---- item ---------------------------------------------------------------

    private fun parseItem(chunk: String, feedHandle: String, host: String): Post? {
        // Everything after the stats block belongs to the next item.
        val body = chunk.substringBefore("</div>\n    </div>\n  </div>", chunk)

        val permalinkPath = body.attributeAfter("tweet-link", "href=\"") ?: return null
        val text = body.between("tweet-content", ">", "</div>")
            ?.let(::stripTags)
            ?.let(::decodeEntities)
            ?.trim()
            .orEmpty()

        val author = body.attributeAfter("class=\"username\"", "title=\"")
            ?.removePrefix("@")
            ?: feedHandle

        val isRepost = "retweet-header" in chunk
        val replyTo = body.between("replying-to", ">", "</div>")
            ?.let(::stripTags)
            ?.let(::decodeEntities)
            ?.substringAfter('@', "")
            ?.trim()
            ?.takeIf { it.isNotBlank() }

        val kind = when {
            isRepost -> PostKind.REPOST
            replyTo != null -> PostKind.REPLY
            else -> PostKind.ORIGINAL
        }

        val absolute = "https://$host" + permalinkPath.ensureLeadingSlash()

        return Post(
            id = permalinkPath.substringBefore("#").trimEnd('/'),
            authorHandle = author,
            authorName = author,
            text = text,
            publishedAtMillis = parseTimestamp(body),
            permalink = absolute.substringBefore("#"),
            kind = kind,
            relatedHandle = replyTo ?: if (isRepost) feedHandle else null,
            mediaUrls = extractMedia(body, host),
            stats = extractStats(body)
        )
    }

    /**
     * The date anchor carries an absolute timestamp in its title, which is far
     * better than the "2h" shown to the reader.
     */
    private fun parseTimestamp(body: String): Long {
        val raw = body.attributeAfter("tweet-date", "title=\"") ?: return 0L
        val cleaned = decodeEntities(raw).replace("·", "").replace(Regex("\\s+"), " ").trim()
        for (pattern in TIMESTAMP_PATTERNS) {
            val parsed = runCatching {
                LocalDateTime.parse(cleaned, DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH))
                    .toInstant(ZoneOffset.UTC)
                    .toEpochMilli()
            }.getOrNull()
            if (parsed != null) return parsed
        }
        return 0L
    }

    private fun extractStats(body: String): PostStats? {
        val block = body.substringAfterKeyOrNull("tweet-stats") ?: return null
        val stats = PostStats(
            replies = block.statAfter("icon-comment"),
            reposts = block.statAfter("icon-retweet"),
            likes = block.statAfter("icon-heart")
        )
        return if (stats.replies == null && stats.reposts == null && stats.likes == null) {
            null
        } else {
            stats
        }
    }

    /** Counts sit as bare text just after the icon span, sometimes with separators. */
    private fun String.statAfter(iconClass: String): Int? {
        val at = indexOf(iconClass).takeIf { it >= 0 } ?: return null
        val window = substring(at, minOf(at + 200, length))
        val digits = stripTags(window).filter { it.isDigit() || it == ',' }.replace(",", "")
        return digits.takeIf { it.isNotBlank() }?.toIntOrNull()
    }

    private fun extractMedia(body: String, host: String): List<String> {
        val results = mutableListOf<String>()
        var cursor = 0
        while (true) {
            val at = body.indexOf("<img", cursor, ignoreCase = true)
            if (at < 0) break
            val srcAt = body.indexOf("src=\"", at)
            if (srcAt < 0) break
            val end = body.indexOf('"', srcAt + 5)
            if (end < 0) break
            val raw = decodeEntities(body.substring(srcAt + 5, end))
            // Avatars are chrome, not content.
            if ("profile_images" !in raw && "avatar" !in raw) {
                results += if (raw.startsWith("http")) raw else "https://$host${raw.ensureLeadingSlash()}"
            }
            cursor = end
        }
        return results.distinct()
    }

    private fun extractProfileName(html: String): String? =
        html.between("profile-card-fullname", ">", "</a>")
            ?.let(::stripTags)
            ?.let(::decodeEntities)
            ?.trim()
            ?.takeIf { it.isNotBlank() }

    // ---- tolerant string helpers -------------------------------------------

    private fun String.substringAfterKeyOrNull(key: String): String? {
        val at = indexOf(key)
        return if (at < 0) null else substring(at + key.length)
    }

    /** Value of an attribute appearing shortly after a marker. */
    private fun String.attributeAfter(marker: String, attribute: String): String? {
        val markerAt = indexOf(marker).takeIf { it >= 0 } ?: return null
        val attrAt = indexOf(attribute, markerAt).takeIf { it >= 0 } ?: return null
        // Guard against matching an attribute belonging to a far away element.
        if (attrAt - markerAt > 400) return null
        val start = attrAt + attribute.length
        val end = indexOf('"', start).takeIf { it >= 0 } ?: return null
        return substring(start, end)
    }

    private fun String.between(marker: String, open: String, close: String): String? {
        val markerAt = indexOf(marker).takeIf { it >= 0 } ?: return null
        val openAt = indexOf(open, markerAt).takeIf { it >= 0 } ?: return null
        val closeAt = indexOf(close, openAt).takeIf { it >= 0 } ?: return null
        return substring(openAt + open.length, closeAt)
    }

    private fun stripTags(input: String): String {
        val out = StringBuilder(input.length)
        var inTag = false
        for (c in input) {
            when {
                c == '<' -> inTag = true
                c == '>' -> inTag = false
                !inTag -> out.append(c)
            }
        }
        return out.toString()
    }

    private fun decodeEntities(input: String): String = input
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&apos;", "'")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")

    private fun String.ensureLeadingSlash(): String = if (startsWith("/")) this else "/$this"

    companion object {
        const val SELECTOR_SET_VERSION = 2

        private val TIMESTAMP_PATTERNS = listOf(
            "MMM d, yyyy h:mm a zzz",
            "MMM d, yyyy h:mm a",
            "d MMM yyyy h:mm a zzz",
            "MMM d, yyyy HH:mm zzz"
        )
    }
}
