package com.mtga.app.data.html

import com.mtga.app.core.model.ProfileStats

/**
 * The profile card of a Nitter page: banner, location, website, join date
 * and the four numbers. Pure, no Android.
 *
 * Markup from Nitter's src/views/profile.nim, fetched in September 2026:
 * "profile-banner" wraps the banner image, "profile-card-extra" holds
 * "profile-location", "profile-website" and "profile-joindate", and
 * "profile-statlist" holds li elements classed "posts", "following",
 * "followers" and "likes", each with a "profile-stat-num" span.
 *
 * Every field is independent and null when missing, so one markup change
 * costs one line of the header, never the page.
 */
object ProfileCardParser {

    data class Card(
        val bannerUrl: String?,
        val location: String?,
        val website: String?,
        val joined: String?,
        val stats: ProfileStats?
    )

    fun parse(html: String, host: String): Card {
        val cardAt = html.indexOf("class=\"profile-card\"")
        // Posts can quote "profile-location" in their text only as escaped
        // entities, but scoping to the card keeps the search short anyway.
        val card = if (cardAt >= 0) html.substring(cardAt, minOf(html.length, cardAt + CARD_WINDOW)) else ""

        return Card(
            bannerUrl = banner(html, host),
            location = segmentText(card, "class=\"profile-location\""),
            website = card.attrAfter("class=\"profile-website\"", "href=\"", 600)?.let(::decode),
            joined = segmentText(card, "class=\"profile-joindate\""),
            stats = stats(card)
        )
    }

    private fun banner(html: String, host: String): String? {
        val raw = html.attrAfter("class=\"profile-banner\"", "src=\"", 600) ?: return null
        val url = decode(raw)
        return when {
            url.startsWith("https://") -> url
            url.startsWith("//") -> "https:$url"
            url.startsWith("/") -> "https://$host$url"
            else -> null
        }
    }

    private fun stats(card: String): ProfileStats? {
        val stats = ProfileStats(
            posts = stat(card, "posts"),
            following = stat(card, "following"),
            followers = stat(card, "followers"),
            likes = stat(card, "likes")
        )
        return if (stats == ProfileStats()) null else stats
    }

    private fun stat(card: String, cls: String): Long? {
        val at = card.indexOf("<li class=\"$cls\"").takeIf { it >= 0 } ?: return null
        val num = card.indexOf("profile-stat-num", at).takeIf { it >= 0 && it - at < 400 } ?: return null
        val open = card.indexOf('>', num).takeIf { it >= 0 } ?: return null
        val close = card.indexOf('<', open).takeIf { it >= 0 } ?: return null
        return card.substring(open + 1, close).filter(Char::isDigit).toLongOrNull()
    }

    /**
     * Visible text of the element opened by [marker], up to the next profile
     * block. Icons are empty elements, so stripping tags leaves the words.
     */
    private fun segmentText(card: String, marker: String): String? {
        val at = card.indexOf(marker).takeIf { it >= 0 } ?: return null
        val start = card.indexOf('>', at).takeIf { it >= 0 } ?: return null
        val next = listOf("class=\"profile-", "class=\"photo-rail")
            .mapNotNull { m -> card.indexOf(m, start).takeIf { it >= 0 } }
            .minOrNull() ?: card.length
        val text = decode(card.substring(start + 1, next).replace(TAG, " "))
            .replace(Regex("\\s+"), " ")
            .trim()
            // The cut lands inside the next opening tag, leaving "<div".
            .removeSuffix("<div").removeSuffix("<li").removeSuffix("<ul").trim()
        return text.takeIf { it.isNotEmpty() }
    }

    private fun String.attrAfter(marker: String, attribute: String, window: Int): String? {
        val at = indexOf(marker).takeIf { it >= 0 } ?: return null
        val attrAt = indexOf(attribute, at).takeIf { it >= 0 && it - at < window } ?: return null
        val start = attrAt + attribute.length
        val end = indexOf('"', start).takeIf { it >= 0 } ?: return null
        return substring(start, end).takeIf { it.isNotBlank() }
    }

    private fun decode(text: String): String = text
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&#x27;", "'")
        .replace("&nbsp;", " ")

    private val TAG = Regex("<[^>]*>")
    private const val CARD_WINDOW = 12_000
}
