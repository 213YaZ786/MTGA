package com.mtga.app.data.rss

import android.util.Xml
import com.mtga.app.core.model.Feed
import com.mtga.app.core.model.Post
import com.mtga.app.core.model.PostId
import com.mtga.app.core.model.MediaItem
import com.mtga.app.core.model.MediaType
import com.mtga.app.core.model.PostKind
import com.mtga.app.core.model.Poll
import com.mtga.app.core.model.PollOption
import com.mtga.app.core.model.QuotedPost
import com.mtga.app.core.text.Entities
import com.mtga.app.data.html.HtmlTimelineParser
import com.mtga.app.data.xcom.XPagePayload
import kotlinx.serialization.json.JsonArray
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Parses a Nitter RSS feed into the domain model.
 *
 * A pure function from String to Feed, with no Android UI and no network, so it
 * can be unit tested against saved fixtures. When an instance changes its
 * output, this is the only file that needs to move.
 */
class RssFeedParser {

    fun parse(xml: String, handle: String, host: String): Feed? {
        val parser = Xml.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            setInput(StringReader(xml))
        }

        var channelTitle: String? = null
        val posts = mutableListOf<Post>()
        var current: MutableItem? = null
        var text = StringBuilder()

        try {
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> {
                        text = StringBuilder()
                        if (parser.name.equals("item", ignoreCase = true)) current = MutableItem()
                    }

                    XmlPullParser.TEXT, XmlPullParser.CDSECT -> text.append(parser.text)

                    XmlPullParser.END_TAG -> {
                        val value = text.toString().trim()
                        val tag = parser.name.lowercase()
                        val item = current
                        if (item == null) {
                            if (tag == "title" && channelTitle == null) channelTitle = value
                        } else {
                            when (tag) {
                                "title" -> item.title = value
                                "description" -> item.description = value
                                "link" -> item.link = value
                                "guid" -> item.guid = value
                                "pubdate" -> item.pubDate = value
                                "dc:creator", "creator" -> item.creator = value
                                "item" -> {
                                    toPost(item, handle, host)?.let(posts::add)
                                    current = null
                                }
                            }
                        }
                    }
                }
                event = parser.next()
            }
        } catch (t: Throwable) {
            return null
        }

        if (posts.isEmpty() && channelTitle == null) return null

        return Feed(
            handle = handle,
            displayName = displayNameFrom(channelTitle, handle),
            posts = posts,
            fetchedFromHost = host,
            fetchedAtMillis = System.currentTimeMillis()
        )
    }

    // AGPL-3.0, ported from Nitter, renderRssPoll read the other way round: each option is a line
    // `pct% — option<br>`, then `<i>votes votes • status</i>`, rebuilt as parsePoll builds its
    // Poll: values, options, leader (the index of the largest value, the feed leaves it to the
    // reader), votes and status.
    // https://github.com/zedeus/nitter/blob/master/src/views/rss.nimf#renderRssPoll
    // https://github.com/zedeus/nitter/blob/master/src/parser.nim#parsePoll
    // not ported: the choiceN_count and choiceN_label bindings and end_datetime_utc (the feed has
    // percentages and a written status), perc as a division of the values by the votes (the
    // percentage is read back as written), insertSep (the thousands separators are dropped).
    private fun renderRssPoll(poll: String): Poll? {
        val values = mutableListOf<Int>()
        val options = mutableListOf<String>()
        for (line in poll.substringAfter("<p>").split("<br>")) {
            val pct = line.substringBefore("% — ").trim().toIntOrNull() ?: continue
            values.add(pct)
            options.add(decodeEntities(line.substringAfter("% — ")))
        }
        if (options.isEmpty()) return null
        val leader = values.indexOf(values.max())
        val votesStr = poll.substringAfter("<i>", "").substringBefore("</i>")
        return Poll(
            options = List(options.size) { i -> PollOption(options[i], values[i], i == leader) },
            votes = votesStr.substringBefore(" votes").replace(",", "").toLongOrNull(),
            status = if ("• " in votesStr) votesStr.substringAfter("• ") else null
        )
    }

    // AGPL-3.0, ported from Nitter, renderQuote (the page) and the blockquote of renderRssTweet
    // (the feed): the quote is `<b>fullname (@username)</b>`, the quote's own tweet in a paragraph,
    // then a footer whose cite links to it (getLink(quote)). The quote's text and its media come
    // from the same reading as the post's own.
    // https://github.com/zedeus/nitter/blob/master/src/views/tweet.nim#renderQuote
    // https://github.com/zedeus/nitter/blob/master/src/views/rss.nimf#renderRssTweet
    // not ported: the unavailable quote (the feed only writes a quote that is available), the
    // quote's renderReply line, articlePreview, community note, "Show this thread" and history.
    private fun renderQuote(quote: String, host: String): QuotedPost? {
        val link = quote.substringAfter("<cite><a href=\"", "").substringBefore('"').takeIf { it.isNotEmpty() } ?: return null
        val user = Regex("""<b>(.*?)\s*\(@(\w+)\)</b>""").find(quote) ?: return null
        val footer = quote.indexOf("<footer>").takeIf { it >= 0 } ?: quote.length
        val open = quote.indexOf("<p>", user.range.last).takeIf { it >= 0 } ?: return null
        val close = quote.lastIndexOf("</p>", footer).takeIf { it > open } ?: return null
        val tweet = renderRssTweet(quote.substring(open + 3, close), host)
        return QuotedPost(
            handle = user.groupValues[2],
            name = decodeEntities(user.groupValues[1]),
            text = tweet.text,
            permalink = decodeEntities(link),
            media = tweet.media
        )
    }

    /** Channel titles look like "Display Name / @handle". */
    private fun displayNameFrom(channelTitle: String?, handle: String): String {
        val raw = channelTitle?.substringBefore(" / ")?.trim()
        return if (raw.isNullOrBlank()) handle else raw
    }

    private fun toPost(item: MutableItem, feedHandle: String, host: String): Post? {
        val link = item.link?.takeIf { it.isNotBlank() } ?: return null
        val title = item.title.orEmpty()

        val kind = when {
            title.startsWith("RT by @") -> PostKind.REPOST
            title.startsWith("R to @") -> PostKind.REPLY
            else -> PostKind.ORIGINAL
        }

        // Nitter prefixes the title with the relationship, for example
        // "RT by @someone: actual text". The prefix is metadata, not content.
        val related = when (kind) {
            PostKind.REPOST -> title.removePrefix("RT by @").substringBefore(':').trim()
            PostKind.REPLY -> title.removePrefix("R to @").substringBefore(':').trim()
            else -> null
        }

        val tweet = renderRssTweet(item.description.orEmpty(), host)
        val body = tweet.text.takeIf { it.isNotBlank() }
            ?: title.substringAfter(':', title).trim()

        return Post(
            id = PostId.normalize(item.guid ?: link),
            authorHandle = item.creator?.removePrefix("@")?.trim().orEmpty()
                .ifBlank { feedHandle },
            authorName = feedHandle,
            // A reply's text starts with the accounts it answers, which X hides (Nitter's
            // expandTextEntities display range) and "Replying to" says already.
            text = XPagePayload.expandTextEntities(
                body, JsonArray(emptyList()), null,
                if (kind == PostKind.REPLY) listOfNotNull(related) else emptyList(), false
            ),
            publishedAtMillis = parseDate(item.pubDate),
            permalink = link,
            kind = if (kind == PostKind.ORIGINAL && tweet.quote != null) PostKind.QUOTE else kind,
            relatedHandle = related,
            media = tweet.media,
            quoted = tweet.quote,
            poll = tweet.poll
        )
    }

    private class RssTweet(val text: String, val media: List<MediaItem>, val poll: Poll?, val quote: QuotedPost?)

    // AGPL-3.0, ported from Nitter, renderRssTweet, read the other way round: the description is
    // `<p>text</p>`, then the media of the tweet (renderRssMedia), then the blocks that each start
    // with an `<hr/>`: the card, the article, the poll (renderRssPoll) and the quote in a blockquote.
    // The text is the first paragraph only, so the quote's text, the poll's lines and the "Video"
    // label of a video's thumbnail no longer run into it, and the quote is read as its own post.
    // https://github.com/zedeus/nitter/blob/master/src/views/rss.nimf#renderRssTweet
    // not ported: renderRssCard (the card's block stays out of the text and is not read),
    // renderRssArticle (same), the community note's paragraph (same), replaceUrls (the text keeps
    // the addresses Nitter wrote), getTitle (the title is read in toPost).
    private fun renderRssTweet(description: String, host: String): RssTweet {
        val quote = description.indexOf("<blockquote>").takeIf { it >= 0 }
        val tweet = if (quote != null) description.substring(0, quote) else description
        val media = tweet.substring(0, tweet.indexOf("<hr/>").takeIf { it >= 0 } ?: tweet.length)
        val text = media.trimStart().takeIf { it.startsWith("<p>") && !it.startsWith("<p><b>Community note:</b>") }
            ?.substringBefore("</p>")?.let(::htmlToPlainText).orEmpty()
        val poll = tweet.indexOf("<b>Poll</b>").takeIf { it >= 0 }?.let { renderRssPoll(tweet.substring(it)) }
        return RssTweet(text, renderRssMedia(media, host), poll, quote?.let { renderQuote(description.substring(it), host) })
    }

    // AGPL-3.0, ported from Nitter, renderRssMedia read the other way round: a photo is an
    // `<img src=urlPrefix + getPicUrl(photo.url)>`, a video the `<a><br>Video<br><img thumb></a>` of
    // its link, a gif a `<video poster autoplay muted loop><source src type="video/mp4">`. The
    // photos and the video thumbnails are pictures here, the gif is read by the Nitter page
    // parser's own video reader, which takes its poster and its source.
    // https://github.com/zedeus/nitter/blob/master/src/views/rss.nimf#renderRssMedia
    // not ported: the video's own file (the feed only writes its thumb).
    private fun renderRssMedia(media: String, urlPrefix: String): List<MediaItem> {
        val photo = extractImages(media, urlPrefix).map { MediaItem(previewUrl = it, downloadUrl = it, type = MediaType.PHOTO) }
        val gif = HtmlTimelineParser().extractMedia(media, urlPrefix)
        return photo + gif
    }

    private fun parseDate(raw: String?): Long {
        if (raw.isNullOrBlank()) return 0L
        return runCatching {
            ZonedDateTime.parse(raw, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()
        }.getOrDefault(0L)
    }

    /**
     * Nitter descriptions are small, predictable HTML. A tag stripper plus
     * entity decoding beats pulling in a parser, and beats android.text.Html
     * which drags UI classes into a pure function.
     *
     * Nitter writes the text of a post as <p>text with every "\n" replaced by
     * "<br>\n"</p> (renderRssTweet), the line break being the tag and the newline
     * after it only the template's layout: that pair reads as one line break.
     * AGPL-3.0, ported from Nitter:
     * https://github.com/zedeus/nitter/blob/master/src/views/rss.nimf#renderRssTweet
     * not ported: the media, card, article, poll, community note and quote blocks
     * (this parser reads the description's text and images only).
     */
    internal fun htmlToPlainText(html: String): String {
        val out = StringBuilder(html.length)
        var inTag = false
        var afterBreak = false
        var index = 0
        while (index < html.length) {
            val c = html[index]
            when {
                c == '<' -> {
                    inTag = true
                    val lower = html.substring(index, minOf(index + 5, html.length)).lowercase()
                    if (lower.startsWith("<br") || lower.startsWith("<p")) out.append('\n')
                    afterBreak = lower.startsWith("<br")
                }
                c == '>' -> {
                    inTag = false
                    // The newline Nitter puts right after its br is layout, not a second break.
                    if (afterBreak && html.getOrNull(index + 1) == '\n') index++
                    afterBreak = false
                }
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

    // The shared decoder ported from jsoup: every named, decimal and hexadecimal reference, once.
    private fun decodeEntities(input: String): String = Entities.unescape(input)

    /** Pulls img sources out, making instance relative paths absolute. */
    private fun extractImages(html: String, host: String): List<String> {
        if ("<img" !in html) return emptyList()
        val results = mutableListOf<String>()
        var cursor = 0
        while (true) {
            val imgAt = html.indexOf("<img", cursor, ignoreCase = true)
            if (imgAt < 0) break
            val srcAt = html.indexOf("src=\"", imgAt, ignoreCase = true)
            if (srcAt < 0) break
            val start = srcAt + 5
            val end = html.indexOf('"', start)
            if (end < 0) break
            val raw = html.substring(start, end)
            results += if (raw.startsWith("http")) raw else "https://$host${raw.ensureLeadingSlash()}"
            cursor = end
        }
        return results.distinct()
    }

    private fun String.ensureLeadingSlash(): String = if (startsWith("/")) this else "/$this"

    private class MutableItem {
        var title: String? = null
        var description: String? = null
        var link: String? = null
        var guid: String? = null
        var pubDate: String? = null
        var creator: String? = null
    }
}
