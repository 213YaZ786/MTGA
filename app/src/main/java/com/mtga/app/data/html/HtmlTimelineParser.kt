package com.mtga.app.data.html

import com.mtga.app.core.model.CommunityNote
import com.mtga.app.core.model.Conversation
import com.mtga.app.core.model.Feed
import com.mtga.app.core.model.LinkCard
import com.mtga.app.core.model.Poll
import com.mtga.app.core.model.PollOption
import com.mtga.app.core.model.MediaItem
import com.mtga.app.core.model.MediaType
import com.mtga.app.core.model.Post
import com.mtga.app.core.model.PostId
import com.mtga.app.core.model.PostKind
import com.mtga.app.core.model.PostStats
import com.mtga.app.core.model.QuotedPost
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Parses a Nitter profile page into the domain model.
 *
 * Written against Nitter's own view templates rather than guessed from a
 * rendered page, so the markers below are the real ones: timeline-item carries
 * data-username, tweet text lives in a div classed "tweet-content media-body",
 * stats sit inside icon-container after an icon span, photos expose their
 * original through a still-image anchor, and videos ship a ready made
 * video-download link.
 *
 * Every extractor returns null rather than throwing. One markup change should
 * cost a field, not a page.
 */
class HtmlTimelineParser {

    fun parse(html: String, handle: String, host: String): Feed? {
        val chunks = html.split("class=\"timeline-item")
        if (chunks.size <= 1) return null

        val parsed = chunks.drop(1).mapNotNull { chunk -> parseItem(chunk, handle, host) }
        if (parsed.isEmpty()) return null

        // Nitter serves the pinned post first regardless of age. Keep it first
        // but label it, and order the rest newest first, because a feed sorted
        // by whatever the server happened to emit reads as broken.
        val ordered = parsed.filter { it.isPinned } +
            parsed.filterNot { it.isPinned }.sortedByDescending { it.publishedAtMillis }

        val card = ProfileCardParser.parse(html, host)
        return Feed(
            handle = handle,
            displayName = extractProfileName(html) ?: handle,
            posts = ordered,
            fetchedFromHost = host,
            fetchedAtMillis = System.currentTimeMillis(),
            avatarUrl = extractProfileAvatar(html, host),
            bio = extractProfileBio(html),
            nextCursor = extractCursor(html),
            bannerUrl = card.bannerUrl,
            location = card.location,
            website = card.website,
            joined = card.joined,
            stats = card.stats
        )
    }

    /**
     * Parses a post's own page: the posts it answers, the post, the author's
     * thread under it, then the replies. Section markers come from Nitter's
     * status.nim: "main-thread" holds "before-tweet", "main-tweet" and
     * "after-tweet", then "replies" holds one "reply thread" per chain, and
     * "related-tweets" (ignored here) may follow.
     */
    fun parseConversation(html: String, host: String): Conversation? {
        val mainAt = html.indexOf("class=\"main-tweet\"").takeIf { it >= 0 } ?: return null
        val repliesAt = html.indexOf("class=\"replies\"").takeIf { it >= 0 }
        val relatedAt = html.indexOf("class=\"related-tweets\"").takeIf { it >= 0 }
        val threadEnd = listOfNotNull(repliesAt, relatedAt).minOrNull() ?: html.length

        val before = itemsIn(html.substring(0, mainAt), host)
        val fromMain = itemsIn(html.substring(mainAt, threadEnd), host)
        val main = fromMain.firstOrNull()

        val replies = if (repliesAt == null) {
            emptyList()
        } else {
            html.substring(repliesAt, relatedAt?.takeIf { it > repliesAt } ?: html.length)
                .split("class=\"reply thread")
                .drop(1)
                .map { itemsIn(it, host) }
                .filter { it.isNotEmpty() }
        }

        if (main == null && before.isEmpty() && replies.isEmpty()) return null
        return Conversation(
            ancestors = before,
            main = main,
            continuation = fromMain.drop(1),
            replies = replies,
            host = host
        )
    }

    /**
     * Why a post page shows no post, in the server's words, or null when the
     * page is not saying that. Two shapes exist in Nitter: an error page
     * ("Tweet not found", or X's tombstone such as a deletion notice) in an
     * error-panel, and a main-tweet drawn as an unavailable box. Only the
     * main post counts, an unavailable ancestor is normal in a thread.
     */
    fun unavailableReason(html: String): String? {
        html.indexOf("class=\"error-panel\"").takeIf { it >= 0 }?.let { at ->
            // X's tombstones end with a "Learn more" link, useless without it.
            return html.spanText(at)?.removeSuffix("Learn more")?.trim()?.takeIf { it.isNotEmpty() } ?: "Not found"
        }
        val mainAt = html.indexOf("class=\"main-tweet\"").takeIf { it >= 0 } ?: return null
        val afterMain = html.indexOf("class=\"after-tweet", mainAt).takeIf { it >= 0 }
            ?: html.indexOf("class=\"replies\"", mainAt).takeIf { it >= 0 }
            ?: html.length
        val main = html.substring(mainAt, afterMain)
        if ("unavailable timeline-item" !in main) return null
        return main.elementText("class=\"unavailable-box\"", "</a>") ?: "This post is unavailable"
    }

    private fun itemsIn(section: String, host: String): List<Post> =
        section.split("class=\"timeline-item").drop(1).mapNotNull { chunk ->
            val author = chunk.substringAfter("data-username=\"", "").substringBefore('"')
            parseItem(chunk, author, host)
        }

    fun extractCursor(html: String): String? {
        val showMoreAt = html.lastIndexOf("class=\"show-more\"").takeIf { it >= 0 } ?: return null
        val hrefAt = html.indexOf("href=\"", showMoreAt).takeIf { it >= 0 } ?: return null
        val end = html.indexOf('"', hrefAt + 6).takeIf { it >= 0 } ?: return null
        return decodeEntities(html.substring(hrefAt + 6, end))
            .substringAfter("cursor=", "")
            .takeIf { it.isNotBlank() }
    }

    // ---- one item -----------------------------------------------------------

    private fun parseItem(chunk: String, feedHandle: String, host: String): Post? {
        val permalinkPath = chunk.attributeNear("class=\"tweet-link\"", "href=\"", 200)
            ?: chunk.attributeNear("class=\"tweet-date\"", "href=\"", 400)
            ?: return null

        // The quote block repeats tweet-name-row and tweet-date, so the main
        // body is everything before it.
        val quoteAt = chunk.indexOf("class=\"quote")
        val body = if (quoteAt > 0) chunk.substring(0, quoteAt) else chunk
        // Nitter draws the post's own community note after the quote, so it
        // is looked for past the end of the quote block, never inside it.
        val quoteEnd = if (quoteAt > 0) chunk.blockEnd(quoteAt) else -1
        val afterQuote = if (quoteEnd > 0) chunk.substring(quoteEnd) else ""

        val handle = chunk.attributeNear("data-username=\"", "", 0)
            ?: body.attributeNear("class=\"username\"", "title=\"", 300)?.removePrefix("@")
            ?: feedHandle

        val name = body.attributeNear("class=\"fullname\"", "title=\"", 300)
            ?.let(::decodeEntities)
            ?: handle

        val contentHtml = body.tagContent("class=\"tweet-content")
        val text = contentHtml?.let(::htmlToText).orEmpty()

        val isRepost = "class=\"retweet-header\"" in chunk
        val replyTo = body.tagContent("class=\"replying-to\"")
            ?.let(::htmlToText)
            ?.substringAfter('@', "")
            ?.substringBefore(' ')
            ?.trim()
            ?.takeIf { it.isNotBlank() }

        return Post(
            id = PostId.normalize(permalinkPath),
            authorHandle = handle,
            authorName = name,
            avatarUrl = body.attributeNear("class=\"tweet-avatar\"", "src=\"", 300)
                ?.let { absolute(it, host) },
            text = text,
            links = contentHtml?.let(::extractLinks).orEmpty(),
            publishedAtMillis = parseTimestamp(body),
            permalink = absolute(permalinkPath.substringBefore("#"), host),
            kind = when {
                isRepost -> PostKind.REPOST
                replyTo != null -> PostKind.REPLY
                quoteAt > 0 -> PostKind.QUOTE
                else -> PostKind.ORIGINAL
            },
            relatedHandle = replyTo ?: if (isRepost) retweeterOf(chunk) else null,
            isPinned = "class=\"pinned\"" in chunk,
            media = extractMedia(body, host),
            quoted = if (quoteAt > 0) parseQuote(chunk.substring(quoteAt), host) else null,
            card = parseCard(body, host),
            poll = parsePoll(body),
            note = parseNote(if (quoteAt > 0) afterQuote else body),
            stats = extractStats(chunk)
        )
    }

    private fun retweeterOf(chunk: String): String? =
        chunk.tagContent("class=\"retweet-header\"")
            ?.let(::htmlToText)
            ?.substringBefore(" retweeted")
            ?.trim()
            ?.takeIf { it.isNotBlank() }

    private fun parseQuote(quoteChunk: String, host: String): QuotedPost? {
        val link = quoteChunk.attributeNear("class=\"quote-link\"", "href=\"", 200) ?: return null
        return QuotedPost(
            handle = quoteChunk.attributeNear("class=\"username\"", "title=\"", 400)
                ?.removePrefix("@").orEmpty(),
            name = quoteChunk.attributeNear("class=\"fullname\"", "title=\"", 400)
                ?.let(::decodeEntities).orEmpty(),
            text = quoteChunk.tagContent("class=\"quote-text\"")?.let(::htmlToText).orEmpty(),
            permalink = absolute(link.substringBefore("#"), host),
            note = parseNote(quoteChunk.substring(0, quoteChunk.blockEnd(0).takeIf { it > 0 } ?: quoteChunk.length))
        )
    }

    /**
     * A community-note block holds a header ("Community note") and a
     * community-note-text div with the note itself, links included.
     */
    private fun parseNote(section: String): CommunityNote? {
        val at = section.indexOf("class=\"community-note\"").takeIf { it >= 0 } ?: return null
        val html = section.substring(at).tagContent("class=\"community-note-text\"") ?: return null
        val text = htmlToText(html).takeIf { it.isNotBlank() } ?: return null
        return CommunityNote(text = text, links = extractLinks(html))
    }

    /**
     * Index just past the div that contains [markerAt], balancing nested divs.
     * -1 when the markup is cut short, so callers can fall back safely.
     */
    private fun String.blockEnd(markerAt: Int): Int {
        val open = indexOf('>', markerAt).takeIf { it >= 0 } ?: return -1
        var depth = 1
        var cursor = open + 1
        while (cursor < length) {
            val nextOpen = indexOf("<div", cursor)
            val nextClose = indexOf("</div", cursor)
            if (nextClose < 0) return -1
            if (nextOpen in 0 until nextClose) {
                depth++
                cursor = nextOpen + 4
            } else {
                depth--
                val end = indexOf('>', nextClose).takeIf { it >= 0 } ?: return -1
                if (depth == 0) return end + 1
                cursor = end + 1
            }
        }
        return -1
    }

    private fun parseCard(body: String, host: String): LinkCard? {
        if ("class=\"card" !in body && "class=\"article-card" !in body) return null
        val title = body.elementText("class=\"card-title\"", "</h2>")?.takeIf {
            it.isNotBlank()
        } ?: return null
        // Relative for X articles ("/i/article/<id>"), absolute otherwise. A
        // titled video attachment reuses card-title without a link container,
        // so its url stays null and the card simply does not open anything.
        val url = body.attributeNear("class=\"card-container\"", "href=\"", 200)
            ?.let { absolute(it, host) }
        val isArticle = "class=\"article-card" in body
        return LinkCard(
            title = title,
            description = body.elementText("class=\"card-description\"", "</p>"),
            destination = body.elementText("class=\"card-destination\"", "</span>"),
            imageUrl = body.attributeNear("class=\"card-image\"", "src=\"", 300)
                ?.let { absolute(it, host) },
            url = url,
            // Nitter marks the wide layout with "card large". Articles are always large.
            large = isArticle || "card large\"" in body,
            isArticle = isArticle
        )
    }

    /**
     * Nitter draws a poll as one poll-meter per choice, the leader flagged,
     * each with a percentage and a label in spans, then one poll-info line
     * such as "1,234 votes • Final results".
     */
    private fun parsePoll(body: String): Poll? {
        val start = body.indexOf("class=\"poll\"").takeIf { it >= 0 } ?: return null
        val infoAt = body.indexOf("class=\"poll-info\"", start)
        val end = if (infoAt > 0) infoAt else body.length
        val options = mutableListOf<PollOption>()
        var cursor = start
        while (true) {
            val at = body.indexOf("class=\"poll-meter", cursor).takeIf { it in 0 until end } ?: break
            val next = body.indexOf("class=\"poll-meter", at + 18).takeIf { it in 0 until end } ?: end
            val meter = body.substring(at, next)
            val label = meter.spanText("class=\"poll-choice-option\"")
            if (!label.isNullOrBlank()) {
                val percent = meter.spanText("class=\"poll-choice-value\"")
                    ?.filter { it.isDigit() }
                    ?.toIntOrNull()
                    ?.coerceIn(0, 100)
                    ?: 0
                options += PollOption(
                    label = label,
                    percent = percent,
                    leader = meter.startsWith("class=\"poll-meter leader")
                )
            }
            cursor = next
            if (next >= end) break
        }
        if (options.isEmpty()) return null

        val info = if (infoAt > 0) body.spanText(infoAt) else null
        val votesPart = info?.substringBefore('•')?.trim()
        return Poll(
            options = options,
            votes = votesPart?.takeIf { "vote" in it }?.filter { it.isDigit() }?.toLongOrNull(),
            status = info?.takeIf { '•' in it }?.substringAfter('•')?.trim()?.takeIf { it.isNotBlank() }
        )
    }

    /**
     * Text of a non div element whose opening tag contains [marker], up to
     * [closeTag]. tagContent balances divs only, so on an h2, a p or a span it
     * would run on to the end of the enclosing div and swallow its siblings.
     */
    private fun String.elementText(marker: String, closeTag: String): String? =
        indexOf(marker).takeIf { it >= 0 }?.let { elementText(it, closeTag) }

    private fun String.elementText(markerAt: Int, closeTag: String): String? {
        val open = indexOf('>', markerAt).takeIf { it >= 0 } ?: return null
        val close = indexOf(closeTag, open).takeIf { it >= 0 } ?: return null
        return htmlToText(substring(open + 1, close)).takeIf { it.isNotBlank() }
    }

    private fun String.spanText(marker: String): String? = elementText(marker, "</span>")

    private fun String.spanText(markerAt: Int): String? = elementText(markerAt, "</span>")

    // ---- media --------------------------------------------------------------

    /**
     * Photos, videos and gifs each announce themselves differently. Nitter
     * generously provides a download URL for video, which is the hard part.
     */
    private fun extractMedia(body: String, host: String): List<MediaItem> {
        val items = mutableListOf<MediaItem>()

        var cursor = 0
        while (true) {
            val at = body.indexOf("class=\"still-image\"", cursor).takeIf { it >= 0 } ?: break
            val original = body.attributeBackwardsOrForward(at, "href=\"")
            val preview = body.attributeNear(at, "src=\"", 400)
            if (original != null || preview != null) {
                val full = original ?: preview!!
                items += MediaItem(
                    previewUrl = absolute(preview ?: full, host),
                    downloadUrl = absolute(full, host),
                    type = MediaType.PHOTO
                )
            }
            cursor = at + 20
        }

        cursor = 0
        while (true) {
            val at = body.indexOf("<video", cursor, ignoreCase = true).takeIf { it >= 0 } ?: break
            val isGif = body.attributeNear(at, "class=\"", 60)?.contains("gif") == true
            val poster = body.attributeNear(at, "poster=\"", 300)
            val source = body.sourceAfter(at) ?: body.attributeNear(at, "data-url=\"", 300)
            val download = body.attributeNear("class=\"video-download\"", "href=\"", 300)

            if (poster != null || source != null) {
                items += MediaItem(
                    previewUrl = absolute(poster ?: source!!, host),
                    downloadUrl = absolute(download ?: source ?: poster!!, host),
                    type = if (isGif) MediaType.GIF else MediaType.VIDEO,
                    durationLabel = body.tagContent("class=\"overlay-duration\"")?.let(::htmlToText)
                )
            }
            cursor = at + 6
        }

        return items.distinctBy { it.downloadUrl }
    }

    private fun String.sourceAfter(from: Int): String? {
        val at = indexOf("<source", from).takeIf { it >= 0 && it - from < 400 } ?: return null
        return attributeNear(at, "src=\"", 200)
    }

    // ---- stats --------------------------------------------------------------

    private fun extractStats(chunk: String): PostStats? {
        val at = chunk.indexOf("class=\"tweet-stats\"").takeIf { it >= 0 } ?: return null
        val block = chunk.substring(at, minOf(at + 1_500, chunk.length))
        val stats = PostStats(
            replies = block.statAfter("icon-comment"),
            reposts = block.statAfter("icon-retweet"),
            likes = block.statAfter("icon-heart"),
            views = block.statAfter("icon-views")
        )
        return stats.takeIf {
            it.replies != null || it.reposts != null || it.likes != null || it.views != null
        }
    }

    /**
     * The count is the text node right after the icon span closes, and Nitter
     * emits an empty string for zero.
     */
    private fun String.statAfter(iconClass: String): Int? {
        val at = indexOf(iconClass).takeIf { it >= 0 } ?: return null
        val spanEnd = indexOf("</span>", at).takeIf { it >= 0 } ?: return null
        val stop = indexOf('<', spanEnd + 7).takeIf { it >= 0 } ?: return null
        return substring(spanEnd + 7, stop).replace(",", "").trim().toIntOrNull()
    }

    // ---- profile ------------------------------------------------------------

    private fun extractProfileName(html: String): String? =
        html.attributeNear("class=\"profile-card-fullname\"", "title=\"", 300)
            ?.let(::decodeEntities)
            ?: html.tagContent("class=\"profile-card-fullname\"")?.let(::htmlToText)

    private fun extractProfileAvatar(html: String, host: String): String? =
        html.attributeNear("class=\"profile-card-avatar\"", "src=\"", 300)?.let { absolute(it, host) }

    private fun extractProfileBio(html: String): String? =
        html.tagContent("class=\"profile-bio\"")?.let(::htmlToText)?.takeIf { it.isNotBlank() }

    // ---- time ---------------------------------------------------------------

    private fun parseTimestamp(body: String): Long {
        val raw = body.attributeNear("class=\"tweet-date\"", "title=\"", 300) ?: return 0L
        val cleaned = decodeEntities(raw)
            .replace("·", " ")
            .replace(Regex("\\s+"), " ")
            .trim()
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

    // ---- html helpers -------------------------------------------------------

    /**
     * Content of the element whose opening tag contains [marker], balancing
     * nested divs so a quote or a card inside the body does not truncate it.
     */
    private fun String.tagContent(marker: String): String? {
        val markerAt = indexOf(marker).takeIf { it >= 0 } ?: return null
        val open = indexOf('>', markerAt).takeIf { it >= 0 } ?: return null
        var depth = 1
        var cursor = open + 1
        while (cursor < length && depth > 0) {
            val nextOpen = indexOf("<div", cursor)
            val nextClose = indexOf("</div", cursor)
            if (nextClose < 0) return substring(open + 1)
            if (nextOpen in 0 until nextClose) {
                depth++
                cursor = nextOpen + 4
            } else {
                depth--
                if (depth == 0) return substring(open + 1, nextClose)
                cursor = nextClose + 5
            }
        }
        return substring(open + 1)
    }

    private fun String.attributeNear(marker: String, attribute: String, window: Int): String? {
        val markerAt = indexOf(marker).takeIf { it >= 0 } ?: return null
        if (attribute.isEmpty()) {
            val start = markerAt + marker.length
            val end = indexOf('"', start).takeIf { it >= 0 } ?: return null
            return substring(start, end)
        }
        return attributeNear(markerAt, attribute, window)
    }

    private fun String.attributeNear(from: Int, attribute: String, window: Int): String? {
        val attrAt = indexOf(attribute, from).takeIf { it >= 0 } ?: return null
        if (attrAt - from > window) return null
        val start = attrAt + attribute.length
        val end = indexOf('"', start).takeIf { it >= 0 } ?: return null
        return decodeEntities(substring(start, end))
    }

    /** still-image puts href before the img, so look back a little then ahead. */
    private fun String.attributeBackwardsOrForward(at: Int, attribute: String): String? {
        val from = maxOf(0, at - 200)
        val slice = substring(from, minOf(length, at + 300))
        val attrAt = slice.indexOf(attribute).takeIf { it >= 0 } ?: return null
        val start = attrAt + attribute.length
        val end = slice.indexOf('"', start).takeIf { it >= 0 } ?: return null
        return decodeEntities(slice.substring(start, end))
    }

    private fun extractLinks(html: String): List<String> {
        val results = mutableListOf<String>()
        var cursor = 0
        while (true) {
            val at = html.indexOf("href=\"", cursor).takeIf { it >= 0 } ?: break
            val end = html.indexOf('"', at + 6).takeIf { it >= 0 } ?: break
            val href = decodeEntities(html.substring(at + 6, end))
            if (href.startsWith("http")) results += href
            cursor = end
        }
        return results.distinct()
    }

    /** Tag stripper that preserves the line structure Nitter expresses as br and p. */
    private fun htmlToText(html: String): String {
        val out = StringBuilder(html.length)
        var index = 0
        var inTag = false
        while (index < html.length) {
            val c = html[index]
            when {
                c == '<' -> {
                    val lower = html.substring(index, minOf(index + 4, html.length)).lowercase()
                    if (lower.startsWith("<br") || lower.startsWith("<p") || lower.startsWith("</p")) {
                        out.append('\n')
                    }
                    inTag = true
                }
                c == '>' -> inTag = false
                !inTag -> out.append(c)
            }
            index++
        }
        return decodeEntities(out.toString())
            .lines()
            .joinToString("\n") { it.trim() }
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }

    private fun decodeEntities(input: String): String = input
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&apos;", "'")
        .replace("&nbsp;", " ")
        .replace("&bull;", "•")
        .replace("&#8226;", "•")
        .replace("&amp;", "&")

    private fun absolute(raw: String, host: String): String = when {
        raw.startsWith("http") -> raw
        raw.startsWith("/") -> "https://$host$raw"
        else -> "https://$host/$raw"
    }

    companion object {
        const val SELECTOR_SET_VERSION = 5

        private val TIMESTAMP_PATTERNS = listOf(
            "MMM d, yyyy h:mm a zzz",
            "MMM d, yyyy h:mm a",
            "d MMM yyyy h:mm a zzz",
            "MMM d, yyyy HH:mm zzz",
            "MMM d, yyyy HH:mm"
        )
    }
}
