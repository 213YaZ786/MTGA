package com.mtga.app.data.rss

import android.util.Xml
import com.mtga.app.core.model.Feed
import com.mtga.app.core.model.Post
import com.mtga.app.core.model.PostId
import com.mtga.app.core.model.MediaItem
import com.mtga.app.core.model.MediaType
import com.mtga.app.core.model.PostKind
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

        val body = item.description
            ?.let(::htmlToPlainText)
            ?.takeIf { it.isNotBlank() }
            ?: title.substringAfter(':', title).trim()

        return Post(
            id = PostId.normalize(item.guid ?: link),
            authorHandle = item.creator?.removePrefix("@")?.trim().orEmpty()
                .ifBlank { feedHandle },
            authorName = feedHandle,
            text = body,
            publishedAtMillis = parseDate(item.pubDate),
            permalink = link,
            kind = kind,
            relatedHandle = related,
            media = extractImages(item.description.orEmpty(), host).map {
                MediaItem(previewUrl = it, downloadUrl = it, type = MediaType.PHOTO)
            }
        )
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
     */
    private fun htmlToPlainText(html: String): String {
        val out = StringBuilder(html.length)
        var inTag = false
        var index = 0
        while (index < html.length) {
            val c = html[index]
            when {
                c == '<' -> {
                    inTag = true
                    val lower = html.substring(index, minOf(index + 5, html.length)).lowercase()
                    if (lower.startsWith("<br") || lower.startsWith("<p")) out.append('\n')
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
        .replace("&amp;", "&")

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
